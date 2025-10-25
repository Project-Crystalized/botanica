package gg.crystalized.botanica.PlantSim.UI;

import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantModifier;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.Soil.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.Soil.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.World.BlockPos;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * UI system for displaying plant status and countdown timers.
 * Runs at ~1 Hz for visible plants/tiles, showing live countdowns and status.
 */
public class PlantStatusUI {
    private final SimulationDataManager data;
    private final PlantRepo plantRepo;
    private final SoilRepo soilRepo;
    private final double BASE_WATER_EVAP_PER_SEC = 0.10; // Match SimulationService

    public PlantStatusUI(SimulationDataManager data, PlantRepo plantRepo, SoilRepo soilRepo) {
        this.data = data;
        this.plantRepo = plantRepo;
        this.soilRepo = soilRepo;
    }

    /**
     * Show status for a plant at the given position.
     */
    public void showPlantStatus(Player player, BlockPos pos) {
        PlantInstance plant = plantRepo.get(pos);
        if (plant == null) {
            player.sendMessage(ChatColor.RED + "No plant found at this location.");
            return;
        }

        var spec = data.plants().get(plant.speciesId);
        if (spec == null) {
            player.sendMessage(ChatColor.RED + "Unknown plant species: " + plant.speciesId);
            return;
        }

        // Get soil state
        SoilInstance soil = soilRepo.get(plant.soilPos);
        if (soil == null) {
            player.sendMessage(ChatColor.RED + "Plant soil not found!");
            return;
        }

        // Project soil levels to current time (read-only)
        SoilProjection soilProj = projectSoilToNow(soil);

        // Build status message
        StringBuilder status = new StringBuilder();
        status.append(ChatColor.GREEN).append("=== Plant Status ===\n");
        status.append(ChatColor.YELLOW).append("Species: ").append(ChatColor.WHITE).append(spec.name).append("\n");
        status.append(ChatColor.YELLOW).append("Progress: ").append(ChatColor.WHITE).append(String.format("%.1f%%", plant.progress * 100)).append("\n");
        
        if (plant.complete) {
            status.append(ChatColor.GREEN).append("Status: ").append(ChatColor.WHITE).append("Ready to harvest!").append("\n");
        } else {
            // Calculate effective growth rate
            double gate = calculateWindowMultiplier(soilProj, spec);
            double totalSec = calculateEffectiveTotalSec(spec, soil, plant);
            double rate = (gate > 0 && totalSec > 0) ? (gate / totalSec) : 0.0;
            
            if (rate <= 0) {
                status.append(ChatColor.RED).append("Status: ").append(ChatColor.WHITE).append("Not growing").append("\n");
                status.append(getGrowthProblem(soilProj, spec));
            } else {
                // Calculate time remaining
                double remainingFrac = 1.0 - plant.progress;
                double remainingSec = remainingFrac / rate;
                Duration remaining = Duration.ofSeconds((long) remainingSec);
                
                status.append(ChatColor.GREEN).append("Status: ").append(ChatColor.WHITE).append("Growing").append("\n");
                status.append(ChatColor.YELLOW).append("Time remaining: ").append(ChatColor.WHITE).append(formatDuration(remaining)).append("\n");
            }
        }

        // Show soil conditions
        status.append("\n").append(ChatColor.BLUE).append("=== Soil Conditions ===\n");
        status.append(ChatColor.YELLOW).append("Water: ").append(ChatColor.WHITE).append(String.format("%.1f", soilProj.water)).append("/100\n");
        status.append(ChatColor.YELLOW).append("Nutrients: ").append(ChatColor.WHITE).append(String.format("%.1f", soilProj.nutrients)).append("/100\n");
        status.append(ChatColor.YELLOW).append("Quality: ").append(ChatColor.WHITE).append(String.format("%.2fx", soil.qualityMult)).append("\n");

        player.sendMessage(status.toString());
    }

    /**
     * Show status for soil at the given position.
     */
    public void showSoilStatus(Player player, BlockPos pos) {
        SoilInstance soil = soilRepo.get(pos);
        if (soil == null) {
            player.sendMessage(ChatColor.RED + "No prepared soil found at this location.");
            return;
        }

        // Project soil levels to current time
        SoilProjection soilProj = projectSoilToNow(soil);

        StringBuilder status = new StringBuilder();
        status.append(ChatColor.BLUE).append("=== Soil Status ===\n");
        status.append(ChatColor.YELLOW).append("Type: ").append(ChatColor.WHITE).append(soil.soilId).append("\n");
        status.append(ChatColor.YELLOW).append("Water: ").append(ChatColor.WHITE).append(String.format("%.1f", soilProj.water)).append("/100\n");
        status.append(ChatColor.YELLOW).append("Nutrients: ").append(ChatColor.WHITE).append(String.format("%.1f", soilProj.nutrients)).append("/100\n");
        status.append(ChatColor.YELLOW).append("Quality: ").append(ChatColor.WHITE).append(String.format("%.2fx", soil.qualityMult)).append("\n");

        // Check if there's a plant on this soil
        List<PlantInstance> plants = plantRepo.findBySoilPos(pos);
        if (!plants.isEmpty()) {
            status.append(ChatColor.GREEN).append("Plants: ").append(ChatColor.WHITE).append(plants.size()).append("\n");
        } else {
            status.append(ChatColor.GRAY).append("Ready for planting").append("\n");
        }

        player.sendMessage(status.toString());
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

    private SoilProjection projectSoilToNow(SoilInstance soil) {
        Instant now = Instant.now();
        double dt = Math.max(0, (now.toEpochMilli() - soil.lastUpdateAt.toEpochMilli()) / 1000.0);
        
        // Apply evaporation
        double waterLoss = BASE_WATER_EVAP_PER_SEC * dt;
        double projectedWater = Math.max(0.0, soil.water - waterLoss);
        
        // Nutrients don't drift (per design spec)
        double projectedNutrients = soil.nutrients;
        
        return new SoilProjection(projectedWater, projectedNutrients);
    }

    private double calculateWindowMultiplier(SoilProjection soil, PlantSpec spec) {
        if (spec.growthFactors == null) return 1.0;
        
        Integer wMin = spec.growthFactors.waterMin;
        Integer wMax = spec.growthFactors.waterMax;
        Integer nMin = spec.growthFactors.nutMin;
        Integer nMax = spec.growthFactors.nutMax;
        Integer rob = spec.growthFactors.robustness;

        if (wMin == null || wMax == null || nMin == null || nMax == null) return 1.0;

        int R = rob != null ? Math.max(0, rob) : 0;

        boolean wIdeal = within(soil.water, wMin, wMax);
        boolean nIdeal = within(soil.nutrients, nMin, nMax);
        if (wIdeal && nIdeal) return 1.0;

        boolean wRobust = within(soil.water, wMin - R, wMax + R);
        boolean nRobust = within(soil.nutrients, nMin - R, nMax + R);
        if (wRobust && nRobust) return 0.75; // ROBUST_PENALTY

        return 0.0;
    }

    private double calculateEffectiveTotalSec(PlantSpec spec, SoilInstance soil, PlantInstance plant) {
        double t = Math.max(1.0, spec.baseGrowthDuration);
        t = t / Math.max(0.1, soil.qualityMult);
        
        // Apply optimal soil bonus
        if (spec.optimalSoil != null && spec.optimalSoil.equals(soil.soilId)) {
            t = t / 1.5; // 1.5x growth speed on preferred soil
        }
        
        // Apply modifiers (simplified for UI)
        double mult = 1.0;
        for (PlantModifier mod : plant.modifiers) {
            if (mod.activeAt(Instant.now())) {
                if ("speed".equals(mod.kind())) mult *= mod.multiplier();
            }
        }
        return Math.max(1.0, t * mult);
    }

    private String getGrowthProblem(SoilProjection soil, PlantSpec spec) {
        if (spec.growthFactors == null) {
            return ChatColor.GRAY + "No growth requirements defined\n";
        }
        
        Integer wMin = spec.growthFactors.waterMin;
        Integer wMax = spec.growthFactors.waterMax;
        Integer nMin = spec.growthFactors.nutMin;
        Integer nMax = spec.growthFactors.nutMax;
        Integer rob = spec.growthFactors.robustness;

        if (wMin == null || wMax == null || nMin == null || nMax == null) {
            return ChatColor.GRAY + "No growth requirements defined\n";
        }

        int R = rob != null ? Math.max(0, rob) : 0;

        // Check water
        if (soil.water < wMin - R) {
            return ChatColor.RED + "Problem: Needs water (current: " + String.format("%.1f", soil.water) + ", need: " + wMin + "+)\n";
        }
        if (soil.water > wMax + R) {
            return ChatColor.RED + "Problem: Overwatered (current: " + String.format("%.1f", soil.water) + ", max: " + wMax + ")\n";
        }

        // Check nutrients
        if (soil.nutrients < nMin - R) {
            return ChatColor.RED + "Problem: Needs nutrients (current: " + String.format("%.1f", soil.nutrients) + ", need: " + nMin + "+)\n";
        }
        if (soil.nutrients > nMax + R) {
            return ChatColor.RED + "Problem: Too many nutrients (current: " + String.format("%.1f", soil.nutrients) + ", max: " + nMax + ")\n";
        }

        return ChatColor.GRAY + "Growth conditions are suboptimal\n";
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private boolean within(double v, double a, double b) {
        double lo = Math.min(a, b), hi = Math.max(a, b);
        return v >= lo && v <= hi;
    }

    // ------------------------------------------------------------------------
    // Data Classes
    // ------------------------------------------------------------------------

    private record SoilProjection(double water, double nutrients) {}
}
