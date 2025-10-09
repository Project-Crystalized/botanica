package gg.crystalized.botanica.Listeners;

import gg.crystalized.botanica.Interactions.ActionResolver;
import gg.crystalized.botanica.Interactions.InteractionContext;
import gg.crystalized.botanica.Interactions.ItemActionRegistry;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for player right-click interactions and routes them through the action system.
 */
public class PlayerInteractListener implements Listener {
    
    private final ItemActionRegistry registry;
    private final ActionResolver resolver;
    
    public PlayerInteractListener(ItemActionRegistry registry, ActionResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block clickedBlock = event.getClickedBlock();
        
        // Validate interaction
        if (item == null || clickedBlock == null) {
            return;
        }
        
        // Check if this item has registered actions
        String itemId = item.getType().name();
        if (!registry.hasActions(itemId)) {
            return; // Not a Botanica item
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
}

