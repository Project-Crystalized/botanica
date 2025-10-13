package gg.crystalized.botanica.Listeners;

import gg.crystalized.botanica.Interactions.ActionResolver;
import gg.crystalized.botanica.Interactions.InteractionContext;
import gg.crystalized.botanica.Interactions.ItemActionRegistry;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens for player right-click interactions and routes them through the action system.
 */
public class PlayerInteractListener implements Listener {
    
    private final ItemActionRegistry registry;
    private final ActionResolver resolver;
    private final PlantRepo plantRepo;
    
    public PlayerInteractListener(ItemActionRegistry registry, ActionResolver resolver, PlantRepo plantRepo) {
        this.registry = registry;
        this.resolver = resolver;
        this.plantRepo = plantRepo;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Validate basic interaction
        if (item == null) {
            return;
        }
        
        // Check if this item has registered actions
        String itemId = item.getType().name();
        if (!registry.hasActions(itemId)) {
            return; // Not a Botanica item
        }
        
        // Check if this is a PLANT action (planting seeds)
        List<String> actions = registry.getActionsForItem(itemId);
        boolean isPlantingItem = actions.contains("PLANT");
        
        PlantInstance plantAlongRay = null;
        
        // PRIORITY 1: Raycast through blocks to find walkthrough plants
        // BUT skip this if we're planting seeds (we want to target soil, not plants)
        if (!isPlantingItem) {
            // Check every block position along the ray from player's eye to target
            plantAlongRay = findWalkthroughPlantAlongRay(player, 5.0);
        }
        
        if (plantAlongRay != null) {
            // Found a walkthrough plant in line of sight!
            Block plantBlock = player.getWorld().getBlockAt(
                plantAlongRay.pos.x(),
                plantAlongRay.pos.y(),
                plantAlongRay.pos.z()
            );
            
            InteractionContext context = new InteractionContext(
                player,
                item,
                plantBlock,
                plantAlongRay.pos,
                plantBlock.getLocation()
            );
            
            resolver.resolveAndExecute(context, registry, itemId);
            event.setCancelled(true);
            return;
        }
        
        // PRIORITY 2: Fall back to block-based interaction (real plants)
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        // Build interaction context
        BlockPos clickedPos = BlockPos.fromBukkitLocation(clickedBlock.getLocation());
        InteractionContext context = new InteractionContext(
            player,
            item,
            clickedBlock,
            clickedPos,
            clickedBlock.getLocation()
        );
        
        // Resolve and execute appropriate action
        resolver.resolveAndExecute(context, registry, itemId);
        
        // Cancel the event to prevent default behavior (e.g., placing blocks)
        event.setCancelled(true);
    }
    
    /**
     * Prevent vanilla block placement for Botanica-registered items.
     * This catches seed planting and other item types that trigger BlockPlaceEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) return;
        
        String itemId = item.getType().name();
        
        // If this item is registered in Botanica's action system, cancel vanilla placement
        if (registry.hasActions(itemId)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Find a walkthrough plant along the player's line of sight.
     * Traces from player's eye to the target block, checking each block position for walkthrough plants.
     * Returns the FIRST (closest) walkthrough plant found along the ray.
     * 
     * @param player The player looking
     * @param maxDistance Maximum distance to check (blocks)
     * @return First walkthrough plant along ray, or null if none found
     */
    private PlantInstance findWalkthroughPlantAlongRay(Player player, double maxDistance) {
        // Get player's eye location and direction
        org.bukkit.Location eyeLocation = player.getEyeLocation();
        org.bukkit.util.Vector direction = eyeLocation.getDirection();
        
        String world = player.getWorld().getName();
        
        // Trace along the ray in small steps (0.1 block increments for accuracy)
        double step = 0.1;
        int maxSteps = (int) (maxDistance / step);
        
        for (int i = 0; i < maxSteps; i++) {
            double distance = i * step;
            
            // Calculate current position along ray
            org.bukkit.util.Vector currentPos = eyeLocation.toVector().add(direction.clone().multiply(distance));
            
            int x = currentPos.getBlockX();
            int y = currentPos.getBlockY();
            int z = currentPos.getBlockZ();
            
            // Check for walkthrough plant at this block position
            BlockPos checkPos = new BlockPos(world, x, y, z);
            PlantInstance plant = plantRepo.get(checkPos);
            
            if (plant != null) {
                // Check if this plant is walkthrough
                var spec = gg.crystalized.botanica.Botanica.sdm.plants().get(plant.speciesId);
                if (spec != null && spec.allowWalkthrough) {
                    // Found a walkthrough plant along the ray!
                    return plant;
                }
            }
        }
        
        return null;
    }
}

