package gg.crystalized.botanica.PlantSim.World;

import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Block Display entities for walkthrough plants.
 * Display entities show custom block visuals with zero collision.
 * 
 * Features:
 * - Zero collision (entities, not blocks)
 * - Custom block visuals via resource pack
 * - Efficient (no packet spam, just entity updates)
 * - Automatic cleanup when plants are removed
 * - Handles multi-block schematics
 * - Tags entities with plant position for raycasting interactions
 */
public class DisplayEntityManager {
    
    // Namespaced keys for persistent data
    private static final NamespacedKey KEY_PLANT_X = new NamespacedKey("botanica", "plant_x");
    private static final NamespacedKey KEY_PLANT_Y = new NamespacedKey("botanica", "plant_y");
    private static final NamespacedKey KEY_PLANT_Z = new NamespacedKey("botanica", "plant_z");
    private static final NamespacedKey KEY_PLANT_WORLD = new NamespacedKey("botanica", "plant_world");
    
    private final BlockAliasManager aliasManager;
    
    // Track display entities: BlockPos -> Entity UUID (single block plants)
    private final Map<BlockPos, UUID> singleBlockEntities = new ConcurrentHashMap<>();
    
    // Track display entities: Plant root BlockPos -> List of entity UUIDs (schematic plants)
    private final Map<BlockPos, List<UUID>> schematicEntities = new ConcurrentHashMap<>();
    
    public DisplayEntityManager(BlockAliasManager aliasManager) {
        this.aliasManager = aliasManager;
    }
    
    /**
     * Create a display entity for a single-block plant.
     * This method MUST be called from the main thread (via MutationApplier).
     * 
     * @param pos Position to show display entity
     * @param materialName Material name (can be alias or block state syntax)
     */
    public void setDisplayBlock(BlockPos pos, String materialName) {
        // Remove old entity if exists
        removeDisplayBlock(pos);
        
        // Resolve alias to actual block state
        String resolved = aliasManager.resolve(materialName);
        BlockData blockData = parseBlockData(resolved);
        if (blockData == null) {
            return;
        }
        
        // Spawn display entity
        org.bukkit.World world = Bukkit.getWorld(pos.world());
        if (world == null) return;
        
        Location location = new Location(world, pos.x(), pos.y(), pos.z());
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(blockData);
            entity.setBrightness(new Display.Brightness(15, 15)); // Full brightness
            entity.setInterpolationDuration(0); // Instant updates, no lerp
            entity.setViewRange(128.0f); // Render distance
            
            // Center the display in the block (default is corner)
            Transformation transform = entity.getTransformation();
            entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), // Translation (centered)
                transform.getLeftRotation(),
                transform.getScale(),
                transform.getRightRotation()
            ));
            
            // Tag entity with plant position for raycasting
            entity.getPersistentDataContainer().set(KEY_PLANT_X, PersistentDataType.INTEGER, pos.x());
            entity.getPersistentDataContainer().set(KEY_PLANT_Y, PersistentDataType.INTEGER, pos.y());
            entity.getPersistentDataContainer().set(KEY_PLANT_Z, PersistentDataType.INTEGER, pos.z());
            entity.getPersistentDataContainer().set(KEY_PLANT_WORLD, PersistentDataType.STRING, pos.world());
        });
        
        singleBlockEntities.put(pos, display.getUniqueId());
    }
    
    /**
     * Create display entities for a multi-block schematic.
     * This method MUST be called from the main thread (via MutationApplier).
     * 
     * @param rootPos Root position of the plant
     * @param schematic Schematic defining the structure
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     */
    public void setDisplaySchematic(BlockPos rootPos, TreeSchematic schematic, int rotation) {
        // Remove old entities if exist
        removeDisplaySchematic(rootPos);
        
        org.bukkit.World world = Bukkit.getWorld(rootPos.world());
        if (world == null) return;
        
        List<UUID> entityIds = new ArrayList<>();
        
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            // Apply rotation
            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), rotation);
            
            Location location = new Location(
                world,
                rootPos.x() + rotated[0],
                rootPos.y() + block.y(),
                rootPos.z() + rotated[1]
            );
            
            BlockData blockData = parseBlockData(block.material());
            if (blockData == null) continue;
            
            // Spawn display entity
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setInterpolationDuration(0);
                entity.setViewRange(128.0f);
                
                // Tag entity with plant root position for raycasting
                entity.getPersistentDataContainer().set(KEY_PLANT_X, PersistentDataType.INTEGER, rootPos.x());
                entity.getPersistentDataContainer().set(KEY_PLANT_Y, PersistentDataType.INTEGER, rootPos.y());
                entity.getPersistentDataContainer().set(KEY_PLANT_Z, PersistentDataType.INTEGER, rootPos.z());
                entity.getPersistentDataContainer().set(KEY_PLANT_WORLD, PersistentDataType.STRING, rootPos.world());
            });
            
            entityIds.add(display.getUniqueId());
        }
        
        schematicEntities.put(rootPos, entityIds);
    }
    
    /**
     * Remove a display entity for a single-block plant.
     * 
     * @param pos Position to clear
     */
    public void removeDisplayBlock(BlockPos pos) {
        UUID entityId = singleBlockEntities.remove(pos);
        if (entityId != null) {
            removeEntity(entityId);
        }
    }
    
    /**
     * Remove all display entities for a schematic.
     * 
     * @param rootPos Root position of the plant
     */
    public void removeDisplaySchematic(BlockPos rootPos) {
        List<UUID> entityIds = schematicEntities.remove(rootPos);
        if (entityIds != null) {
            for (UUID id : entityIds) {
                removeEntity(id);
            }
        }
    }
    
    /**
     * Extract plant position from a display entity's persistent data.
     * Returns null if the entity is not tagged as a plant display.
     * 
     * @param entity The display entity to check
     * @return BlockPos of the plant root, or null if not a plant display
     */
    public static BlockPos getPlantPosition(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof BlockDisplay)) {
            return null;
        }
        
        var pdc = entity.getPersistentDataContainer();
        
        if (!pdc.has(KEY_PLANT_X, PersistentDataType.INTEGER) ||
            !pdc.has(KEY_PLANT_Y, PersistentDataType.INTEGER) ||
            !pdc.has(KEY_PLANT_Z, PersistentDataType.INTEGER) ||
            !pdc.has(KEY_PLANT_WORLD, PersistentDataType.STRING)) {
            return null;
        }
        
        int x = pdc.get(KEY_PLANT_X, PersistentDataType.INTEGER);
        int y = pdc.get(KEY_PLANT_Y, PersistentDataType.INTEGER);
        int z = pdc.get(KEY_PLANT_Z, PersistentDataType.INTEGER);
        String world = pdc.get(KEY_PLANT_WORLD, PersistentDataType.STRING);
        
        return new BlockPos(world, x, y, z);
    }
    
    /**
     * Clear all display entities (cleanup on disable).
     */
    public void removeAll() {
        // Remove all single block entities
        for (UUID id : singleBlockEntities.values()) {
            removeEntity(id);
        }
        singleBlockEntities.clear();
        
        // Remove all schematic entities
        for (List<UUID> ids : schematicEntities.values()) {
            for (UUID id : ids) {
                removeEntity(id);
            }
        }
        schematicEntities.clear();
    }
    
    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------
    
    /**
     * Remove an entity by UUID.
     */
    private void removeEntity(UUID entityId) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            var entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }
    
    /**
     * Parse material string (with optional block states) into BlockData.
     * 
     * @param materialName Material name (can include block states)
     * @return Parsed BlockData, or null if invalid
     */
    private BlockData parseBlockData(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        
        // Extract base material and block states
        String baseMaterial = materialName;
        String blockStates = null;
        
        int bracketIndex = materialName.indexOf('[');
        if (bracketIndex != -1) {
            baseMaterial = materialName.substring(0, bracketIndex);
            blockStates = materialName.substring(bracketIndex + 1, materialName.length() - 1);
        }
        
        // Get Material enum
        Material material = Material.matchMaterial(baseMaterial);
        if (material == null) {
            return null;
        }
        
        // Create BlockData with states if present
        try {
            if (blockStates != null && !blockStates.isEmpty()) {
                return Bukkit.createBlockData(material, "[" + blockStates + "]");
            } else {
                return material.createBlockData();
            }
        } catch (IllegalArgumentException e) {
            // Invalid block state syntax
            return material.createBlockData(); // Fallback to default state
        }
    }
}

