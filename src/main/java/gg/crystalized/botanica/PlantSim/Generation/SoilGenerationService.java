package gg.crystalized.botanica.PlantSim.Generation;

import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;

/**
 * Agnostic soil generation service that handles soil creation logic.
 * Can be used by various soil generation systems (soil bin, industrial processors, etc.)
 */
public class SoilGenerationService {
    
    private final SimulationDataManager dataManager;
    
    public SoilGenerationService(SimulationDataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    /**
     * Calculate soil output based on percentages and total volume.
     * 
     * @param totalVolume Total volume of ingredients
     * @param dirtPercentage Percentage of dirt (0.0-1.0)
     * @param secondaryPercentage Percentage of secondary ingredients (0.0-1.0)
     * @return SoilOutput containing soil type, concentration, and total soil count
     */
    public SoilOutput calculateOutput(int totalVolume, double dirtPercentage, double secondaryPercentage) {
        // Validate inputs
        if (totalVolume <= 0 || dirtPercentage < 0 || secondaryPercentage < 0) {
            throw new IllegalArgumentException("Invalid soil bin parameters");
        }
        
        // Calculate total soil output (1 per dirt block)
        int totalSoil = (int) Math.round(totalVolume * dirtPercentage);
        
        // Use secondary percentage as concentration (already in decimal format)
        double concentration = secondaryPercentage;
        
        // Determine soil type based on secondary percentage
        String soilType = determineSoilType(secondaryPercentage);
        
        return new SoilOutput(soilType, concentration, totalSoil);
    }
    
    /**
     * Determine soil type based on secondary percentage.
     * 
     * @param secondaryPercentage Percentage of secondary ingredients (0.0-1.0)
     * @return Soil type ID
     */
    private String determineSoilType(double secondaryPercentage) {
        // If we have secondary ingredients, determine the soil type
        if (secondaryPercentage > 0) {
            // Check all soil specs and return the first one that has a secondary ingredient requirement
            for (var soilSpec : dataManager.soils().all()) {
                if (soilSpec.requiredSecondary != null) {
                    return soilSpec.id;
                }
            }
        }
        
        // Default to plain soil if no secondary ingredients
        return "PLAIN";
    }
    
    /**
     * Data class representing soil generation output.
     */
    public static class SoilOutput {
        public final String soilType;
        public final double concentration;
        public final int totalSoil;
        
        public SoilOutput(String soilType, double concentration, int totalSoil) {
            this.soilType = soilType;
            this.concentration = concentration;
            this.totalSoil = totalSoil;
        }
        
        @Override
        public String toString() {
            return String.format("SoilOutput{type=%s, concentration=%.3f, total=%d}", 
                soilType, concentration, totalSoil);
        }
    }
}
