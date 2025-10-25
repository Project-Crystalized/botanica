package gg.crystalized.botanica.PlantSim.UI;

import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.Soil.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.Soil.Domain.SoilInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Instant;

/**
 * Formats plant and soil data for display in the action bar.
 * Uses Kyori Adventure API (Paper's native text system).
 * 
 * Features:
 * - Separate "Growth" and "Fruiting" phases with independent progress tracking
 * - Unicode progress bars for water/nutrients showing optimal/robust/bad zones
 * - Harvest drop multiplier display when in fruiting phase
 * - Real-time status updates
 */
public class ActionBarUI {
    
    private final PlantStatusUI statusUI;
    private static final int BAR_LENGTH = 20; // Total bar segments (5% per segment for better precision)
    
    public ActionBarUI(PlantStatusUI statusUI) {
        this.statusUI = statusUI;
    }
    
    /**
     * Format soil-only display (no plant).
     */
    public Component formatSoil(SoilInstance soil, SoilSpec spec) {
        // Determine if soil is ready for planting
        String statusText;
        NamedTextColor statusColor;
        
        if (soil.tilled) {
            statusText = "Ready to plant";
            statusColor = NamedTextColor.GREEN;
        } else {
            statusText = "Needs tilling";
            statusColor = NamedTextColor.YELLOW;
        }
        
        // 🌱 Loamy Soil | 💧 45/100 | 🌾 80/100 | Ready to plant
        return Component.text()
            .append(Component.text("🌱 ", NamedTextColor.GREEN))
            .append(Component.text(spec.displayName, NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatWater(soil.water))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatNutrients(soil.nutrients))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(statusText, statusColor))
            .build();
    }
    
    /**
     * Format plant with soil display.
     * Shows separate Growth/Fruiting phases with unicode progress bars, percentages, stages, and time remaining.
     */
    public Component formatPlant(PlantInstance plant, PlantSpec spec, SoilInstance soil) {
        // Calculate projected values
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();
        long elapsedMs = nowMs - plant.lastSimAt.toEpochMilli();
        double elapsedSec = elapsedMs / 1000.0;
        
        // Project water (evaporation only, simplified)
        double projectedWater = Math.max(0.0, soil.water - (0.10 * elapsedSec)); // BASE_WATER_EVAP_PER_SEC
        double projectedNutrients = soil.nutrients; // Simplified for UI
        
        // Calculate phase info
        PhaseInfo phaseInfo = calculatePhaseInfo(plant, spec);
        
        return Component.text()
            .append(getPlantIcon(spec.kind))
            .append(Component.text(spec.name, NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatResourceBar("💧", projectedWater, spec.growthFactors))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatResourceBar("🌾", projectedNutrients, spec.growthFactors))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(formatPhaseStatusWithTime(phaseInfo, plant, spec, soil, projectedWater, projectedNutrients, now))
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
     * Uses EXACT same logic as SimulationService to determine if plant can grow.
     */
    private Component formatGrowthIndicator(PlantInstance plant, PlantSpec spec, double water, double nutrients) {
        if (plant.complete) {
            return Component.empty();
        }
        
        // Check if growth is blocked using exact simulation logic
        if (spec.growthFactors == null) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✅ Growing", NamedTextColor.GREEN))
                .build();
        }
        
        Integer wMin = spec.growthFactors.waterMin;
        Integer wMax = spec.growthFactors.waterMax;
        Integer nMin = spec.growthFactors.nutMin;
        Integer nMax = spec.growthFactors.nutMax;
        Integer rob = spec.growthFactors.robustness;
        
        if (wMin == null || wMax == null || nMin == null || nMax == null) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✅ Growing", NamedTextColor.GREEN))
                .build();
        }
        
        int R = rob != null ? Math.max(0, rob) : 0;
        
        // Check ideal range (1.0x growth)
        boolean wIdeal = water >= wMin && water <= wMax;
        boolean nIdeal = nutrients >= nMin && nutrients <= nMax;
        if (wIdeal && nIdeal) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("✅ Optimal", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
        }
        
        // Check robust range (0.75x growth)
        boolean wRobust = water >= (wMin - R) && water <= (wMax + R);
        boolean nRobust = nutrients >= (nMin - R) && nutrients <= (nMax + R);
        if (wRobust && nRobust) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚡ Growing (Slow)", NamedTextColor.YELLOW))
                .build();
        }
        
        // Not growing - determine why
        if (!wRobust && !nRobust) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Water & Nutrients!", NamedTextColor.RED))
                .build();
        } else if (!wRobust) {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Water level!", NamedTextColor.RED))
                .build();
        } else {
            return Component.text()
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("⚠️ Nutrient level!", NamedTextColor.RED))
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
    
    // ------------------------------------------------------------------------
    // Enhanced UI Methods (Growth/Fruiting Phases + Resource Bars)
    // ------------------------------------------------------------------------
    
    /**
     * Calculate which phase the plant is in and progress within that phase.
     */
    private PhaseInfo calculatePhaseInfo(PlantInstance plant, PlantSpec spec) {
        if (spec.mutations == null || spec.mutations.stages == null || spec.mutations.stages.isEmpty()) {
            // Fallback: show as single-stage growth
            return new PhaseInfo("Growth", 1, 1, 0, false);
        }
        
        int growthStageCount = spec.mutations.stages.size();
        int harvestableStageCount = 0;
        if (spec.mutations.harvestableStages != null) {
            harvestableStageCount = spec.mutations.harvestableStages.size();
        }
        int totalStageCount = growthStageCount + harvestableStageCount;
        
        if (totalStageCount == 0) {
            // Fallback: show as single-stage growth
            return new PhaseInfo("Growth", 1, 1, 0, false);
        }
        
        // Calculate current stage
        int currentStageIndex;
        if (plant.complete || plant.progress >= 1.0) {
            currentStageIndex = totalStageCount - 1;
        } else {
            double stageProgress = plant.progress * 0.99;
            currentStageIndex = Math.max(0, Math.min(totalStageCount - 2, (int) (stageProgress * (totalStageCount - 1))));
        }
        
        // Determine phase
        // Special case: If at last growth stage AND there are harvestable stages, show as "Fruiting: 0"
        if (currentStageIndex == growthStageCount - 1 && harvestableStageCount > 0) {
            return new PhaseInfo("Fruiting", 0, harvestableStageCount, 0, false);
        }
        
        if (currentStageIndex < growthStageCount) {
            // In growth phase - show 0-indexed stages (seed = 0)
            // Don't include last growth stage here (it's shown as "Fruiting: 0" above)
            int currentStage = currentStageIndex; // Keep 0-indexed
            int displayMaxStage = growthStageCount - 2; // Max is second-to-last growth stage (0-indexed)
            
            return new PhaseInfo("Growth", currentStage, displayMaxStage, 0, false);
        } else {
            // In fruiting/harvestable phase (1-indexed within harvestable stages)
            int harvestableIndex = currentStageIndex - growthStageCount;
            int currentFruitStage = harvestableIndex + 1; // 1-indexed for actual harvestable stages
            double dropMultiplier = ((double) (harvestableIndex + 1) / harvestableStageCount) * 100;
            return new PhaseInfo("Fruiting", currentFruitStage, harvestableStageCount, (int) dropMultiplier, true);
        }
    }
    
    /**
     * Format phase status (Growth vs Fruiting with stage numbers).
     * Compact format showing "X/Y" where X is current stage and Y is total stages.
     */
    private Component formatPhaseStatus(PhaseInfo info) {
        // Use phaseName directly to determine label (not isHarvestable flag)
        if (info.phaseName.equals("Fruiting")) {
            return Component.text()
                .append(Component.text("Fruiting: ", NamedTextColor.GOLD))
                .append(Component.text(info.currentStage + "/" + info.totalStages, NamedTextColor.YELLOW))
                .build();
        } else {
            return Component.text()
                .append(Component.text("Growth: ", NamedTextColor.AQUA))
                .append(Component.text(info.currentStage + "/" + info.totalStages, NamedTextColor.YELLOW))
                .build();
        }
    }
    
    /**
     * Format phase status with percentage, stage numbers, and time remaining/harvestable status.
     * Format: "Growth: 45% (2/3) | Remaining: 3m 12s" or "Fruiting: 66% (2/3) | Harvestable!"
     */
    private Component formatPhaseStatusWithTime(PhaseInfo info, PlantInstance plant, PlantSpec spec, 
                                                 SoilInstance soil, double water, double nutrients, Instant now) {
        var builder = Component.text();
        
        // Check if fully complete first
        if (plant.complete || plant.progress >= 1.0) {
            // Fully mature - just show "Done" (no phase label, percentage, or stage)
            builder.append(Component.text("Done", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            // Still growing - show phase label, percentage, stage, and time remaining
            if (info.phaseName.equals("Fruiting")) {
                builder.append(Component.text("Fruiting: ", NamedTextColor.GOLD));
            } else {
                builder.append(Component.text("Growth: ", NamedTextColor.AQUA));
            }
            

            // Still growing - show percentage, stage, and time remaining
            double phasePercentage = calculatePhasePercentage(plant, spec, info);
            builder.append(Component.text((int) phasePercentage + "%", NamedTextColor.YELLOW))
                   .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                   .append(Component.text(info.currentStage + "/" + info.totalStages, NamedTextColor.GRAY))
                   .append(Component.text(")", NamedTextColor.DARK_GRAY));
            
            // Show time remaining
            double growthRate = calculateGrowthRate(spec, soil, plant, water, nutrients);
            if (growthRate > 0) {
                double remainingProgress = 1.0 - plant.progress;
                long remainingSeconds = (long) (remainingProgress / growthRate);
                
                builder.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                       .append(Component.text("Remaining: ", NamedTextColor.GRAY))
                       .append(Component.text(formatDuration(remainingSeconds), NamedTextColor.WHITE));
            }
        }
        
        return builder.build();
    }
    
    /**
     * Calculate percentage progress within the current phase.
     */
    private double calculatePhasePercentage(PlantInstance plant, PlantSpec spec, PhaseInfo info) {
        if (spec.mutations == null || spec.mutations.stages == null) {
            return plant.progress * 100;
        }
        
        int growthStageCount = spec.mutations.stages.size();
        int harvestableStageCount = spec.mutations.harvestableStages != null ? spec.mutations.harvestableStages.size() : 0;
        int totalStageCount = growthStageCount + harvestableStageCount;
        
        if (totalStageCount == 0) {
            return plant.progress * 100;
        }
        
        // Map overall progress (0-100%) to phase-specific progress
        if (info.phaseName.equals("Fruiting")) {
            // In fruiting phase - calculate progress through fruiting stages
            // Progress range for fruiting = [(growthStageCount-1)/totalStageCount, 1.0]
            double fruitingStartProgress = (double)(growthStageCount - 1) / (totalStageCount - 1);
            double fruitingRange = 1.0 - fruitingStartProgress;
            
            if (fruitingRange <= 0) return 0;
            
            double progressInFruiting = plant.progress - fruitingStartProgress;
            return Math.max(0, Math.min(100, (progressInFruiting / fruitingRange) * 100));
        } else {
            // In growth phase - calculate progress through growth stages (excluding last)
            // Progress range for growth = [0, (growthStageCount-1)/totalStageCount]
            double growthEndProgress = (double)(growthStageCount - 1) / (totalStageCount - 1);
            
            if (growthEndProgress <= 0) return 0;
            
            return Math.max(0, Math.min(100, (plant.progress / growthEndProgress) * 100));
        }
    }
    
    /**
     * Calculate current growth rate (progress per second).
     */
    private double calculateGrowthRate(PlantSpec spec, SoilInstance soil, PlantInstance plant, double water, double nutrients) {
        // Calculate window multiplier (same logic as SimulationService)
        double gate = calculateWindowMultiplier(spec, water, nutrients);
        if (gate <= 0) return 0;
        
        // Calculate effective duration
        double totalSec = Math.max(1.0, spec.baseGrowthDuration);
        totalSec = totalSec / Math.max(0.1, soil.qualityMult);
        
        if (spec.optimalSoil != null && spec.optimalSoil.equals(soil.soilId)) {
            totalSec = totalSec / 1.5;
        }
        
        return totalSec > 0 ? (gate / totalSec) : 0;
    }
    
    /**
     * Calculate window multiplier (same logic as SimulationService).
     */
    private double calculateWindowMultiplier(PlantSpec spec, double water, double nutrients) {
        if (spec.growthFactors == null) return 1.0;
        
        Integer wMin = spec.growthFactors.waterMin;
        Integer wMax = spec.growthFactors.waterMax;
        Integer nMin = spec.growthFactors.nutMin;
        Integer nMax = spec.growthFactors.nutMax;
        Integer rob = spec.growthFactors.robustness;
        
        if (wMin == null || wMax == null || nMin == null || nMax == null) return 1.0;
        
        int R = rob != null ? Math.max(0, rob) : 0;
        
        boolean wIdeal = water >= wMin && water <= wMax;
        boolean nIdeal = nutrients >= nMin && nutrients <= nMax;
        if (wIdeal && nIdeal) return 1.0;
        
        boolean wRobust = water >= (wMin - R) && water <= (wMax + R);
        boolean nRobust = nutrients >= (nMin - R) && nutrients <= (nMax + R);
        if (wRobust && nRobust) return 0.75;
        
        return 0.0;
    }
    
    /**
     * Format resource bar with colored zones (optimal/robust/bad).
     * Uses Unicode box characters for visual progress bar.
     */
    private Component formatResourceBar(String icon, double value, PlantSpec.GrowthFactors factors) {
        // Create bar: ━━━━●━━━━━
        // Green zone = optimal, Yellow zone = robust, Red zone = bad
        
        var builder = Component.text();
        builder.append(Component.text(icon + " ", NamedTextColor.WHITE));
        
        if (factors == null) {
            // No growth factors defined, just show value
            return builder.append(Component.text((int) value, getResourceColor(value))).build();
        }
        
        // Determine which resource we're displaying (water or nutrients)
        boolean isWater = icon.equals("💧");
        Integer min = isWater ? factors.waterMin : factors.nutMin;
        Integer max = isWater ? factors.waterMax : factors.nutMax;
        Integer rob = factors.robustness;
        
        if (min == null || max == null) {
            return builder.append(Component.text((int) value, getResourceColor(value))).build();
        }
        
        int R = rob != null ? Math.max(0, rob) : 0;
        int optimalMin = min;
        int optimalMax = max;
        int robustMin = min - R;
        int robustMax = max + R;
        
        // Calculate position of value marker
        int markerPos = (int) ((value / 100.0) * BAR_LENGTH);
        markerPos = Math.max(0, Math.min(BAR_LENGTH - 1, markerPos));
        
        // Build bar with colored zones
        for (int i = 0; i < BAR_LENGTH; i++) {
            // Calculate segment's center value (more accurate than start)
            double segmentStart = ((double) i / BAR_LENGTH) * 100;
            double segmentEnd = ((double) (i + 1) / BAR_LENGTH) * 100;
            double segmentCenter = (segmentStart + segmentEnd) / 2.0;
            NamedTextColor color;
            
            // Determine color based on zone (using segment center for accuracy)
            if (segmentCenter >= optimalMin && segmentCenter <= optimalMax) {
                color = NamedTextColor.GREEN; // Optimal zone
            } else if (segmentCenter >= robustMin && segmentCenter <= robustMax) {
                color = NamedTextColor.YELLOW; // Robust zone
            } else {
                color = NamedTextColor.RED; // Bad zone
            }
            
            // Draw segment (marker matches segment color for better visibility)
            if (i == markerPos) {
                builder.append(Component.text("●", color)); // Current value marker (matches zone color)
            } else {
                builder.append(Component.text("━", color));
            }
        }
        
        return builder.build();
    }
    
    /**
     * Data class for phase information.
     */
    private record PhaseInfo(
        String phaseName,       // "Growth" or "Fruiting"
        int currentStage,       // Current stage number (1-indexed)
        int totalStages,        // Total stages in this phase
        int dropMultiplier,     // Drop multiplier if harvestable (0-100)
        boolean isHarvestable   // True if in fruiting phase
    ) {}
}

