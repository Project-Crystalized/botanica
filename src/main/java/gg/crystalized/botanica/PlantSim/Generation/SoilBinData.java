package gg.crystalized.botanica.PlantSim.Generation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages NBT data persistence for soil bin blocks.
 * Stores soil bin state in chunk persistent data containers.
 */
public class SoilBinData {
    
    // NBT Keys for soil bin data
    private static final NamespacedKey TOTAL_VOLUME_KEY = new NamespacedKey("botanica", "total_volume");
    private static final NamespacedKey DIRT_COUNT_KEY = new NamespacedKey("botanica", "dirt_count");
    private static final NamespacedKey DIRT_PERCENTAGE_KEY = new NamespacedKey("botanica", "dirt_percentage");
    private static final NamespacedKey SECONDARY_PERCENTAGE_KEY = new NamespacedKey("botanica", "secondary_percentage");
    private static final NamespacedKey REMAINING_SOIL_KEY = new NamespacedKey("botanica", "remaining_soil");
    private static final NamespacedKey SOIL_TYPE_KEY = new NamespacedKey("botanica", "soil_type");
    private static final NamespacedKey LOCKED_KEY = new NamespacedKey("botanica", "locked");
    
    /**
     * Initialize a new soil bin with empty state.
     */
    public static void createSoilBin(Block block) {
        if (block.getType() != Material.COMPOSTER) {
            throw new IllegalArgumentException("Block must be a composter");
        }
        
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        
        // Initialize all soil bin data to empty state
        container.set(getKey(blockKey, TOTAL_VOLUME_KEY), PersistentDataType.INTEGER, 0);
        container.set(getKey(blockKey, DIRT_COUNT_KEY), PersistentDataType.INTEGER, 0);
        container.set(getKey(blockKey, DIRT_PERCENTAGE_KEY), PersistentDataType.DOUBLE, 0.0);
        container.set(getKey(blockKey, SECONDARY_PERCENTAGE_KEY), PersistentDataType.DOUBLE, 0.0);
        container.set(getKey(blockKey, REMAINING_SOIL_KEY), PersistentDataType.INTEGER, 0);
        container.set(getKey(blockKey, SOIL_TYPE_KEY), PersistentDataType.STRING, "");
        container.set(getKey(blockKey, LOCKED_KEY), PersistentDataType.BOOLEAN, false);
    }
    
    /**
     * Generate a unique key for this block position.
     */
    private static String getBlockKey(Block block) {
        return block.getX() + "_" + block.getY() + "_" + block.getZ();
    }
    
    /**
     * Create a namespaced key with block position prefix.
     */
    private static NamespacedKey getKey(String blockKey, NamespacedKey baseKey) {
        return new NamespacedKey(baseKey.getNamespace(), blockKey + "_" + baseKey.getKey());
    }
    
    // Getter methods
    public static int getTotalVolume(Block block) {
        if (block.getType() != Material.COMPOSTER) return 0;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, TOTAL_VOLUME_KEY), PersistentDataType.INTEGER, 0);
    }
    
    public static int getDirtCount(Block block) {
        if (block.getType() != Material.COMPOSTER) return 0;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, DIRT_COUNT_KEY), PersistentDataType.INTEGER, 0);
    }
    
    public static double getDirtPercentage(Block block) {
        if (block.getType() != Material.COMPOSTER) return 0.0;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, DIRT_PERCENTAGE_KEY), PersistentDataType.DOUBLE, 0.0);
    }
    
    public static double getSecondaryPercentage(Block block) {
        if (block.getType() != Material.COMPOSTER) return 0.0;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, SECONDARY_PERCENTAGE_KEY), PersistentDataType.DOUBLE, 0.0);
    }
    
    public static int getRemainingSoil(Block block) {
        if (block.getType() != Material.COMPOSTER) return 0;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, REMAINING_SOIL_KEY), PersistentDataType.INTEGER, 0);
    }
    
    public static String getSoilType(Block block) {
        if (block.getType() != Material.COMPOSTER) return "";
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, SOIL_TYPE_KEY), PersistentDataType.STRING, "");
    }
    
    public static boolean isLocked(Block block) {
        if (block.getType() != Material.COMPOSTER) return false;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        return container.getOrDefault(getKey(blockKey, LOCKED_KEY), PersistentDataType.BOOLEAN, false);
    }
    
    // Setter methods
    public static void setTotalVolume(Block block, int totalVolume) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, TOTAL_VOLUME_KEY), PersistentDataType.INTEGER, totalVolume);
    }
    
    public static void setDirtCount(Block block, int dirtCount) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, DIRT_COUNT_KEY), PersistentDataType.INTEGER, dirtCount);
    }
    
    public static void setDirtPercentage(Block block, double dirtPercentage) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, DIRT_PERCENTAGE_KEY), PersistentDataType.DOUBLE, dirtPercentage);
    }
    
    public static void setSecondaryPercentage(Block block, double secondaryPercentage) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, SECONDARY_PERCENTAGE_KEY), PersistentDataType.DOUBLE, secondaryPercentage);
    }
    
    public static void setRemainingSoil(Block block, int remainingSoil) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, REMAINING_SOIL_KEY), PersistentDataType.INTEGER, remainingSoil);
    }
    
    public static void setSoilType(Block block, String soilType) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, SOIL_TYPE_KEY), PersistentDataType.STRING, soilType);
    }
    
    public static void setLocked(Block block, boolean locked) {
        if (block.getType() != Material.COMPOSTER) return;
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        container.set(getKey(blockKey, LOCKED_KEY), PersistentDataType.BOOLEAN, locked);
    }
    
    /**
     * Clear all soil bin data when block is broken.
     */
    public static void clearSoilBinData(Block block) {
        if (block.getType() != Material.COMPOSTER) return;
        
        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        String blockKey = getBlockKey(block);
        
        // Remove all soil bin data for this block position
        container.remove(getKey(blockKey, TOTAL_VOLUME_KEY));
        container.remove(getKey(blockKey, DIRT_COUNT_KEY));
        container.remove(getKey(blockKey, DIRT_PERCENTAGE_KEY));
        container.remove(getKey(blockKey, SECONDARY_PERCENTAGE_KEY));
        container.remove(getKey(blockKey, REMAINING_SOIL_KEY));
        container.remove(getKey(blockKey, SOIL_TYPE_KEY));
        container.remove(getKey(blockKey, LOCKED_KEY));
    }
}
