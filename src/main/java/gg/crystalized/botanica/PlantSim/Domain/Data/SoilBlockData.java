package gg.crystalized.botanica.PlantSim.Domain.Data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Helper class to identify and extract data from custom soil blocks.
 * Uses an in-memory map to track soil composition data by block coordinates.
 */
public class SoilBlockData {
    
    // In-memory storage for soil composition data
    // Key: "world:x:y:z" -> Value: SoilBucketData
    private static final Map<String, SoilBucketData> soilDataMap = new ConcurrentHashMap<>();
    
    /**
     * Check if a block is a custom soil block that can be picked up.
     */
    public static boolean isCustomSoilBlock(Block block) {
        // Check if this is one of our custom soil blocks
        // For now, we'll check for mushroom blocks (our custom soil bases)
        return block.getType() == Material.RED_MUSHROOM_BLOCK || 
               block.getType() == Material.BROWN_MUSHROOM_BLOCK ||
               block.getType() == Material.MUSHROOM_STEM;
    }
    
    /**
     * Extract soil data from a custom soil block by looking up coordinates in memory map.
     * 
     * @param block The soil block to extract data from
     * @return SoilBucketData or null if not a valid soil block or no data stored
     */
    public static SoilBucketData extractFromBlock(Block block) {
        if (!isCustomSoilBlock(block)) {
            return null;
        }
        
        String key = getBlockKey(block);
        return soilDataMap.get(key);
    }
    
    /**
     * Store soil data in the in-memory map using block coordinates as key.
     */
    public static void storeSoilDataInBlock(Block block, SoilBucketData soilData) {
        if (!isCustomSoilBlock(block) || soilData == null) {
            return;
        }
        
        String key = getBlockKey(block);
        soilDataMap.put(key, soilData);
    }
    
    /**
     * Remove soil data from the in-memory map when a block is broken.
     */
    public static void removeSoilDataFromBlock(Block block) {
        if (!isCustomSoilBlock(block)) {
            return;
        }
        
        String key = getBlockKey(block);
        soilDataMap.remove(key);
    }
    
    /**
     * Generate a unique key for a block position.
     */
    private static String getBlockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
    
    /**
     * Generate a unique key for a location.
     */
    private static String getLocationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
    
    /**
     * Get all stored soil data for persistence.
     * Returns a copy of the current soil data map.
     */
    public static Map<String, SoilBucketData> getAllSoilData() {
        return new ConcurrentHashMap<>(soilDataMap);
    }
    
    /**
     * Load soil data from persistence.
     * This will be used when loading from SQL/database in the future.
     */
    public static void loadSoilData(Map<String, SoilBucketData> data) {
        soilDataMap.clear();
        soilDataMap.putAll(data);
    }
    
    /**
     * Get the appropriate block alias for placing soil based on soil data.
     * This will be used to determine which custom block to place.
     */
    public static String getBlockAliasForSoilData(SoilBucketData soilData, boolean tilled) {
        if (soilData == null) {
            return null;
        }
        
        String soilType = soilData.getSoilType();
        double secondaryPercentage = soilData.getSecondaryPercentage();
        
        // TODO: Use SoilSpec to get the correct block alias
        // For now, return placeholder aliases
        if (soilType.equals("PLAIN")) {
            return tilled ? "tilled_soil" : "soil";
        } else if (soilType.equals("SANDY")) {
            // Calculate which sandy soil variant based on percentage
            int variant = (int) Math.round(secondaryPercentage * 10);
            variant = Math.max(1, Math.min(10, variant)); // Clamp to 1-10
            
            return tilled ? "tilled_soil_sandy_" + variant : "soil_sandy_" + variant;
        }
        
        return null;
    }
}