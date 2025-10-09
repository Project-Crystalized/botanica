package gg.crystalized.botanica.Interactions;

import gg.crystalized.botanica.Botanica;
import gg.crystalized.botanica.PlantSim.Actions.PlantActions;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import org.bukkit.ChatColor;

import java.util.List;

/**
 * Resolves which action to execute based on interaction context.
 * Contains smart logic to determine the appropriate action from the available options.
 */
public class ActionResolver {
    
    private final PlantActions plantActions;
    private final SoilRepo soilRepo;
    private final PlantRepo plantRepo;
    private final SchematicBlockIndex schematicBlockIndex;
    
    public ActionResolver(PlantActions plantActions, SoilRepo soilRepo, PlantRepo plantRepo, SchematicBlockIndex schematicBlockIndex) {
        this.plantActions = plantActions;
        this.soilRepo = soilRepo;
        this.plantRepo = plantRepo;
        this.schematicBlockIndex = schematicBlockIndex;
    }
    
    /**
     * Execute the appropriate action based on context and available actions.
     */
    public void resolveAndExecute(InteractionContext ctx, ItemActionRegistry registry, String itemId) {
        // Resolve simulation state
        ctx.plant = findPlantAtPosition(ctx.clickedPos);
        
        // If we found a plant, trace to its soil position
        // Otherwise, check if there's soil at the clicked position
        if (ctx.plant != null) {
            ctx.soil = soilRepo.get(ctx.plant.soilPos);
        } else {
            ctx.soil = soilRepo.get(ctx.clickedPos);
        }
        
        // Get all actions this item can perform
        List<String> actions = registry.getActionsForItem(itemId);
        
        // Determine which action to execute based on context
        for (String action : actions) {
            ItemActionRegistry.ActionStats stats = registry.getStatsForAction(itemId, action);
            if (stats == null) continue;
            
            switch (action) {
                case "TILL_SOIL" -> {
                    if (attemptTillSoil(ctx, stats)) return;
                }
                case "HARVEST_PLANT" -> {
                    if (attemptHarvest(ctx, stats)) return;
                }
                case "PLANT" -> {
                    if (attemptPlant(ctx, stats)) return;
                }
                case "ADD_WATER" -> {
                    if (attemptAddWater(ctx, stats)) return;
                }
                case "REMOVE_WATER" -> {
                    if (attemptRemoveWater(ctx, stats)) return;
                }
                case "ADD_NUTRIENTS" -> {
                    if (attemptAddNutrients(ctx, stats)) return;
                }
                case "REMOVE_NUTRIENTS" -> {
                    if (attemptRemoveNutrients(ctx, stats)) return;
                }
            }
        }
        
        // If we got here, no action was valid for this context
        ctx.player.sendMessage(ChatColor.YELLOW + "Can't use that item here.");
    }
    
    private boolean attemptTillSoil(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        // Can only till if there's no soil and no plant
        if (ctx.soil != null || ctx.plant != null) {
            return false; // Try next action
        }
        
        // For now, default to LOAMY soil (can be made configurable later)
        // Calculate qualityVariance as the range of quality values
        double qualityVariance = (stats.qualityMax - stats.qualityMin) / 2.0;
        PlantActions.HoeStats hoeStats = new PlantActions.HoeStats(
            qualityVariance,
            0.0 // lootMultiplier will come from enchantments later
        );
        
        plantActions.hoeSoil(ctx.clickedPos, "LOAMY", hoeStats);
        ctx.player.sendMessage(ChatColor.GREEN + "Tilled soil!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        
        return true;
    }
    
    private boolean attemptHarvest(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        // Can only harvest if there's a plant
        if (ctx.plant == null) {
            return false; // Try next action
        }
        
        // Attempt harvest
        boolean success = plantActions.harvestPlant(ctx.plant.pos, 0); // lootLevel from enchantments later
        if (success) {
            ctx.player.sendMessage(ChatColor.GREEN + "Harvested plant!");
            
            // Process item consumption/giving
            processItemAfterAction(ctx, stats);
        } else {
            ctx.player.sendMessage(ChatColor.YELLOW + "Plant is not ready to harvest yet!");
        }
        return true;
    }
    
    private boolean attemptPlant(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        // Need soil, but no existing plant
        if (ctx.soil == null) {
            ctx.player.sendMessage(ChatColor.RED + "This needs to be planted on prepared soil!");
            return true; // We handled it (with error message)
        }
        
        if (ctx.plant != null) {
            ctx.player.sendMessage(ChatColor.YELLOW + "There's already a plant here!");
            return true;
        }
        
        // Plant the seed (one block above soil)
        BlockPos plantPos = new BlockPos(
            ctx.clickedPos.world(),
            ctx.clickedPos.x(),
            ctx.clickedPos.y() + 1,
            ctx.clickedPos.z()
        );
        
        plantActions.plantSeed(ctx.player.getUniqueId().toString(), stats.plantSpecId, plantPos);
        ctx.player.sendMessage(ChatColor.GREEN + "Planted " + stats.plantSpecId + "!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        return true;
    }
    
    private boolean attemptAddWater(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        if (ctx.soil == null) {
            ctx.player.sendMessage(ChatColor.RED + "No soil here to water!");
            return true;
        }
        
        plantActions.waterSoil(ctx.soil.pos, stats.amount);
        ctx.player.sendMessage(ChatColor.AQUA + "Added " + stats.amount + " water!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        return true;
    }
    
    private boolean attemptRemoveWater(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        if (ctx.soil == null) {
            ctx.player.sendMessage(ChatColor.RED + "No soil here!");
            return true;
        }
        
        plantActions.drainSoil(ctx.soil.pos, stats.amount);
        ctx.player.sendMessage(ChatColor.GRAY + "Removed " + stats.amount + " water!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        return true;
    }
    
    private boolean attemptAddNutrients(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        if (ctx.soil == null) {
            ctx.player.sendMessage(ChatColor.RED + "No soil here to fertilize!");
            return true;
        }
        
        plantActions.bindNutrients(ctx.soil.pos, stats.amount);
        ctx.player.sendMessage(ChatColor.YELLOW + "Added " + stats.amount + " nutrients!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        return true;
    }
    
    private boolean attemptRemoveNutrients(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        if (ctx.soil == null) {
            ctx.player.sendMessage(ChatColor.RED + "No soil here!");
            return true;
        }
        
        // Use negative amount to remove nutrients
        plantActions.bindNutrients(ctx.soil.pos, -stats.amount);
        ctx.player.sendMessage(ChatColor.GRAY + "Removed " + stats.amount + " nutrients!");
        
        // Process item consumption/giving
        processItemAfterAction(ctx, stats);
        return true;
    }
    
    /**
     * Find plant at position (direct or via schematic index).
     * Checks both root position and schematic blocks.
     */
    private PlantInstance findPlantAtPosition(BlockPos pos) {
        // Direct lookup (root position)
        PlantInstance plant = plantRepo.get(pos);
        if (plant != null) {
            return plant;
        }
        
        // Schematic block lookup (for multi-block plants)
        BlockPos rootPos = schematicBlockIndex.getRootPosition(pos);
        if (rootPos != null) {
            return plantRepo.get(rootPos);
        }
        
        return null;
    }
    
    /**
     * Handle item consumption and giving after successful action.
     * 
     * Logic:
     * 1. If consume=true: Remove 1 from held item stack
     * 2. If give is set: Add 1 of that item to player inventory
     * 
     * Examples:
     * - Hoe: consume=false, give=null → Keep tool
     * - Water Bucket: consume=true, give=BUCKET → Remove bucket, add empty bucket
     * - Sponge: consume=true, give=WET_SPONGE → Remove sponge, add wet sponge
     * - Bone Meal: consume=true, give=null → Remove bone meal
     */
    private void processItemAfterAction(InteractionContext ctx, ItemActionRegistry.ActionStats stats) {
        // Step 1: Consume item if needed
        if (stats.consume) {
            int newAmount = ctx.item.getAmount() - 1;
            if (newAmount <= 0) {
                // Stack depleted, clear held item
                ctx.item.setAmount(0);
                ctx.item.setType(org.bukkit.Material.AIR);
            } else {
                ctx.item.setAmount(newAmount);
            }
        }
        
        // Step 2: Give item if specified
        if (stats.give != null && !stats.give.isBlank()) {
            try {
                org.bukkit.Material giveMaterial = org.bukkit.Material.valueOf(stats.give);
                org.bukkit.inventory.ItemStack giveItem = new org.bukkit.inventory.ItemStack(giveMaterial, 1);
                
                // Try to add to inventory, drop if full
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> overflow = 
                    ctx.player.getInventory().addItem(giveItem);
                
                if (!overflow.isEmpty()) {
                    // Inventory full, drop at player location
                    ctx.player.getWorld().dropItemNaturally(ctx.player.getLocation(), giveItem);
                    ctx.player.sendMessage(ChatColor.YELLOW + "Your inventory is full! Item dropped.");
                }
            } catch (IllegalArgumentException e) {
                ctx.player.sendMessage(ChatColor.RED + "Warning: Invalid 'give' material '" + stats.give + "'");
            }
        }
    }
}

