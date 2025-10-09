package gg.crystalized.botanica.PlantSim.UI;

import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;

/**
 * Formats plant and soil data for display in the action bar.
 * Uses Kyori Adventure API (Paper's native text system).
 */
public class ActionBarUI {
    
    private final PlantStatusUI statusUI;
    
    public ActionBarUI(PlantStatusUI statusUI) {
        this.statusUI = statusUI;
    }
    
    /**
     * Format soil-only display (no plant).
     */
    public Component formatSoil(SoilInstance soil, SoilSpec spec) {
        // 🌱 Loamy Soil | 💧 45/100 | 🌾 80/100 | Ready to plant
        return Component.text()
            .append(Component.text("🌱 ", NamedTextColor.GREEN))
            .append(Component.text(spec.displayName, NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatWater(soil.water))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatNutrients(soil.nutrients))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Ready to plant", NamedTextColor.GREEN))
            .build();
    }
    
    /**
     * Format plant with soil display.
     */
    public Component formatPlant(PlantInstance plant, PlantSpec spec, SoilInstance soil) {
        // Calculate projected values
        long nowMs = Instant.now().toEpochMilli();
        long elapsedMs = nowMs - plant.lastSimAt.toEpochMilli();
        double elapsedSec = elapsedMs / 1000.0;
        
        // Project water (evaporation only, simplified)
        double projectedWater = Math.max(0.0, soil.water - (0.10 * elapsedSec)); // BASE_WATER_EVAP_PER_SEC
        double projectedNutrients = soil.nutrients; // Simplified for UI
        
        // Use current progress
        double currentProgress = plant.progress;
        
        return Component.text()
            .append(getPlantIcon(spec.kind))
            .append(Component.text(spec.name, NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatGrowthStatus(plant, spec, currentProgress))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatWater(projectedWater))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatNutrients(projectedNutrients))
            .append(formatGrowthIndicator(plant, spec, projectedWater, projectedNutrients))
            .build();
    }
    
    /**
     * Format growth status (progress or completion).
     */
    private Component formatGrowthStatus(PlantInstance plant, PlantSpec spec, double progress) {
        if (plant.complete) {
            return Component.text("✅ Ready to Harvest!", NamedTextColor.GREEN, TextDecoration.BOLD);
        } else {
            int progressPercent = (int) (progress * 100);
            // Simplified time calculation - just show progress for now
            return Component.text("🌱 " + progressPercent + "%", NamedTextColor.YELLOW);
        }
    }
    
    /**
     * Format growth indicator (growing/blocked/problem).
     */
    private Component formatGrowthIndicator(PlantInstance plant, PlantSpec spec, double water, double nutrients) {
        if (plant.complete) {
            return Component.empty();
        }
        
        // Check if growth is blocked
        if (spec.growthFactors == null) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Growing...", NamedTextColor.GREEN))
                .build();
        }
        
        boolean waterOk = (spec.growthFactors.waterMin == null || water >= spec.growthFactors.waterMin) &&
                          (spec.growthFactors.waterMax == null || water <= spec.growthFactors.waterMax);
        boolean nutrientsOk = (spec.growthFactors.nutMin == null || nutrients >= spec.growthFactors.nutMin) &&
                              (spec.growthFactors.nutMax == null || nutrients <= spec.growthFactors.nutMax);
        
        if (!waterOk && !nutrientsOk) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Water & Nutrients!", NamedTextColor.RED))
                .build();
        } else if (!waterOk) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Water level!", NamedTextColor.RED))
                .build();
        } else if (!nutrientsOk) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Nutrient level!", NamedTextColor.RED))
                .build();
        } else {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Growing...", NamedTextColor.GREEN))
                .build();
        }
    }
    
    /**
     * Format water level with color coding.
     */
    private Component formatWater(double water) {
        int waterInt = (int) water;
        NamedTextColor color = getResourceColor(water);
        return Component.text("💧 " + waterInt + "/100", color);
    }
    
    /**
     * Format nutrient level with color coding.
     */
    private Component formatNutrients(double nutrients) {
        int nutrientsInt = (int) nutrients;
        NamedTextColor color = getResourceColor(nutrients);
        return Component.text("🌾 " + nutrientsInt + "/100", color);
    }
    
    /**
     * Get color based on resource level.
     */
    private NamedTextColor getResourceColor(double value) {
        if (value >= 70) return NamedTextColor.GREEN;
        if (value >= 40) return NamedTextColor.YELLOW;
        if (value >= 20) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }
    
    /**
     * Get icon for plant type.
     */
    private Component getPlantIcon(String kind) {
        String icon = switch (kind != null ? kind.toUpperCase() : "GENERIC") {
            case "TREE" -> "🌳 ";
            case "FLOWER" -> "🌸 ";
            case "VINE" -> "🌿 ";
            default -> "🌱 ";
        };
        return Component.text(icon, NamedTextColor.GREEN);
    }
    
    /**
     * Format duration in human-readable form.
     */
    private String formatDuration(long seconds) {
        if (seconds < 0) return "Ready!";
        if (seconds < 60) return seconds + "s";
        
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        
        if (minutes < 60) {
            return minutes + "m " + remainingSeconds + "s";
        }
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return hours + "h " + remainingMinutes + "m";
    }
}

