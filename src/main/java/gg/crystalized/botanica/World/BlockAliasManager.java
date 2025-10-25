package gg.crystalized.botanica.World;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import gg.crystalized.botanica.Botanica;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages friendly aliases for block states and item custom model data.
 * Allows schematics to use short names (e.g., "oak_leaves_full_0_0") 
 * instead of verbose block state syntax (e.g., "BROWN_MUSHROOM_BLOCK[north=true,...]").
 * Also manages item aliases for base materials (e.g., "soil_bucket" -> "FLINT").
 */
public class BlockAliasManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> blockAliases = new HashMap<>();
    private final Map<String, Object> itemAliases = new HashMap<>();
    private final File aliasFile;

    public BlockAliasManager() {
        aliasFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/ResourcePackAliases.json");
        loadAliases();
    }

    /**
     * Resolve a block name to its full block state syntax.
     * If the name is an alias, returns the mapped block state.
     * Otherwise, returns the name as-is (allows both aliases and direct block states).
     * 
     * @param blockName Alias or direct block state (e.g., "oak_leaves_empty" or "OAK_LOG")
     * @return Resolved block state string
     */
    public String resolve(String blockName) {
        return blockAliases.getOrDefault(blockName, blockName);
    }
    
    /**
     * Get the base material for an item alias.
     * If the name is an item alias, returns the mapped material.
     * Otherwise, returns null.
     * 
     * @param itemName Item alias (e.g., "soil_bucket")
     * @return Base material string or null
     */
    public String resolveItemMaterial(String itemName) {
        Object itemData = itemAliases.get(itemName);
        if (itemData == null) {
            return null;
        }
        
        // Handle both string and object formats
        if (itemData instanceof String) {
            return (String) itemData;
        } else if (itemData instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> itemMap = (java.util.Map<String, Object>) itemData;
            return (String) itemMap.get("base");
        }
        
        return null;
    }
    
    /**
     * Get the display name for an item alias.
     * Returns null if the item doesn't have a static name (e.g., soil buckets with dynamic names).
     * 
     * @param itemName Item alias (e.g., "bagged_dirt")
     * @return Display name or null if not specified
     */
    public String getItemDisplayName(String itemName) {
        Object itemData = itemAliases.get(itemName);
        if (itemData == null) {
            return null;
        }
        
        // Only return display name if it's an object with itemName
        if (itemData instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> itemMap = (java.util.Map<String, Object>) itemData;
            return (String) itemMap.get("itemName");
        }
        
        return null; // String format means no static name
    }

    /**
     * Load aliases from ResourcePackAliases.json
     */
    private void loadAliases() {
        if (!aliasFile.exists()) {
            Botanica.INSTANCE.getLogger().warning("ResourcePackAliases.json not found at: " + aliasFile.getAbsolutePath());
            return;
        }

        try (FileReader reader = new FileReader(aliasFile)) {
            AliasConfig config = gson.fromJson(reader, AliasConfig.class);
            if (config != null) {
                if (config.blockAliases != null) {
                    blockAliases.clear();
                    blockAliases.putAll(config.blockAliases);
                    Botanica.INSTANCE.getLogger().info("Loaded " + blockAliases.size() + " block aliases");
                }
                if (config.itemAliases != null) {
                    itemAliases.clear();
                    itemAliases.putAll(config.itemAliases);
                    Botanica.INSTANCE.getLogger().info("Loaded " + itemAliases.size() + " item aliases");
                }
            }
        } catch (IOException e) {
            Botanica.INSTANCE.getLogger().severe("Error loading ResourcePackAliases.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reload aliases from disk (useful for hot-reloading during development).
     */
    public void reload() {
        loadAliases();
    }

    /**
     * Config structure for ResourcePackAliases.json
     */
    private static class AliasConfig {
        @SerializedName("blockAliases")
        Map<String, String> blockAliases;
        
        @SerializedName("itemAliases")
        Map<String, Object> itemAliases;
    }
}

