package gg.crystalized.botanica.Interactions;

import java.util.List;
import java.util.Map;

/**
 * Configuration for item-to-action mappings.
 * Loaded from ItemActions.json.
 * 
 * Structure: Actions are top-level keys, each containing a list of items.
 */
public class ItemActionConfig {
    
    // Actions grouped by type
    public List<TillSoilItem> TILL_SOIL;
    public List<HarvestPlantItem> HARVEST_PLANT;
    public List<PlantItem> PLANT;
    public List<ResourceItem> ADD_WATER;
    public List<ResourceItem> REMOVE_WATER;
    public List<ResourceItem> ADD_NUTRIENTS;
    public List<ResourceItem> REMOVE_NUTRIENTS;
    
    // Optional documentation object (ignored during loading)
    public Map<String, Object> _documentation;
    
    /**
     * Item that can till soil (hoes).
     */
    public static class TillSoilItem {
        public String itemId;              // Material name (e.g., "WOODEN_HOE")
        public Double qualityMin;          // Minimum quality multiplier (optional, default 1.0)
        public Double qualityMax;          // Maximum quality multiplier (optional, default 1.0)
        public Boolean consume;            // Whether to consume 1 from stack (optional, default false for tools)
        public String give;                // Item to give player after use (optional)
    }
    
    /**
     * Item that can harvest plants (hoes, shears, etc.).
     */
    public static class HarvestPlantItem {
        public String itemId;              // Material name (e.g., "WOODEN_HOE")
        public Boolean consume;            // Whether to consume 1 from stack (optional, default false for tools)
        public String give;                // Item to give player after harvest (optional)
    }
    
    /**
     * Item that plants seeds/saplings.
     */
    public static class PlantItem {
        public String itemId;              // Material name (e.g., "WHEAT_SEEDS")
        public String plantSpecId;         // Plant spec to create (required)
        public Boolean consume;            // Whether to consume 1 from stack (optional, default true for seeds)
        public String give;                // Item to give player after planting (optional)
    }
    
    /**
     * Item that modifies resources (water/nutrients).
     */
    public static class ResourceItem {
        public String itemId;              // Material name (e.g., "WATER_BUCKET", "BONE_MEAL")
        public Double amount;              // Amount to add/remove (optional, default 10.0)
        public Boolean consume;            // Whether to consume 1 from stack (optional, default true for consumables)
        public String give;                // Item to give player after use (optional, e.g., empty bucket)
    }
}
