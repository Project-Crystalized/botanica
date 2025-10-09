package gg.crystalized.botanica.Listeners;

import gg.crystalized.botanica.Config.BotanicaSimulationConfig;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.TreeSchematic;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
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
    private final Random random = new Random();
    
    public PlantBreakListener(
        PlantRepo plantRepo,
        SchematicBlockIndex schematicBlockIndex,
        SchematicManager schematicManager,
        SimulationDataManager dataManager,
        BotanicaSimulationConfig config
    ) {
        this.plantRepo = plantRepo;
        this.schematicBlockIndex = schematicBlockIndex;
        this.schematicManager = schematicManager;
        this.dataManager = dataManager;
        this.config = config;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        String brokenBlockType = event.getBlock().getType().name();
        
        // Check if this block is on the allow list (safe to break without triggering removal)
        if (config.blockBreakAllowList.contains(brokenBlockType)) {
            return; // Allow vanilla behavior, don't remove plant
        }
        
        // Convert location to BlockPos
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
        
        // Determine what stage the plant is at
        boolean isSchematicStage = plant.currentSchematicId != null;
        
        // Handle drops and removal based on plant type and stage
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
     */
    private void handleSchematicPlantBreak(PlantInstance plant, PlantSpec spec, Player player) {
        // Load the schematic
        TreeSchematic schematic = schematicManager.getSchematic(plant.currentSchematicId);
        if (schematic == null) {
            return; // Schematic not found, shouldn't happen
        }
        
        // Count blocks by material type (excluding allow list for drops)
        Map<String, Integer> blockCounts = new HashMap<>();
        
        for (TreeSchematic.SchematicBlock block : schematic.blocks()) {
            String material = block.material();
            
            // Remove ALL blocks from world (including allow list blocks like leaves)
            BlockPos blockPos = new BlockPos(
                plant.pos.world(),
                plant.pos.x() + block.x(),
                plant.pos.y() + block.y(),
                plant.pos.z() + block.z()
            );
            
            Location blockLocation = new Location(
                player.getWorld(),
                blockPos.x(),
                blockPos.y(),
                blockPos.z()
            );
            
            blockLocation.getBlock().setType(Material.AIR);
            
            // Count this block for drops ONLY if NOT on allow list
            if (!config.blockBreakAllowList.contains(material)) {
                blockCounts.put(material, blockCounts.getOrDefault(material, 0) + 1);
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
}

