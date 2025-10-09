package gg.crystalized.botanica.PlantSim.Domain.Data;

import java.util.List;

/**
 * Defines a multi-block plant structure (tree) with relative block positions.
 * Used for the final growth stage of trees that require multiple blocks.
 */
public record TreeSchematic(
        String id,
        List<SchematicBlock> blocks
) {
    /**
     * A single block in the schematic with relative coordinates.
     * Coordinates are relative to the plant's root position (the sapling location).
     */
    public record SchematicBlock(
            int x,  // relative x offset from root
            int y,  // relative y offset from root
            int z,  // relative z offset from root
            String material  // Minecraft block material name (e.g., "OAK_LOG", "OAK_LEAVES")
    ) {}
}

