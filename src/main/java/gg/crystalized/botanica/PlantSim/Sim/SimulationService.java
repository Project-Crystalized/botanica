package gg.crystalized.botanica.PlantSim.Sim;

import gg.crystalized.botanica.PlantSim.Bus.BlockMutation;
import gg.crystalized.botanica.PlantSim.Bus.DisplayEntityMutation;
import gg.crystalized.botanica.PlantSim.Bus.Mutation;
import gg.crystalized.botanica.PlantSim.Bus.MutationBus;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantModifier;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import gg.crystalized.botanica.PlantSim.World.SchematicManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Central sim for BOTH plants and soils. Pure compute + next wake prediction. */
public final class SimulationService {

    // --- Tuning knobs (move to config if you like) ---------------------------
    private static final double SAFETY_WAKE_SECS = 15.0;      // small periodic check
    private static final double ROBUST_PENALTY   = 0.75;      // rate in robust-but-not-ideal band
    private static final double BASE_WATER_DECAY_PER_SEC     = 0.10; // moisture/sec lost

    private final SimulationDataManager data;
    private final SoilRepo soilRepo;
    private final MutationBus mutationBus;
    private final SchematicManager schematicManager;
    private final SchematicBlockIndex schematicBlockIndex;
    public SimulationService(SimulationDataManager data, SoilRepo soilRepo, MutationBus mutationBus, SchematicBlockIndex schematicBlockIndex, SchematicManager schematicManager) {
        this.data = data;
        this.soilRepo = soilRepo;
        this.mutationBus = mutationBus;
        this.schematicManager = schematicManager;
        this.schematicBlockIndex = schematicBlockIndex;
    }
    
    // Getter for spatial index (used by PlantCommands)
    public SchematicBlockIndex getSchematicBlockIndex() {
        return schematicBlockIndex;
    }

    // ------------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------------

    /** Simulate a batch of soils and plants that are due now; return world mutations. */
    public List<Mutation> simulate(List<SoilInstance> soils, List<PlantInstance> plants, Instant now) {
        List<Mutation> mutations = new ArrayList<>();

        // 1) Soils first (so plants see fresh water/nutrients)
        for (SoilInstance s : soils) stepSoil(s, now);

        // 2) Plants
        for (PlantInstance p : plants) {
            Mutation m = stepPlant(p, now);
            if (m != null) mutations.add(m);
        }
        return mutations;
    }

    /** Simulate only soils (if you wake soils on their own schedule). */
    public void simulateSoils(List<SoilInstance> soils, Instant now) {
        for (SoilInstance s : soils) stepSoil(s, now);
    }

    /** Simulate only plants (if you woke soils earlier this run). */
    public List<Mutation> simulatePlants(List<PlantInstance> plants, Instant now) {
        List<Mutation> out = new ArrayList<>();
        for (PlantInstance p : plants) {
            Mutation m = stepPlant(p, now);
            if (m != null) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------------
    // Soil step: integrate drift and schedule next soil wake
    // ------------------------------------------------------------------------
    private void stepSoil(SoilInstance s, Instant now) {
        // Integrate drift
        SoilSpec spec = data.soils().get(s.soilId);

        double dt = secondsBetween(s.lastUpdateAt, now);
        if (dt > 0) {
            double wLoss = (BASE_WATER_DECAY_PER_SEC) * dt;
            s.water        = clamp01(s.water - wLoss);
            s.lastUpdateAt = now;
        }

        // Predict next soil wake (earliest boundary or safety)
        double nextWaterSecs = boundaryTimeSeconds(s.water, BASE_WATER_DECAY_PER_SEC);
        double nextSecs = minPositive(nextWaterSecs, SAFETY_WAKE_SECS);
        s.nextUpdateAt = now.plusMillis((long) (nextSecs * 1000));
    }

    /**
     * When do we hit the next "interesting" moisture/nutrient boundary if we keep decaying?
     * Here we use "reach zero" as a boundary. Return +INF if rate==0 or already 0.
     */
    private static double boundaryTimeSeconds(double level0to100, double decayPerSec) {
        if (decayPerSec <= 1e-9) return Double.POSITIVE_INFINITY;
        if (level0to100 <= 0)    return SAFETY_WAKE_SECS; // already dry; keep a small wake
        return level0to100 / decayPerSec;
    }

    // ------------------------------------------------------------------------
    // Plant step: progress integration and schedule next plant wake
    // ------------------------------------------------------------------------
    private Mutation stepPlant(PlantInstance p, Instant now) {
        if (p.complete) {
            p.nextUpdateAt = null;
            return null;
        }

        // Bring time forward
        double dt = Math.max(0, secondsBetween(p.lastSimAt, now));
        p.lastSimAt = now;

        // Pull spec
        PlantSpec spec = data.plants().get(p.speciesId);
        if (spec == null) {
            // If content missing, keep a safety wake and do nothing.
            p.nextUpdateAt = now.plusSeconds((long) SAFETY_WAKE_SECS);
            return null;
        }

        // Fetch soil (this should be passed in or fetched from repo)
        // For now, we'll assume the soil has been stepped already in the same simulation run
        SoilInstance soil = null; // TODO: Fetch from repo using p.soilPos
        
        // If no soil found, keep a safety wake
        if (soil == null) {
            p.nextUpdateAt = now.plusSeconds((long) SAFETY_WAKE_SECS);
            return null;
        }

        // Apply evaporation to soil first (so plant sees current state)
        double soilDt = secondsBetween(soil.lastUpdateAt, now);
        if (soilDt > 0) {
            double wLoss = (BASE_WATER_DECAY_PER_SEC) * soilDt;
            soil.water = clamp01(soil.water - wLoss);
            soil.lastUpdateAt = now;
        }

        // Compute duration from spec + soil quality + mods
        double totalSec = effectiveTotalSec(spec, soil, p.modifiers, now);
        
        // Window multiplier from soil windows
        double gate = windowMultiplier(soil, spec);

        // Apply consumption if gate > 0 (ideal or robust)
        if (gate > 0) {
            double consumptionDt = Math.min(dt, soilDt > 0 ? soilDt : dt);
            soil.water = clamp01(soil.water - spec.growthFactors.waterPerSec * consumptionDt);
            soil.nutrients = clamp01(soil.nutrients - spec.growthFactors.nutrientsPerSec * consumptionDt);
        }

        // Instantaneous growth rate (fraction/sec)
        double rate = (gate > 0 && totalSec > 0) ? (gate / totalSec) : 0.0;

        // Integrate progress
        p.progress = Math.min(1.0, p.progress + rate * dt);

        // Finished?
        if (p.progress >= 1.0) {
            p.complete = true;
            p.nextUpdateAt = null;

            // Choose the mature block from combined stages (last stage), if present
            String matureBlock = null;
            if (spec.mutations != null && spec.mutations.stages != null && !spec.mutations.stages.isEmpty()) {
                // Combine growth stages and harvestable stages
                List<String> allStages = new ArrayList<>(spec.mutations.stages);
                if (spec.mutations.harvestableStages != null && !spec.mutations.harvestableStages.isEmpty()) {
                    allStages.addAll(spec.mutations.harvestableStages);
                }
                matureBlock = allStages.get(allStages.size() - 1);
            }
            
            // Only return BlockMutation for non-walkthrough plants
            // Walkthrough plants are handled by PhantomBlockManager via updatePlantGrowth
            if (!spec.allowWalkthrough) {
                return new BlockMutation(p.pos, matureBlock != null ? matureBlock : "OAK_SAPLING", null);
            }
        }

        // Not finished: predict next wake
        // Finish ETA (given current rate), modifier expiry, soil boundary, safety
        Instant finishAt = null;
        if (rate > 1e-12) {
            double remainingFrac = 1.0 - p.progress;
            long etaMs = (long) ((remainingFrac / rate) * 1000);
            finishAt = now.plusMillis(etaMs);
        }

        Instant nextModExpiry = p.modifiers.stream()
                .map(PlantModifier::expiresAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Instant soilBoundary = predictSoilBoundary(soil, spec, now);
        Instant safety = now.plusSeconds((long) SAFETY_WAKE_SECS);

        p.nextUpdateAt = minNonNull(finishAt, nextModExpiry, soilBoundary, safety);
        return null;
    }

    // ------------------------------------------------------------------------
    // Math
    // ------------------------------------------------------------------------

    private double effectiveTotalSec(PlantSpec spec, SoilInstance soil, List<PlantModifier> mods, Instant now) {
        double t = Math.max(1.0, spec.baseGrowthDuration); // base duration (seconds), clamp to 1s min

        // Apply soil quality multiplier (larger qualityMult = faster growth)
        t = t / Math.max(0.1, soil.qualityMult); // divide by qualityMult for speed multiplier
        
        // Apply optimal soil bonus (1.5x growth speed if planted on preferred soil)
        if (spec.optimalSoil != null && spec.optimalSoil.equals(soil.soilId)) {
            t = t / 1.5; // Divide by 1.5 to make it 1.5x faster (lower duration = faster growth)
        }

        // Apply modifiers
        double mult = 1.0, flat = 0.0;
        if (mods != null) {
            for (PlantModifier m : mods) {
                if (m != null && m.activeAt(now)) {
                    if ("speed".equals(m.kind())) mult *= m.multiplier();
                    else if ("flat".equals(m.kind())) flat += m.flatAdd();
                }
            }
        }
        return Math.max(1.0, t * mult - flat);
    }

    private double windowMultiplier(SoilInstance soil, PlantSpec spec) {
        // Check if growthFactors is defined
        if (spec.growthFactors == null) return 1.0;
        
        Integer wMin = spec.growthFactors.waterMin;
        Integer wMax = spec.growthFactors.waterMax;
        Integer nMin = spec.growthFactors.nutMin;
        Integer nMax = spec.growthFactors.nutMax;
        Integer rob  = spec.growthFactors.robustness;

        if (wMin == null || wMax == null || nMin == null || nMax == null) return 1.0;

        int R = rob != null ? Math.max(0, rob) : 0;

        boolean wIdeal = within(soil.water, wMin, wMax);
        boolean nIdeal = within(soil.nutrients, nMin, nMax);
        if (wIdeal && nIdeal) return 1.0;

        boolean wRobust = within(soil.water, wMin - R, wMax + R);
        boolean nRobust = within(soil.nutrients, nMin - R, nMax + R);
        if (wRobust && nRobust) return ROBUST_PENALTY;

        return 0.0;
    }

    /** Optional: predict when moisture/nutrients will cross out of the current band. Keep simple or return null. */
    private Instant predictSoilBoundary(SoilInstance soil, PlantSpec spec, Instant now) {
        // Minimal: rely on safety wakes + event-driven reschedules → return null.
        // If you want better prediction, compute time to hit ideal/robust edges given current decay and return now+that.
        return null;
    }

    // ------------------------------------------------------------------------
    // Lightweight update methods (for high-frequency resource updates)
    // ------------------------------------------------------------------------

    /**
     * Update soil resources (water evaporation) without full simulation.
     * Used for high-frequency updates to keep resources accurate.
     */
    public SoilInstance updateSoilResources(SoilInstance soil, Instant now) {
        double dt = secondsBetween(soil.lastUpdateAt, now);
        if (dt <= 0) return soil;

        // Apply water evaporation
        double wLoss = BASE_WATER_DECAY_PER_SEC * dt;
        double newWater = clamp01(soil.water - wLoss);

        return new SoilInstance(
            soil.pos, soil.soilId, soil.qualityMult,
            newWater, soil.nutrients, soil.tilled, now
        );
    }

    /**
     * Update plant growth and consumption without full simulation.
     * Used for high-frequency updates to keep progress accurate.
     */
    public PlantInstance updatePlantGrowth(PlantInstance plant, SoilInstance soil, Instant now) {
        if (plant.complete) return null;

        double dt = Math.max(0, (now.toEpochMilli() - plant.lastSimAt.toEpochMilli()) / 1000.0);
        if (dt <= 0) return null;

        PlantSpec spec = data.plants().get(plant.speciesId);
        if (spec == null) return null;

        // Calculate current growth rate
        double gate = windowMultiplier(soil, spec);
        double totalSec = effectiveTotalSec(spec, soil, plant.modifiers, now);
        double rate = (gate > 0 && totalSec > 0) ? (gate / totalSec) : 0.0;

        // Update progress
        double newProgress = Math.min(1.0, plant.progress + (rate * dt));
        boolean newComplete = newProgress >= 1.0;

        // Update visual block if stage changed
        updatePlantVisual(plant, spec, newProgress, newComplete);

        // Consume resources if growing
        if (rate > 0) {
            double waterConsumed = spec.growthFactors.waterPerSec * dt;
            double nutrientsConsumed = spec.growthFactors.nutrientsPerSec * dt;

            // Update soil with consumed resources
            double newWater = Math.max(0.0, soil.water - waterConsumed);
            double newNutrients = Math.max(0.0, soil.nutrients - nutrientsConsumed);

            SoilInstance updatedSoil = new SoilInstance(
                soil.pos, soil.soilId, soil.qualityMult,
                newWater, newNutrients, soil.tilled, now
            );
            soilRepo.upsert(updatedSoil);
        }

        // Update plant state in-place (same plant object, just modified)
        plant.progress = newProgress;
        plant.complete = newComplete;
        // plant.currentSchematicId is already set by updatePlantVisual() call above
        plant.lastSimAt = now;
        plant.nextUpdateAt = now.plusSeconds(1); // Simple 1-second update interval

        return plant;
    }

    /**
     * Update the visual representation of a plant based on growth progress.
     * Supports both single-block stages and multi-block schematics.
     * Handles stage transitions by removing old schematic before placing new.
     * Combines growth stages and harvestable stages for visual progression.
     * Only updates when the stage actually changes (prevents flickering).
     */
    private void updatePlantVisual(PlantInstance plant, PlantSpec spec, double progress, boolean complete) {
        if (spec.mutations == null || spec.mutations.stages == null || spec.mutations.stages.isEmpty()) {
            return;
        }
        
        // Combine growth stages and harvestable stages into one list
        List<String> allStages = new ArrayList<>(spec.mutations.stages);
        if (spec.mutations.harvestableStages != null && !spec.mutations.harvestableStages.isEmpty()) {
            allStages.addAll(spec.mutations.harvestableStages);
        }
        
        int stageCount = allStages.size();
        int newStageIndex;

        if (complete || progress >= 1.0) {
            // Use the last stage for complete (100% progress)
            newStageIndex = stageCount - 1;
        } else {
            // Distribute stages across 0-99% progress (reserving 100% for complete stage)
            double stageProgress = progress * 0.99; // Scale to 0-99% to reserve last stage
            newStageIndex = Math.min(stageCount - 2, (int) (stageProgress * (stageCount - 1)));
            newStageIndex = Math.max(0, newStageIndex); // Ensure we don't go below 0
        }
        
        // CHECK: Has the stage actually changed?
        if (newStageIndex == plant.currentStageIndex) {
            return; // No change, skip update to prevent flickering
        }
        
        // Stage has changed, update it
        plant.currentStageIndex = newStageIndex;
        String blockOrSchematic = allStages.get(newStageIndex);

        // Determine if new stage is a schematic
        boolean newIsSchematic = blockOrSchematic.startsWith("schematic:");
        String newSchematicId = newIsSchematic ? blockOrSchematic.substring("schematic:".length()) : null;

        // Check if we need to transition stages
        boolean needsTransition = false;
        if (newIsSchematic && !newSchematicId.equals(plant.currentSchematicId)) {
            needsTransition = true;
        } else if (!newIsSchematic && plant.currentSchematicId != null) {
            needsTransition = true;
        }

        // Remove old schematic if transitioning away from one
        if (needsTransition && plant.currentSchematicId != null) {
            TreeSchematic oldSchematic = schematicManager.getSchematic(plant.currentSchematicId);
            if (oldSchematic != null) {
                if (spec.allowWalkthrough) {
                    // Walkthrough plant - queue display entity removal
                    mutationBus.queue(DisplayEntityMutation.removeSchematic(plant.pos));
                } else {
                    // Real plant - remove server-side blocks
                    removeSchematic(plant.pos, oldSchematic, plant.rotation);
                }
                schematicBlockIndex.unregisterSchematic(plant.pos, oldSchematic);
            }
        }

        // Place new stage
        if (newIsSchematic) {
            TreeSchematic newSchematic = schematicManager.getSchematic(newSchematicId);
            if (newSchematic != null) {
                if (spec.allowWalkthrough) {
                    // Walkthrough plant - queue display entity creation
                    mutationBus.queue(DisplayEntityMutation.setSchematic(plant.pos, newSchematicId, newSchematic, plant.rotation));
                } else {
                    // Real plant - place server-side blocks
                    placeSchematic(plant.pos, newSchematic, plant.rotation);
                }
                schematicBlockIndex.registerSchematic(plant.pos, newSchematic, plant.id);
                plant.currentSchematicId = newSchematicId;
            }
        } else {
            // Regular single block placement
            if (spec.allowWalkthrough) {
                // Walkthrough plant - queue display entity creation
                mutationBus.queue(DisplayEntityMutation.setBlock(plant.pos, blockOrSchematic));
            } else {
                // Real plant - place server-side block
                mutationBus.queue(new BlockMutation(plant.pos, blockOrSchematic, null));
            }
            plant.currentSchematicId = null;
        }
    }

    /**
     * Place a multi-block schematic at the given position.
     * Uses "reckless" placement - replaces any blocks in the way.
     * Applies rotation around Y-axis if specified.
     */
    private void placeSchematic(BlockPos rootPos, TreeSchematic schematic, int rotation) {
        // Place all blocks from the schematic
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            // Apply rotation transform
            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), rotation);
            
            BlockPos blockPos = new BlockPos(
                rootPos.world(),
                rootPos.x() + rotated[0],
                rootPos.y() + block.y(),
                rootPos.z() + rotated[1]
            );
            mutationBus.queue(new BlockMutation(blockPos, block.material(), null));
        }
    }

    /**
     * Remove a multi-block schematic from the world.
     * Uses "surgical" removal - only removes blocks defined in the schematic.
     * Applies rotation around Y-axis if specified.
     */
    private void removeSchematic(BlockPos rootPos, TreeSchematic schematic, int rotation) {
        // Remove all blocks from the schematic
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            // Apply rotation transform
            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), rotation);
            
            BlockPos blockPos = new BlockPos(
                rootPos.world(),
                rootPos.x() + rotated[0],
                rootPos.y() + block.y(),
                rootPos.z() + rotated[1]
            );
            mutationBus.queue(new BlockMutation(blockPos, "AIR", null));
        }
    }

    // ------------------------------------------------------------------------
    // Utils
    // ------------------------------------------------------------------------
    private static double secondsBetween(Instant a, Instant b) {
        return Math.max(0, (b.toEpochMilli() - a.toEpochMilli()) / 1000.0);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static boolean within(double v, double a, double b) {
        double lo = Math.min(a, b), hi = Math.max(a, b);
        return v >= lo && v <= hi;
    }

    private static double minPositive(double... vals) {
        double best = Double.POSITIVE_INFINITY;
        for (double v : vals) if (v > 0 && v < best) best = v;
        return Double.isFinite(best) ? best : SAFETY_WAKE_SECS;
    }

    private static Instant minNonNull(Instant... times) {
        Instant best = null;
        for (Instant t : times) if (t != null && (best == null || t.isBefore(best))) best = t;
        return best;
    }
    
    /**
     * Get a soil spec by its ID.
     * @param soilId The soil ID (e.g., "PLAIN", "SANDY")
     * @return SoilSpec or null if not found
     */
    public SoilSpec getSoilSpecById(String soilId) {
        return data.soils().get(soilId);
    }
}
