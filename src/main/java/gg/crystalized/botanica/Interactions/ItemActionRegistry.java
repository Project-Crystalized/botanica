package gg.crystalized.botanica.Interactions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.crystalized.botanica.Botanica;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Registry for item-to-action mappings.
 * Provides fast lookup of what actions an item can perform and the stats for each action.
 */
public class ItemActionRegistry {
    
    // Multi-map: itemId -> action -> stats
    private final Map<String, Map<String, ActionStats>> itemToActions = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Stats for a specific item-action combination.
     */
    public static class ActionStats {
        public String action;
        public String plantSpecId;     // For PLANT actions
        public double qualityMin = 1.0; // For TILL_SOIL
        public double qualityMax = 1.0; // For TILL_SOIL
        public double amount = 10.0;   // For resource actions
        public boolean consume = true;  // Whether to consume 1 from stack (default true for most items)
        public String give;            // Item to give player after action (optional)
    }
    
    /**
     * Load item actions from config file.
     */
    public void load() {
        File configFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/ItemActions.json");
        
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            ItemActionConfig config = gson.fromJson(reader, ItemActionConfig.class);
            if (config != null) {
                int totalItems = 0;
                if (config.TILL_SOIL != null) {
                    totalItems += loadTillSoil(config.TILL_SOIL);
                }
                totalItems += loadHarvestPlant(config.HARVEST_PLANT);
                totalItems += loadPlant(config.PLANT);
                totalItems += loadResourceAction("ADD_WATER", config.ADD_WATER);
                totalItems += loadResourceAction("REMOVE_WATER", config.REMOVE_WATER);
                totalItems += loadResourceAction("ADD_NUTRIENTS", config.ADD_NUTRIENTS);
                totalItems += loadResourceAction("REMOVE_NUTRIENTS", config.REMOVE_NUTRIENTS);
                
                Botanica.INSTANCE.getLogger().info("Loaded " + totalItems + " item-action mappings across " + itemToActions.size() + " unique items");
            }
        } catch (IOException e) {
            Botanica.INSTANCE.getLogger().severe("Failed to load ItemActions.json: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int loadTillSoil(List<ItemActionConfig.TillSoilItem> items) {
        if (items == null) return 0;
        int count = 0;
        
        for (ItemActionConfig.TillSoilItem item : items) {
            // Validation
            if (item.itemId == null || item.itemId.isBlank()) {
                Botanica.INSTANCE.getLogger().warning("TILL_SOIL entry missing 'itemId', skipping");
                continue;
            }
            
            ActionStats stats = new ActionStats();
            stats.action = "TILL_SOIL";
            stats.qualityMin = item.qualityMin != null ? item.qualityMin : 1.0;
            stats.qualityMax = item.qualityMax != null ? item.qualityMax : 1.0;
            stats.consume = item.consume != null ? item.consume : false; // Tools don't consume by default
            stats.give = item.give;
            
            // Validate quality range
            if (stats.qualityMin > stats.qualityMax) {
                Botanica.INSTANCE.getLogger().warning("TILL_SOIL entry for '" + item.itemId + "' has qualityMin > qualityMax, swapping values");
                double temp = stats.qualityMin;
                stats.qualityMin = stats.qualityMax;
                stats.qualityMax = temp;
            }
            
            registerAction(item.itemId, "TILL_SOIL", stats);
            count++;
        }
        
        return count;
    }
    
    private int loadHarvestPlant(List<ItemActionConfig.HarvestPlantItem> items) {
        if (items == null) return 0;
        int count = 0;
        
        for (ItemActionConfig.HarvestPlantItem item : items) {
            // Validation
            if (item.itemId == null || item.itemId.isBlank()) {
                Botanica.INSTANCE.getLogger().warning("HARVEST_PLANT entry missing 'itemId', skipping");
                continue;
            }
            
            ActionStats stats = new ActionStats();
            stats.action = "HARVEST_PLANT";
            stats.consume = item.consume != null ? item.consume : false; // Tools don't consume by default
            stats.give = item.give;
            
            registerAction(item.itemId, "HARVEST_PLANT", stats);
            count++;
        }
        
        return count;
    }
    
    private int loadPlant(List<ItemActionConfig.PlantItem> items) {
        if (items == null) return 0;
        int count = 0;
        
        for (ItemActionConfig.PlantItem item : items) {
            // Validation
            if (item.itemId == null || item.itemId.isBlank()) {
                Botanica.INSTANCE.getLogger().warning("PLANT entry missing 'itemId', skipping");
                continue;
            }
            
            if (item.plantSpecId == null || item.plantSpecId.isBlank()) {
                Botanica.INSTANCE.getLogger().warning("PLANT entry for '" + item.itemId + "' missing 'plantSpecId', skipping");
                continue;
            }
            
            // Validate that plant spec exists
            if (Botanica.sdm.plants().get(item.plantSpecId) == null) {
                Botanica.INSTANCE.getLogger().warning("PLANT entry for '" + item.itemId + "' references unknown plantSpecId '" + item.plantSpecId + "', skipping");
                continue;
            }
            
            ActionStats stats = new ActionStats();
            stats.action = "PLANT";
            stats.plantSpecId = item.plantSpecId;
            stats.consume = item.consume != null ? item.consume : true; // Seeds consume by default
            stats.give = item.give;
            
            registerAction(item.itemId, "PLANT", stats);
            count++;
        }
        
        return count;
    }
    
    private int loadResourceAction(String actionName, List<ItemActionConfig.ResourceItem> items) {
        if (items == null) return 0;
        int count = 0;
        
        for (ItemActionConfig.ResourceItem item : items) {
            // Validation
            if (item.itemId == null || item.itemId.isBlank()) {
                Botanica.INSTANCE.getLogger().warning(actionName + " entry missing 'itemId', skipping");
                continue;
            }
            
            ActionStats stats = new ActionStats();
            stats.action = actionName;
            stats.amount = item.amount != null ? item.amount : 10.0;
            stats.consume = item.consume != null ? item.consume : true; // Consumables consume by default
            stats.give = item.give;
            
            // Validate amount is positive
            if (stats.amount <= 0) {
                Botanica.INSTANCE.getLogger().warning(actionName + " entry for '" + item.itemId + "' has amount <= 0, using default 10.0");
                stats.amount = 10.0;
            }
            
            registerAction(item.itemId, actionName, stats);
            count++;
        }
        
        return count;
    }
    
    private void registerAction(String itemId, String action, ActionStats stats) {
        itemToActions.computeIfAbsent(itemId, k -> new HashMap<>()).put(action, stats);
    }
    
    /**
     * Get all actions that an item can perform.
     */
    public List<String> getActionsForItem(String itemId) {
        Map<String, ActionStats> actions = itemToActions.get(itemId);
        if (actions == null) return List.of();
        return new ArrayList<>(actions.keySet());
    }
    
    /**
     * Get stats for a specific item-action combination.
     */
    public ActionStats getStatsForAction(String itemId, String action) {
        Map<String, ActionStats> actions = itemToActions.get(itemId);
        if (actions == null) return null;
        return actions.get(action);
    }
    
    /**
     * Check if an item has any registered actions.
     */
    public boolean hasActions(String itemId) {
        return itemToActions.containsKey(itemId);
    }
    
    private void createDefaultConfig(File configFile) {
        ItemActionConfig defaultConfig = new ItemActionConfig();
        
        // Documentation
        defaultConfig._documentation = Map.of(
            "actions", List.of("HARVEST_PLANT", "PLANT", "ADD_WATER", "REMOVE_WATER", "ADD_NUTRIENTS", "REMOVE_NUTRIENTS"),
            "note", "Actions are grouped as top-level keys. Add items under each action with relevant fields.",
            "consume", "If true, removes 1 from held item stack. If false, keeps item (for tools/reusable items - default true).",
            "give", "Item to add to player inventory after action (e.g., empty bucket after using water bucket - default null)."
        );
        
        // Note: TILL_SOIL is now handled directly in PlayerInteractListener for custom soil blocks
        
        // HARVEST_PLANT: Same hoes (tools, not consumed)
        ItemActionConfig.HarvestPlantItem woodHoeHarvest = createHarvest("WOODEN_HOE");
        woodHoeHarvest.consume = false;
        woodHoeHarvest.give = null;
        ItemActionConfig.HarvestPlantItem diamondHoeHarvest = createHarvest("DIAMOND_HOE");
        diamondHoeHarvest.consume = false;
        diamondHoeHarvest.give = null;
        defaultConfig.HARVEST_PLANT = List.of(woodHoeHarvest, diamondHoeHarvest);
        
        // PLANT: Seeds (consumed, no return item)
        ItemActionConfig.PlantItem oakSapling = createPlant("OAK_SAPLING", "oak_tree");
        oakSapling.consume = true;
        oakSapling.give = null;
        defaultConfig.PLANT = List.of(oakSapling);
        
        // ADD_WATER: Water bucket example (consumed, gives empty bucket)
        ItemActionConfig.ResourceItem waterBucket = createResource("WATER_BUCKET", 30.0);
        waterBucket.consume = true;
        waterBucket.give = "BUCKET";
        defaultConfig.ADD_WATER = List.of(waterBucket);
        
        // REMOVE_WATER: Sponge example (consumed, gives wet sponge)
        ItemActionConfig.ResourceItem sponge = createResource("SPONGE", 20.0);
        sponge.consume = true;
        sponge.give = "WET_SPONGE";
        defaultConfig.REMOVE_WATER = List.of(sponge);
        
        // ADD_NUTRIENTS: Bone meal (consumed, no return item)
        ItemActionConfig.ResourceItem boneMeal = createResource("BONE_MEAL", 30.0);
        boneMeal.consume = true;
        boneMeal.give = null;
        defaultConfig.ADD_NUTRIENTS = List.of(boneMeal);
        
        // REMOVE_NUTRIENTS: Charcoal (consumed, no return item)
        ItemActionConfig.ResourceItem charcoal = createResource("CHARCOAL", 20.0);
        charcoal.consume = true;
        charcoal.give = null;
        defaultConfig.REMOVE_NUTRIENTS = List.of(charcoal);
        
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(defaultConfig, writer);
            Botanica.INSTANCE.getLogger().info("Created default ItemActions.json");
        } catch (IOException e) {
            Botanica.INSTANCE.getLogger().warning("Failed to create default ItemActions.json: " + e.getMessage());
        }
    }
    
    
    private ItemActionConfig.HarvestPlantItem createHarvest(String itemId) {
        ItemActionConfig.HarvestPlantItem item = new ItemActionConfig.HarvestPlantItem();
        item.itemId = itemId;
        return item;
    }
    
    private ItemActionConfig.PlantItem createPlant(String itemId, String plantSpecId) {
        ItemActionConfig.PlantItem item = new ItemActionConfig.PlantItem();
        item.itemId = itemId;
        item.plantSpecId = plantSpecId;
        return item;
    }
    
    private ItemActionConfig.ResourceItem createResource(String itemId, double amount) {
        ItemActionConfig.ResourceItem item = new ItemActionConfig.ResourceItem();
        item.itemId = itemId;
        item.amount = amount;
        return item;
    }
}
