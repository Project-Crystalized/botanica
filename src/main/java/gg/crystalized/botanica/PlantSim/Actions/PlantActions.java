package gg.crystalized.botanica.PlantSim.Actions;

import gg.crystalized.botanica.PlantSim.Bus.BlockMutation;
import gg.crystalized.botanica.PlantSim.Bus.ItemDropMutation;
import gg.crystalized.botanica.PlantSim.Bus.MutationBus;
import gg.crystalized.botanica.PlantSim.Bus.SoundMutation;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicManager;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Actions façade for player interactions with the plant simulation.
 * All actions enqueue work to the sim thread; they never mutate repos directly.
 */
public class PlantActions {
    private final SimulationDataManager data;
    private final SoilRepo soilRepo;
    private final PlantRepo plantRepo;
    private final MutationBus bus;
    private final SchematicManager schematicManager;
    private final Random random = new Random();

    public PlantActions(SimulationDataManager data, SoilRepo soilRepo, PlantRepo plantRepo, MutationBus bus, SchematicManager schematicManager) {
        this.data = data;
        this.soilRepo = soilRepo;
        this.plantRepo = plantRepo;
        this.bus = bus;
        this.schematicManager = schematicManager;
    }

    // ------------------------------------------------------------------------
    // Soil Actions
    // ------------------------------------------------------------------------

    /**
     * Hoe soil to prepare it for planting.
     * Creates/updates SoilState, rolls and assigns qualityMult based on the hoe.
     */
    public void hoeSoil(BlockPos pos, String soilId, HoeStats hoeStats) {
        Instant now = Instant.now();
        
        // Get soil spec to retrieve block type
        var soilSpec = data.soils().get(soilId);
        if (soilSpec == null) {
            // Invalid soil ID, can't proceed
            return;
        }
        
        // Roll quality multiplier based on hoe stats
        double qualityMult = rollQualityMultiplier(hoeStats);
        
        // Create or update soil instance
        SoilInstance soil = soilRepo.get(pos);
        if (soil == null) {
            soil = new SoilInstance(pos, soilId, qualityMult, 50.0, 50.0, now); // Default levels
        } else {
            soil.soilId = soilId;
            soil.qualityMult = qualityMult;
            soil.lastUpdateAt = now;
        }
        
        // Set next update for evaporation
        soil.nextUpdateAt = now.plusSeconds(15); // Safety wake
        
        soilRepo.upsert(soil);
        
        // Update visual block with soil's block type
        bus.queue(new BlockMutation(pos, soilSpec.block, null));
        
        // Play hoe sound
        bus.queue(new SoundMutation(pos, "item.hoe.till", 1.0f, 1.0f));
    }

    /**
     * Add water to soil.
     */
    public void waterSoil(BlockPos pos, double units) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil != null) {
            soil.water = Math.min(100.0, soil.water + units);
            soil.lastUpdateAt = Instant.now();
            soil.nextUpdateAt = soil.lastUpdateAt.plusSeconds(15); // Reschedule
            
            soilRepo.upsert(soil);
            wakeOccupantPlant(pos);
        }
    }

    /**
     * Drain water from soil.
     */
    public void drainSoil(BlockPos pos, double units) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil != null) {
            soil.water = Math.max(0.0, soil.water - units);
            soil.lastUpdateAt = Instant.now();
            soil.nextUpdateAt = soil.lastUpdateAt.plusSeconds(15); // Reschedule
            
            soilRepo.upsert(soil);
            wakeOccupantPlant(pos);
        }
    }

    /**
     * Add nutrients to soil.
     */
    public void fertilizeSoil(BlockPos pos, double units) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil != null) {
            soil.nutrients = Math.min(100.0, soil.nutrients + units);
            soil.lastUpdateAt = Instant.now();
            soil.nextUpdateAt = soil.lastUpdateAt.plusSeconds(15); // Reschedule
            
            soilRepo.upsert(soil);
            wakeOccupantPlant(pos);
        }
    }

    /**
     * Leach soil (drain water while keeping some nutrients).
     */
    public void leachSoil(BlockPos pos, double keepFactor, double waterBoost) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil != null) {
            soil.nutrients = soil.nutrients * keepFactor;
            soil.water = Math.min(100.0, soil.water + waterBoost);
            soil.lastUpdateAt = Instant.now();
            soil.nextUpdateAt = soil.lastUpdateAt.plusSeconds(15); // Reschedule
            
            soilRepo.upsert(soil);
            wakeOccupantPlant(pos);
        }
    }

    /**
     * Bind nutrients in soil (reduce nutrient levels).
     */
    public void bindNutrients(BlockPos pos, double units) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil != null) {
            // Positive units = add nutrients, negative units = remove nutrients
            soil.nutrients = Math.max(0.0, Math.min(100.0, soil.nutrients + units));
            soil.lastUpdateAt = Instant.now();
            soil.nextUpdateAt = soil.lastUpdateAt.plusSeconds(15); // Reschedule
            
            soilRepo.upsert(soil);
            wakeOccupantPlant(pos);
        }
    }

    // ------------------------------------------------------------------------
    // Plant Actions
    // ------------------------------------------------------------------------

    /**
     * Plant a seed on soil.
     * Resolves soilPos, creates PlantInstance, schedules first step.
     */
    public void plantSeed(String playerId, String speciesId, BlockPos plantPos) {
        // Find soil below the plant
        BlockPos soilPos = plantPos.below();
        SoilInstance soil = soilRepo.get(soilPos);
        
        if (soil == null) {
            // No soil found, cannot plant
            return;
        }

        // Check if there's already a plant at this position
        PlantInstance existing = plantRepo.get(plantPos);
        if (existing != null) {
            return; // Position occupied
        }

        // Create new plant instance
        UUID plantId = UUID.randomUUID();
        PlantInstance plant = new PlantInstance(plantId, speciesId, playerId, plantPos, soilPos);
        
        // Assign random rotation if allowed by spec
        PlantSpec spec = data.plants().get(speciesId);
        if (spec != null && spec.allowRotation) {
            int[] possibleRotations = {0, 90, 180, 270};
            plant.rotation = possibleRotations[random.nextInt(possibleRotations.length)];
        }
        
        // Set initial scheduling
        plant.lastSimAt = Instant.now();
        plant.nextUpdateAt = plant.lastSimAt.plusSeconds(1); // Wake soon for first simulation
        
        plantRepo.upsert(plant);
    }

    /**
     * Harvest a mature plant.
     * Computes drops (fertility × loot mult), applies world changes via mutation bus.
     * Handles both single-block plants and multi-block schematic plants.
     * Supports renewable harvest (regresses to 2nd-to-last stage) if destroyOnHarvest = false.
     * 
     * @return true if harvest succeeded, false if plant not ready
     */
    public boolean harvestPlant(BlockPos pos, double hoeLootLevel) {
        PlantInstance plant = plantRepo.get(pos);
        if (plant == null) {
            return false; // No plant
        }
        
        if (!plant.complete) {
            return false; // Not ready to harvest
        }

        // Get plant spec for drops
        var spec = data.plants().get(plant.speciesId);
        if (spec == null) {
            return false;
        }

        // Calculate effective fertility (base + bonus from loot enchantments)
        double lootMultiplier = 1.0 + hoeLootLevel;
        double effectiveFertility = plant.fertility * lootMultiplier;

        // Generate drops
        if (spec.drops != null) {
            for (var drop : spec.drops) {
                // Apply fertility as a flat bonus to the range (supports negative fertility)
                double adjustedMin = Math.max(0, drop.min + effectiveFertility);
                double adjustedMax = Math.max(0, drop.max + effectiveFertility);
                
                // Random roll within the adjusted range, rounded down
                int actualDrop = (int) Math.floor(adjustedMin + Math.random() * (adjustedMax - adjustedMin));
                
                if (actualDrop > 0) {
                    if (drop.makeGroups && actualDrop > 1) {
                        // Split drops into 2-3 groups at different positions
                        int groupCount = 2 + random.nextInt(2); // 2 or 3 groups
                        int itemsPerGroup = actualDrop / groupCount;
                        int remainder = actualDrop % groupCount;
                        
                        for (int i = 0; i < groupCount; i++) {
                            int groupSize = itemsPerGroup + (i < remainder ? 1 : 0); // Distribute remainder
                            if (groupSize > 0) {
                                spawnDropGroup(pos, drop, groupSize);
                            }
                        }
                    } else {
                        // Single drop at one position
                        spawnDropGroup(pos, drop, actualDrop);
                    }
                }
            }
        }

        // Play harvest sound
        bus.queue(new SoundMutation(pos, "block.crop.break", 1.0f, 1.0f));

        // Handle destructive vs renewable harvest
        if (spec.destroyOnHarvest) {
            // DESTRUCTIVE: Remove plant entirely
            removeVisualBlocks(plant, spec);
            plantRepo.remove(plant);
        } else {
            // RENEWABLE: Regress to 2nd-to-last stage and regrow
            regressPlantStage(plant, spec);
        }
        
        return true;
    }
    
    /**
     * Remove visual blocks (single block or schematic) for destructive harvest.
     */
    private void removeVisualBlocks(PlantInstance plant, gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec spec) {
        if (spec.mutations != null && spec.mutations.stages != null && !spec.mutations.stages.isEmpty()) {
            String lastStage = spec.mutations.stages.get(spec.mutations.stages.size() - 1);
            
            // Check if this is a schematic (starts with "schematic:")
            if (lastStage.startsWith("schematic:")) {
                String schematicId = lastStage.substring("schematic:".length());
                removeSchematic(plant.pos, schematicId, plant.rotation);
            } else {
                // Regular single block - clear to AIR
                bus.queue(new BlockMutation(plant.pos, "AIR", null));
            }
        } else {
            // No visuals defined - just clear the block
            bus.queue(new BlockMutation(plant.pos, "AIR", null));
        }
    }
    
    /**
     * Regress plant to 2nd-to-last stage for renewable harvest.
     * Sets progress to start of 2nd-to-last stage, marks as incomplete, and updates visual.
     */
    private void regressPlantStage(PlantInstance plant, gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec spec) {
        if (spec.mutations == null || spec.mutations.stages == null || spec.mutations.stages.isEmpty()) {
            return;
        }
        
        int stageCount = spec.mutations.stages.size();
        if (stageCount < 2) {
            // Safety check: need at least 2 stages for renewable harvest
            return;
        }
        
        // Calculate progress for start of 2nd-to-last stage
        // For N stages: stage boundaries are at 0%, 100/(N-1)%, 200/(N-1)%, ..., 100%
        // 2nd-to-last stage index = N-2
        // Its start progress = (N-2) / (N-1) + small epsilon
        // Add 1% buffer to ensure we're firmly inside the target stage (not at the edge)
        int targetStageIndex = stageCount - 2;
        double targetProgress = (targetStageIndex / (double)(stageCount - 1)) + 0.01;
        
        // Update plant state
        plant.progress = targetProgress;
        plant.complete = false;
        plant.lastSimAt = Instant.now();
        plant.nextUpdateAt = plant.lastSimAt.plusSeconds(1); // Resume simulation soon
        
        // Get 2nd-to-last stage visual
        String targetStage = spec.mutations.stages.get(targetStageIndex);
        boolean targetIsSchematic = targetStage.startsWith("schematic:");
        String targetSchematicId = targetIsSchematic ? targetStage.substring("schematic:".length()) : null;
        
        // SMART TRANSITION: Only remove blocks that don't overlap with new stage
        if (plant.currentSchematicId != null) {
            TreeSchematic oldSchematic = schematicManager.getSchematic(plant.currentSchematicId);
            if (oldSchematic != null) {
                // Build set of positions from new stage for fast lookup
                java.util.Set<BlockPos> newBlockPositions = new java.util.HashSet<>();
                
                if (targetIsSchematic) {
                    // New stage is also a schematic - check for overlaps
                    TreeSchematic newSchematic = schematicManager.getSchematic(targetSchematicId);
                    if (newSchematic != null) {
                        for (TreeSchematic.SchematicBlock block : newSchematic.blocks()) {
                            // Apply rotation
                            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), plant.rotation);
                            BlockPos blockPos = new BlockPos(
                                plant.pos.world(),
                                plant.pos.x() + rotated[0],
                                plant.pos.y() + block.y(),
                                plant.pos.z() + rotated[1]
                            );
                            newBlockPositions.add(blockPos);
                        }
                    }
                } else {
                    // New stage is single block at root
                    newBlockPositions.add(plant.pos);
                }
                
                // Only remove old blocks that DON'T exist in new stage (no flicker!)
                for (TreeSchematic.SchematicBlock block : oldSchematic.blocks()) {
                    // Apply rotation
                    int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), plant.rotation);
                    BlockPos blockPos = new BlockPos(
                        plant.pos.world(),
                        plant.pos.x() + rotated[0],
                        plant.pos.y() + block.y(),
                        plant.pos.z() + rotated[1]
                    );
                    if (!newBlockPositions.contains(blockPos)) {
                        bus.queue(new BlockMutation(blockPos, "AIR", null));
                    }
                }
            }
            plant.currentSchematicId = null;
        }
        
        // SECOND: Place new stage visual (will overwrite overlapping blocks seamlessly)
        if (targetIsSchematic) {
            // If 2nd-to-last is also a schematic (edge case)
            TreeSchematic newSchematic = schematicManager.getSchematic(targetSchematicId);
            if (newSchematic != null) {
                for (TreeSchematic.SchematicBlock block : newSchematic.blocks()) {
                    // Apply rotation
                    int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), plant.rotation);
                    BlockPos blockPos = new BlockPos(
                        plant.pos.world(),
                        plant.pos.x() + rotated[0],
                        plant.pos.y() + block.y(),
                        plant.pos.z() + rotated[1]
                    );
                    bus.queue(new BlockMutation(blockPos, block.material(), null));
                }
                plant.currentSchematicId = targetSchematicId;
            }
        } else {
            // Regular block placement at root position
            bus.queue(new BlockMutation(plant.pos, targetStage, null));
        }
        
        // Update plant in repository
        plantRepo.upsert(plant);
    }

    /**
     * Remove a multi-block schematic from the world.
     * Uses "surgical" removal - only removes blocks defined in the schematic.
     * Applies rotation around Y-axis if specified.
     */
    private void removeSchematic(BlockPos rootPos, String schematicId, int rotation) {
        TreeSchematic schematic = schematicManager.getSchematic(schematicId);
        if (schematic == null) {
            return; // Schematic not found, silently fail
        }

        // Remove all blocks from the schematic
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            // Apply rotation
            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), rotation);
            BlockPos blockPos = new BlockPos(
                rootPos.world(),
                rootPos.x() + rotated[0],
                rootPos.y() + block.y(),
                rootPos.z() + rotated[1]
            );
            bus.queue(new BlockMutation(blockPos, "AIR", null));
        }
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

    /**
     * Spawn a group of items at a random position within the drop range.
     */
    private void spawnDropGroup(BlockPos plantPos, PlantSpec.Drop drop, int quantity) {
        // Calculate random position using configured range
        double radius = drop.dropRangeMin + Math.random() * (drop.dropRangeMax - drop.dropRangeMin);
        double angle = Math.random() * 2 * Math.PI; // Random angle around plant
        
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;
        
        BlockPos dropPos = new BlockPos(
            plantPos.world(),
            (int) Math.round(plantPos.x() + offsetX),
            (int) Math.round(plantPos.y() + drop.dropHeight),
            (int) Math.round(plantPos.z() + offsetZ)
        );
        bus.queue(new ItemDropMutation(dropPos, drop.id, quantity, null));
    }

    private double rollQualityMultiplier(HoeStats hoeStats) {
        // Base quality around 1.0 with some variance
        double base = 1.0;
        double variance = hoeStats.qualityVariance();
        double roll = random.nextGaussian() * variance;
        return Math.max(0.1, Math.min(3.0, base + roll));
    }

    private void wakeOccupantPlant(BlockPos soilPos) {
        // Find any plants that are linked to this soil
        List<PlantInstance> plants = plantRepo.findBySoilPos(soilPos);
        for (PlantInstance plant : plants) {
            // Wake the plant for immediate rescheduling
            plant.nextUpdateAt = Instant.now().plusMillis(100); // Very soon
            plantRepo.upsert(plant);
        }
    }

    // ------------------------------------------------------------------------
    // Data Classes
    // ------------------------------------------------------------------------

    public record HoeStats(
        double qualityVariance,  // Standard deviation for quality rolls
        double lootMultiplier    // Multiplier for harvest drops
    ) {}
}
