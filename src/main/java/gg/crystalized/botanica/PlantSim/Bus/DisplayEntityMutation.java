package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.PlantSim.World.BlockPos;

/**
 * Mutation to create or remove a Block Display entity.
 * Display entities provide client-side block visuals with zero collision (walkthrough plants).
 * 
 * Operations:
 * - SET_BLOCK: Spawn a single block display entity
 * - SET_SCHEMATIC: Spawn multiple block display entities from a schematic
 * - REMOVE_BLOCK: Remove a single block display entity
 * - REMOVE_SCHEMATIC: Remove all display entities for a schematic
 */
public record DisplayEntityMutation(
    Operation operation,
    BlockPos pos,
    String materialOrSchematicId,
    TreeSchematic schematic,
    int rotation
) implements Mutation {
    
    public enum Operation {
        SET_BLOCK,          // Spawn single block display entity
        SET_SCHEMATIC,      // Spawn schematic display entities
        REMOVE_BLOCK,       // Remove single block display entity
        REMOVE_SCHEMATIC    // Remove schematic display entities
    }
    
    // Factory methods for cleaner construction
    
    public static DisplayEntityMutation setBlock(BlockPos pos, String material) {
        return new DisplayEntityMutation(Operation.SET_BLOCK, pos, material, null, 0);
    }
    
    public static DisplayEntityMutation setSchematic(BlockPos rootPos, String schematicId, TreeSchematic schematic, int rotation) {
        return new DisplayEntityMutation(Operation.SET_SCHEMATIC, rootPos, schematicId, schematic, rotation);
    }
    
    public static DisplayEntityMutation removeBlock(BlockPos pos) {
        return new DisplayEntityMutation(Operation.REMOVE_BLOCK, pos, null, null, 0);
    }
    
    public static DisplayEntityMutation removeSchematic(BlockPos rootPos) {
        return new DisplayEntityMutation(Operation.REMOVE_SCHEMATIC, rootPos, null, null, 0);
    }
}

