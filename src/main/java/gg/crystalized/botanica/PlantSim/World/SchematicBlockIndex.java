package gg.crystalized.botanica.PlantSim.World;

import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.World.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial index for fast lookup of which plant owns a given schematic block.
 * Provides O(1) lookup instead of O(N×M) by maintaining a reverse mapping.
 * 
 * Thread-safe for concurrent access.
 */
public class SchematicBlockIndex {
    // Maps schematic block position -> plant root position
    private final Map<BlockPos, BlockPos> blockToRoot = new ConcurrentHashMap<>();
    
    /**
     * Register all blocks from a schematic as belonging to a plant.
     * Call this when placing a schematic.
     */
    public void registerSchematic(BlockPos rootPos, TreeSchematic schematic, UUID plantId) {
        if (schematic == null) return;
        
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            BlockPos blockPos = new BlockPos(
                rootPos.world(),
                rootPos.x() + block.x(),
                rootPos.y() + block.y(),
                rootPos.z() + block.z()
            );
            blockToRoot.put(blockPos, rootPos);
        }
    }
    
    /**
     * Unregister all blocks from a schematic.
     * Call this when removing a schematic or when a plant is harvested.
     */
    public void unregisterSchematic(BlockPos rootPos, TreeSchematic schematic) {
        if (schematic == null) return;
        
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            BlockPos blockPos = new BlockPos(
                rootPos.world(),
                rootPos.x() + block.x(),
                rootPos.y() + block.y(),
                rootPos.z() + block.z()
            );
            blockToRoot.remove(blockPos);
        }
    }
    
    /**
     * Find the root position of the plant that owns the block at the given position.
     * Returns the plant's root BlockPos, or null if no plant owns this block.
     * 
     * O(1) lookup time.
     */
    public BlockPos getRootPosition(BlockPos pos) {
        return blockToRoot.get(pos);
    }
    
    /**
     * Remove all mappings for a specific root position.
     * Useful when a plant is removed without knowing its schematic.
     */
    public void unregisterByRootPosition(BlockPos rootPos) {
        blockToRoot.entrySet().removeIf(entry -> entry.getValue().equals(rootPos));
    }
    
    /**
     * Clear all mappings (for testing or reset).
     */
    public void clear() {
        blockToRoot.clear();
    }
    
    /**
     * Get the total number of indexed blocks.
     * Useful for debugging/monitoring.
     */
    public int size() {
        return blockToRoot.size();
    }
}

