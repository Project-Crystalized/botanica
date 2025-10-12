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
 * Manages friendly aliases for block states.
 * Allows schematics to use short names (e.g., "oak_leaves_full_0_0") 
 * instead of verbose block state syntax (e.g., "BROWN_MUSHROOM_BLOCK[north=true,...]").
 */
public class BlockAliasManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> aliases = new HashMap<>();
    private final File aliasFile;

    public BlockAliasManager() {
        aliasFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/BlockAliases.json");
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
        return aliases.getOrDefault(blockName, blockName);
    }

    /**
     * Load aliases from BlockAliases.json
     */
    private void loadAliases() {
        if (!aliasFile.exists()) {
            Botanica.INSTANCE.getLogger().warning("BlockAliases.json not found at: " + aliasFile.getAbsolutePath());
            return;
        }

        try (FileReader reader = new FileReader(aliasFile)) {
            AliasConfig config = gson.fromJson(reader, AliasConfig.class);
            if (config != null && config.aliases != null) {
                aliases.clear();
                aliases.putAll(config.aliases);
                Botanica.INSTANCE.getLogger().info("Loaded " + aliases.size() + " block aliases");
            }
        } catch (IOException e) {
            Botanica.INSTANCE.getLogger().severe("Error loading BlockAliases.json: " + e.getMessage());
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
     * Config structure for BlockAliases.json
     */
    private static class AliasConfig {
        @SerializedName("aliases")
        Map<String, String> aliases;
    }
}

