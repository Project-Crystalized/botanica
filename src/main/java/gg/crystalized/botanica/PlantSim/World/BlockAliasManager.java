package gg.crystalized.botanica.PlantSim.World;

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
    private final Map<String, String> itemAliases = new HashMap<>();
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
        return itemAliases.get(itemName);
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
        Map<String, String> itemAliases;
    }
}

