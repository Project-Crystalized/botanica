package gg.crystalized.botanica.PlantSim.World;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.crystalized.botanica.Botanica;
import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loading and caching of tree schematics from JSON files.
 * Schematics are loaded on-demand and cached for performance.
 */
public class SchematicManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, TreeSchematic> loadedSchematics = new ConcurrentHashMap<>();
    private final File schematicDir;

    public SchematicManager() {
        // Ensure schematics directory exists
        schematicDir = new File(Botanica.INSTANCE.getDataFolder(), "sim/schematics");
        if (!schematicDir.exists()) {
            schematicDir.mkdirs();
            Botanica.INSTANCE.getLogger().info("Created schematics directory: " + schematicDir.getAbsolutePath());
        }
    }

    /**
     * Get a schematic by ID, loading it from file if not already cached.
     * @param id The schematic ID (without .json extension)
     * @return The loaded schematic, or null if not found or invalid
     */
    public TreeSchematic getSchematic(String id) {
        return loadedSchematics.computeIfAbsent(id, this::loadSchematicFromFile);
    }

    /**
     * Load a schematic from a JSON file.
     */
    private TreeSchematic loadSchematicFromFile(String id) {
        File schematicFile = new File(schematicDir, id + ".json");
        if (!schematicFile.exists()) {
            Botanica.INSTANCE.getLogger().warning("Schematic file not found: " + schematicFile.getAbsolutePath());
            return null;
        }

        try (FileReader reader = new FileReader(schematicFile)) {
            TreeSchematic schematic = gson.fromJson(reader, TreeSchematic.class);
            if (schematic != null && schematic.id().equals(id)) {
                // Validate that no blocks have negative Y values
                for (var block : schematic.blocks()) {
                    if (block.y() < 0) {
                        Botanica.INSTANCE.getLogger().severe(
                            "INVALID SCHEMATIC: " + id + " contains block at Y=" + block.y() + 
                            ". Negative Y values are not allowed (plants only grow upwards). Skipping this schematic."
                        );
                        return null;
                    }
                }
                Botanica.INSTANCE.getLogger().info("Loaded schematic: " + id + " with " + schematic.blocks().size() + " blocks");
                return schematic;
            } else {
                Botanica.INSTANCE.getLogger().warning("Invalid schematic file or ID mismatch: " + schematicFile.getAbsolutePath());
                return null;
            }
        } catch (IOException e) {
            Botanica.INSTANCE.getLogger().severe("Error loading schematic " + id + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Reload a specific schematic from file (useful for hot-reloading during development).
     */
    public void reloadSchematic(String id) {
        loadedSchematics.remove(id);
        getSchematic(id);
    }

    /**
     * Clear all cached schematics and reload from disk.
     */
    public void reloadAll() {
        loadedSchematics.clear();
        Botanica.INSTANCE.getLogger().info("Cleared schematic cache");
    }
    
    /**
     * Rotate a block position around the Y-axis by the specified degrees.
     * Used for adding variety to plant placement.
     * 
     * @param x Original X coordinate (relative to root)
     * @param z Original Z coordinate (relative to root)
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @return Array [newX, newZ] after rotation
     */
    public static int[] rotateBlock(int x, int z, int rotation) {
        return switch (rotation) {
            case 0 -> new int[]{x, z};           // North (no rotation)
            case 90 -> new int[]{-z, x};          // East (90° clockwise)
            case 180 -> new int[]{-x, -z};        // South (180°)
            case 270 -> new int[]{z, -x};         // West (270° clockwise / 90° counter-clockwise)
            default -> new int[]{x, z};           // Fallback to no rotation
        };
    }
}

