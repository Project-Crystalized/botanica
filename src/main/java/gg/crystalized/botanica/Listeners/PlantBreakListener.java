package gg.crystalized.botanica.Listeners;

import gg.crystalized.botanica.Config.BotanicaSimulationConfig;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.World.BlockAliasManager;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import gg.crystalized.botanica.PlantSim.World.SchematicManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Listens for block break events and removes plant instances when their blocks are broken.
 * Handles both single-block plants and multi-block schematic plants.
 * 
 * Allow List Behavior:
 * 1. If broken block IS on allow list (e.g., leaves): Do nothing, allow vanilla behavior
 * 2. If broken block NOT on allow list (e.g., logs): Break entire schematic, drop only non-allow-list blocks
 * 
 * Drop Behavior:
 * - GENERIC plants: No drops (use /botanica harvest for proper drops)
 * - Schematic plants: Remove entire schematic, drop only structural blocks (not leaves/decorative)
 */
public class PlantBreakListener implements Listener {
    
    private final PlantRepo plantRepo;
    private final SchematicBlockIndex schematicBlockIndex;
    private final SchematicManager schematicManager;
    private final SimulationDataManager dataManager;
    private final BotanicaSimulationConfig config;
    private final BlockAliasManager aliasManager;
    private final Random random = new Random();
    
    public PlantBreakListener(
        PlantRepo plantRepo,
        SchematicBlockIndex schematicBlockIndex,
        SchematicManager schematicManager,
        SimulationDataManager dataManager,
        BotanicaSimulationConfig config,
        BlockAliasManager aliasManager
    ) {
        this.plantRepo = plantRepo;
        this.schematicBlockIndex = schematicBlockIndex;
        this.schematicManager = schematicManager;
        this.dataManager = dataManager;
        this.config = config;
        this.aliasManager = aliasManager;
    }
    
    /**
     * Check if a material (with or without block states) is on the allow list.
     * Supports vanilla materials, block state syntax, and aliases.
     * 
     * @param materialWithStates Full material string (may include block states or be pre-resolved from alias)
     * @return true if this material is safe to break without destroying the whole plant
     */
    private boolean isOnAllowList(String materialWithStates) {
        // Extract base material (remove block states)
        String baseMaterial = materialWithStates;
        int bracketIndex = materialWithStates.indexOf('[');
        if (bracketIndex != -1) {
            baseMaterial = materialWithStates.substring(0, bracketIndex);
        }
        
        // Check if base material is on allow list
        if (config.blockBreakAllowList.contains(baseMaterial)) {
            return true;
        }
        
        // Check if the full string (with states) is on allow list
        if (config.blockBreakAllowList.contains(materialWithStates)) {
            return true;
        }
        
        // Check if any alias in the allow list resolves to this material
        for (String allowedItem : config.blockBreakAllowList) {
            String resolved = aliasManager.resolve(allowedItem);
            if (resolved.equals(materialWithStates) || resolved.equals(baseMaterial)) {
                return true;
            }
        }
        
        return false;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        BlockPos blockPos = BlockPos.fromBukkitLocation(location);
        
        // Try to find the plant at this position
        PlantInstance plant = findPlantAtPosition(blockPos);
        
        if (plant == null) {
            return; // Not a plant block, allow vanilla behavior
        }
        
        // Get plant spec to determine behavior
        PlantSpec spec = dataManager.plants().get(plant.speciesId);
        if (spec == null) {
            return; // Invalid spec, shouldn't happen
        }
        
        // If this is a schematic plant, check if the broken block is on the allow list
        if (plant.currentSchematicId != null) {
            TreeSchematic schematic = schematicManager.getSchematic(plant.currentSchematicId);
            if (schematic != null) {
                // Find which schematic block was broken
                String brokenBlockMaterial = findSchematicBlockMaterial(schematic, plant, blockPos);
                if (brokenBlockMaterial != null && isOnAllowList(brokenBlockMaterial)) {
                    // This block is on the allow list - allow vanilla break, don't remove plant
                    return;
                }
            }
        }
        
        // Block is not on allow list (or single-block plant) - remove entire plant
        boolean isSchematicStage = plant.currentSchematicId != null;
        
        if (isSchematicStage) {
            handleSchematicPlantBreak(plant, spec, player);
        } else {
            handleSingleBlockPlantBreak(plant, spec, player);
        }
        
        // Remove the plant instance from repository
        plantRepo.remove(plant);
        
        // Note: Soil instance is NOT touched - it persists with its water/nutrient state
    }
    
    /**
     * Handles breaking a single-block plant (any growth stage that's not a schematic)
     */
    private void handleSingleBlockPlantBreak(PlantInstance plant, PlantSpec spec, Player player) {
        // For GENERIC plants: no drops (player should use /botanica harvest)
        // For other types: allow vanilla block drop (the event handles it)
        // We just need to remove the plant instance
        
        // No additional drops needed - vanilla behavior will drop the block item if applicable
    }
    
    /**
     * Handles breaking a multi-block schematic plant.
     * Removes entire schematic and drops all non-allow-list blocks.
     * Handles race conditions by also clearing the currently visible schematic.
     */
    private void handleSchematicPlantBreak(PlantInstance plant, PlantSpec spec, Player player) {
        // Load the schematic based on currentSchematicId
        TreeSchematic schematic = schematicManager.getSchematic(plant.currentSchematicId);
        if (schematic == null) {
            return; // Schematic not found, shouldn't happen
        }
        
        // Also determine what schematic SHOULD be visible based on current progress
        // This handles race conditions where the visual updated but currentSchematicId is stale
        String expectedSchematicId = determineCurrentSchematicId(plant, spec);
        TreeSchematic expectedSchematic = null;
        if (expectedSchematicId != null && !expectedSchematicId.equals(plant.currentSchematicId)) {
            expectedSchematic = schematicManager.getSchematic(expectedSchematicId);
        }
        
        // Count blocks by material type (excluding allow list for drops)
        Map<String, Integer> blockCounts = new HashMap<>();
        
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            String materialWithStates = block.material();
            
            // Extract base material name (remove block states if present)
            // e.g., "BROWN_MUSHROOM_BLOCK[north=true,...]" -> "BROWN_MUSHROOM_BLOCK"
            String baseMaterial = materialWithStates;
            int bracketIndex = materialWithStates.indexOf('[');
            if (bracketIndex != -1) {
                baseMaterial = materialWithStates.substring(0, bracketIndex);
            }
            
            // Apply rotation to block coordinates
            int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), plant.rotation);
            
            // Remove ALL blocks from world (including allow list blocks like leaves)
            BlockPos blockPos = new BlockPos(
                plant.pos.world(),
                plant.pos.x() + rotated[0],
                plant.pos.y() + block.y(),
                plant.pos.z() + rotated[1]
            );
            
            Location blockLocation = new Location(
                player.getWorld(),
                blockPos.x(),
                blockPos.y(),
                blockPos.z()
            );
            
            blockLocation.getBlock().setType(Material.AIR);
            
            // Count this block for drops ONLY if NOT on allow list
            if (!isOnAllowList(materialWithStates)) {
                blockCounts.put(baseMaterial, blockCounts.getOrDefault(baseMaterial, 0) + 1);
            }
        }
        
        // Drop items dispersed around plant root with 0.5 block radius
        Location dropBaseLocation = new Location(
            player.getWorld(),
            plant.pos.x() + 0.5, // Center of block
            plant.pos.y(),
            plant.pos.z() + 0.5
        );
        
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String materialName = entry.getKey();
            int count = entry.getValue();
            
            try {
                Material material = Material.valueOf(materialName);
                ItemStack itemStack = new ItemStack(material, count);
                
                // Add random dispersion (0.5 block radius on X/Z)
                double offsetX = (random.nextDouble() - 0.5) * 1.0; // -0.5 to +0.5
                double offsetZ = (random.nextDouble() - 0.5) * 1.0;
                
                Location dropLocation = dropBaseLocation.clone().add(offsetX, 0, offsetZ);
                player.getWorld().dropItemNaturally(dropLocation, itemStack);
                
            } catch (IllegalArgumentException e) {
                // Invalid material name, skip
                player.sendMessage("§cWarning: Invalid material '" + materialName + "' in schematic");
            }
        }
        
        // Unregister schematic from spatial index
        schematicBlockIndex.unregisterSchematic(plant.pos, schematic);
        
        // Clean up expected schematic if it's different (race condition fix)
        if (expectedSchematic != null) {
            for (TreeSchematic.SchematicBlock block : expectedSchematic.blocks()) {
                // Apply rotation
                int[] rotated = SchematicManager.rotateBlock(block.x(), block.z(), plant.rotation);
                
                BlockPos blockPos = new BlockPos(
                    plant.pos.world(),
                    plant.pos.x() + rotated[0],
                    plant.pos.y() + block.y(),
                    plant.pos.z() + rotated[1]
                );
                
                Location blockLocation = new Location(
                    player.getWorld(),
                    blockPos.x(),
                    blockPos.y(),
                    blockPos.z()
                );
                
                // Only clear if it's not already air (avoid unnecessary updates)
                if (blockLocation.getBlock().getType() != Material.AIR) {
                    blockLocation.getBlock().setType(Material.AIR);
                }
            }
            
            // Also unregister this schematic from index
            schematicBlockIndex.unregisterSchematic(plant.pos, expectedSchematic);
        }
    }
    
    /**
     * Determine what schematic should be visible based on plant progress.
     * Used to handle race conditions during stage transitions.
     */
    private String determineCurrentSchematicId(PlantInstance plant, PlantSpec spec) {
        if (spec.mutations == null || spec.mutations.stages == null || spec.mutations.stages.isEmpty()) {
            return null;
        }
        
        // Combine growth and harvestable stages
        java.util.List<String> allStages = new java.util.ArrayList<>(spec.mutations.stages);
        if (spec.mutations.harvestableStages != null && !spec.mutations.harvestableStages.isEmpty()) {
            allStages.addAll(spec.mutations.harvestableStages);
        }
        
        int totalStageCount = allStages.size();
        if (totalStageCount == 0) {
            return null;
        }
        
        // Calculate current stage index (same logic as SimulationService)
        int currentStageIndex;
        if (plant.complete || plant.progress >= 1.0) {
            currentStageIndex = totalStageCount - 1;
        } else {
            double stageProgress = plant.progress * 0.99;
            currentStageIndex = Math.max(0, Math.min(totalStageCount - 2, (int) (stageProgress * (totalStageCount - 1))));
        }
        
        String stageVisual = allStages.get(currentStageIndex);
        
        // Check if it's a schematic
        if (stageVisual.startsWith("schematic:")) {
            return stageVisual.substring("schematic:".length());
        }
        
        return null; // Not a schematic stage
    }
    
    /**
     * Finds a plant at the given position.
     * Checks both direct position (root block) and spatial index (schematic blocks).
     */
    private PlantInstance findPlantAtPosition(BlockPos pos) {
        // 1. Direct check - is this the root block?
        PlantInstance directPlant = plantRepo.get(pos);
        if (directPlant != null) {
            return directPlant;
        }
        
        // 2. Spatial index check - is this part of a schematic?
        BlockPos rootPos = schematicBlockIndex.getRootPosition(pos);
        if (rootPos != null) {
            // Look up the plant by root position
            return plantRepo.get(rootPos);
        }
        
        return null; // No plant found
    }
    
    /**
     * Find the material definition of the schematic block at the given position.
     * Accounts for rotation when matching coordinates.
     * 
     * @param schematic The schematic to search
     * @param plant The plant instance (for root position and rotation)
     * @param brokenPos The position of the broken block
     * @return The material string from the schematic, or null if not found
     */
    private String findSchematicBlockMaterial(TreeSchematic schematic, PlantInstance plant, BlockPos brokenPos) {
        // Calculate relative position from plant root
        int relX = brokenPos.x() - plant.pos.x();
        int relY = brokenPos.y() - plant.pos.y();
        int relZ = brokenPos.z() - plant.pos.z();
        
        // Reverse the rotation to find the original schematic coordinates
        int[] unrotated = reverseRotateBlock(relX, relZ, plant.rotation);
        
        // Find matching block in schematic
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            if (block.x() == unrotated[0] && block.y() == relY && block.z() == unrotated[1]) {
                return block.material();
            }
        }
        
        return null; // Block not found in schematic (shouldn't happen)
    }
    
    /**
     * Reverse rotation to convert world coordinates back to schematic coordinates.
     */
    private int[] reverseRotateBlock(int x, int z, int rotation) {
        // Reverse rotation is the opposite direction
        return switch (rotation) {
            case 0 -> new int[]{x, z};           // No rotation
            case 90 -> new int[]{z, -x};         // Reverse 90° clockwise = 90° counter-clockwise
            case 180 -> new int[]{-x, -z};       // 180° is its own reverse
            case 270 -> new int[]{-z, x};        // Reverse 270° clockwise = 90° clockwise
            default -> new int[]{x, z};          // Fallback
        };
    }
}

