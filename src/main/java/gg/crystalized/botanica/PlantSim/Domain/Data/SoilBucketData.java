package gg.crystalized.botanica.PlantSim.Domain.Data;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Handles NBT data for soil bucket items.
 * Stores soil type and secondary percentage for bucket-to-bucket transfers.
 */
public class SoilBucketData {
    
    // NBT keys for soil bucket metadata
    public static final NamespacedKey KEY_SOIL_TYPE = new NamespacedKey("botanica", "soil_type");
    public static final NamespacedKey KEY_SECONDARY_PERCENTAGE = new NamespacedKey("botanica", "secondary_percentage");
    
    /**
     * Create a filled soil bucket with NBT metadata.
     * @param bucketItemId The custom bucket item ID from SoilSpec (e.g., "soil_bucket_sandy")
     * @param soilType The soil type (e.g., "SANDY", "PLAIN")
     * @param secondaryPercentage Percentage of secondary material (0.0 to 1.0)
     * @param aliasManager The BlockAliasManager to resolve custom model data
     * @return ItemStack with NBT data (custom item with custom model)
     */
    public static ItemStack createFilledBucket(String bucketItemId, String soilType, double secondaryPercentage, gg.crystalized.botanica.PlantSim.World.BlockAliasManager aliasManager) {
        // Use BlockAliasManager to get material dynamically
        String materialName = aliasManager.resolveItemMaterial(bucketItemId);
        org.bukkit.Material material = org.bukkit.Material.FLINT; // Default fallback
        
        if (materialName != null) {
            org.bukkit.Material resolvedMaterial = org.bukkit.Material.matchMaterial(materialName);
            if (resolvedMaterial != null) {
                material = resolvedMaterial;
            }
        }
        
        ItemStack bucket = new ItemStack(material);
        ItemMeta meta = bucket.getItemMeta();
        
        if (meta != null) {
            // Set display name based on soil type and percentage
            String displayName = formatBucketName(soilType, secondaryPercentage);
            meta.setDisplayName(displayName);
            
            // Set lore with composition info
            List<String> lore = List.of(
                formatSecondaryComposition(soilType, secondaryPercentage)
            );
            meta.setLore(lore);
            
            // Store NBT data
            meta.getPersistentDataContainer().set(KEY_SOIL_TYPE, PersistentDataType.STRING, soilType);
            meta.getPersistentDataContainer().set(KEY_SECONDARY_PERCENTAGE, PersistentDataType.DOUBLE, secondaryPercentage);
            
            // Store the bucket item ID for reference
            meta.getPersistentDataContainer().set(
                new NamespacedKey("botanica", "bucket_item_id"), 
                PersistentDataType.STRING, 
                bucketItemId
            );
            
            // Use the new 1.21+ CustomModelDataComponent API (the proper way!)
            org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            if (cmd != null) {
                // Set the strings list for the custom model data
                cmd.setStrings(java.util.List.of(bucketItemId));
                
                // Clear any old numeric custom model data
                meta.setCustomModelData(null);
                
                // Set the component back
                meta.setCustomModelDataComponent(cmd);
            }
            
            // Debug logging
            gg.crystalized.botanica.Botanica.INSTANCE.getLogger().info("Creating bucket with ID: " + bucketItemId);
            gg.crystalized.botanica.Botanica.INSTANCE.getLogger().info("Material: " + material.name() + ", Custom Model Data String: " + bucketItemId);
            
            bucket.setItemMeta(meta);
        }
        
        return bucket;
    }
    
    /**
     * Extract soil data from a filled bucket.
     * @param bucket The filled soil bucket
     * @return SoilBucketData or null if not a valid soil bucket
     */
    public static SoilBucketData fromBucket(ItemStack bucket) {
        if (bucket == null || bucket.getItemMeta() == null) {
            return null;
        }
        
        ItemMeta meta = bucket.getItemMeta();
        var container = meta.getPersistentDataContainer();
        
        // Check if this is a soil bucket
        if (!container.has(KEY_SOIL_TYPE, PersistentDataType.STRING) || 
            !container.has(KEY_SECONDARY_PERCENTAGE, PersistentDataType.DOUBLE)) {
            return null;
        }
        
        String soilType = container.get(KEY_SOIL_TYPE, PersistentDataType.STRING);
        double secondaryPercentage = container.get(KEY_SECONDARY_PERCENTAGE, PersistentDataType.DOUBLE);
        
        return new SoilBucketData(soilType, secondaryPercentage);
    }
    
    /**
     * Check if an item is a filled soil bucket (custom string with soil NBT data).
     */
    public static boolean isSoilBucket(ItemStack item) {
        return fromBucket(item) != null;
    }
    
    /**
     * Check if an item is an empty bucket (regular vanilla bucket).
     */
    public static boolean isEmptyBucket(ItemStack item) {
        return item != null && item.getType() == org.bukkit.Material.BUCKET;
    }
    
    
    private final String soilType;
    private final double secondaryPercentage;
    
    public SoilBucketData(String soilType, double secondaryPercentage) {
        this.soilType = soilType;
        this.secondaryPercentage = secondaryPercentage;
    }
    
    public String getSoilType() {
        return soilType;
    }
    
    public double getSecondaryPercentage() {
        return secondaryPercentage;
    }
    
    /**
     * Format bucket display name based on soil type and percentage.
     */
    private static String formatBucketName(String soilType, double secondaryPercentage) {
        String soilDisplayName = switch (soilType) {
            case "PLAIN" -> "Plain Soil";
            case "SANDY" -> "Sandy Soil";
            case "CLAY" -> "Clay Soil";
            case "LOAMY" -> "Loamy Soil";
            default -> soilType + " Soil";
        };
        
        // Include percentage in the name for better inventory management
        if (soilType.equals("PLAIN") || secondaryPercentage == 0.0) {
            return "Bucket of " + soilDisplayName + " (100%)";
        } else {
            int percentage = (int) Math.round(secondaryPercentage * 100);
            return "Bucket of " + soilDisplayName + " (" + percentage + "%)";
        }
    }
    
    /**
     * Format secondary composition for lore.
     */
    private static String formatSecondaryComposition(String soilType, double secondaryPercentage) {
        if (soilType.equals("PLAIN") || secondaryPercentage == 0.0) {
            return "100% Base Soil";
        }
        
        String secondaryName = switch (soilType) {
            case "SANDY" -> "Sand";
            case "CLAY" -> "Clay";
            case "LOAMY" -> "Loam";
            default -> soilType;
        };
        
        int percentage = (int) Math.round(secondaryPercentage * 100);
        return percentage + "% " + secondaryName;
    }
    
    // ========================================
    // CONVENIENT WRAPPER METHODS FOR SOIL CREATION
    // ========================================
    
    /**
     * Convenient method to create any soil bucket by type and percentage.
     * This method dynamically reads from the loaded soil specs to get the correct bucketItemId.
     * @param soilType The soil type ID (PLAIN, SANDY, CLAY, LOAMY, etc.)
     * @param secondaryPercentage Percentage of secondary material (0.0 to 1.0)
     * @param aliasManager The BlockAliasManager for resolving bucket models
     * @return ItemStack of soil bucket, or null if soil type not found
     */
    public static ItemStack createSoilBucket(String soilType, double secondaryPercentage, gg.crystalized.botanica.PlantSim.World.BlockAliasManager aliasManager) {
        // Get the soil spec for this type
        gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec soilSpec = getSoilSpecById(soilType);
        if (soilSpec == null) {
            gg.crystalized.botanica.Botanica.INSTANCE.getLogger().warning("Unknown soil type: " + soilType);
            return null;
        }
        
        // Use the bucketItemId from the soil spec
        return createFilledBucket(soilSpec.bucketItemId, soilType, secondaryPercentage, aliasManager);
    }
    
    /**
     * Get the soil spec by ID from the loaded soil specs.
     * This method dynamically looks up soil specs instead of hardcoding.
     */
    private static gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec getSoilSpecById(String soilId) {
        // Get the soil specs from the simulation service
        try {
            gg.crystalized.botanica.PlantSim.Sim.SimulationService simulationService = gg.crystalized.botanica.Botanica.INSTANCE.getSimulationService();
            if (simulationService != null) {
                return simulationService.getSoilSpecById(soilId);
            }
        } catch (Exception e) {
            gg.crystalized.botanica.Botanica.INSTANCE.getLogger().warning("Failed to get soil spec: " + e.getMessage());
        }
        return null;
    }
}
