package gg.crystalized.botanica.Soil.Data;

public final class SoilSpec {
    public String id;
    public String displayName;
    public String altName; // Short name for UI display (e.g., "dirt", "sand")
    public String block;
    public String requiredSecondary; // Material needed to create this soil type (null for plain soil)
    public String bucketItemId; // Custom bucket item for this soil type (e.g., "bucket_sandy_soil")
    
    // Block arrays for different soil compositions (must be same length)
    // Each index represents a 10% increment of secondary material content
    public String[] untilledBlocks; // e.g., ["soil_sandy_1", "soil_sandy_2", ..., "soil_sandy_10"]
    public String[] tilledBlocks;   // e.g., ["tilled_soil_sandy_1", "tilled_soil_sandy_2", ..., "tilled_soil_sandy_10"]
    
    /**
     * Get the appropriate block alias based on secondary material percentage
     * @param secondaryPercentage Percentage of secondary material (0.0 to 1.0)
     * @param tilled Whether to return tilled or untilled block
     * @return Block alias string
     */
    public String getBlockForPercentage(double secondaryPercentage, boolean tilled) {
        String[] blocks = tilled ? tilledBlocks : untilledBlocks;
        
        if (blocks == null || blocks.length == 0) {
            return null;
        }
        
        // Convert percentage to array index (0-1.0 -> 0 to length-1)
        int index = (int) Math.round(secondaryPercentage * (blocks.length - 1));
        
        // Clamp to valid range
        index = Math.max(0, Math.min(index, blocks.length - 1));
        
        return blocks[index];
    }
}
