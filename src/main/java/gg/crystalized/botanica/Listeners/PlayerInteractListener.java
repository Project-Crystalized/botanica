package gg.crystalized.botanica.Listeners;

import gg.crystalized.botanica.Interactions.ActionResolver;
import gg.crystalized.botanica.Interactions.InteractionContext;
import gg.crystalized.botanica.Interactions.ItemActionRegistry;
import gg.crystalized.botanica.PlantSim.Actions.PlantActions;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilBucketData;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilBlockData;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.BlockAliasManager;
import gg.crystalized.botanica.PlantSim.Generation.SoilBinBlock;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
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
    private final SoilRepo soilRepo;
    private final PlantActions plantActions;
    private final BlockAliasManager aliasManager;
    private final SoilBinBlock soilBinBlock;
    
    public PlayerInteractListener(ItemActionRegistry registry, ActionResolver resolver, PlantRepo plantRepo, SoilRepo soilRepo, PlantActions plantActions, BlockAliasManager aliasManager, SoilBinBlock soilBinBlock) {
        this.registry = registry;
        this.resolver = resolver;
        this.plantRepo = plantRepo;
        this.soilRepo = soilRepo;
        this.plantActions = plantActions;
        this.aliasManager = aliasManager;
        this.soilBinBlock = soilBinBlock;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Validate basic interaction
        if (item == null) {
            return;
        }
        
        // PRIORITY 0: Handle soil bucket interactions (empty bucket pickup / filled bucket placement)
        if (handleSoilBucketInteraction(event, player, item)) {
            return; // Bucket interaction handled, skip normal action resolution
        }
        
        // PRIORITY 0.5: Handle soil bin interactions (includes bonemeal prevention)
        if (handleSoilBinInteraction(event, player, item)) {
            return; // Soil bin interaction handled, skip normal action resolution
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
        
        // PRIORITY 1.5: Handle hoe tilling interactions (only if holding a hoe AND no walkable plant found)
        // This ensures walkable plants take priority over soil blocks
        if (isHoe(item.getType()) && plantAlongRay == null && handleHoeTilling(event, player, item)) {
            return; // Hoe tilling handled, skip normal action resolution
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
     * Also handles soil bin interactions when placing dirt/sand near composters.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) return;
        
        String itemId = item.getType().name();
        
        // If this item is registered in Botanica's action system, cancel vanilla placement
        if (registry.hasActions(itemId)) {
            event.setCancelled(true);
            return;
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
    
    /**
     * Handle soil bucket interactions (empty bucket pickup / filled bucket placement).
     * @return true if interaction was handled, false to continue with normal action resolution
     */
    private boolean handleSoilBucketInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        // Only handle right-click block interactions
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return false;
        }
        
        Block clickedBlock = event.getClickedBlock();
        
        // Handle empty bucket (pickup soil)
        if (SoilBucketData.isEmptyBucket(item)) {
            return handleEmptyBucketPickup(event, player, item, clickedBlock);
        }
        
        // Handle filled soil bucket (place soil)
        if (SoilBucketData.isSoilBucket(item)) {
            return handleFilledBucketPlacement(event, player, item, clickedBlock);
        }
        
        return false; // Not a bucket interaction
    }
    
    /**
     * Handle empty bucket pickup of existing soil.
     */
    private boolean handleEmptyBucketPickup(PlayerInteractEvent event, Player player, ItemStack emptyBucket, Block clickedBlock) {
        // Check if clicked block is a custom soil block
        if (!SoilBlockData.isCustomSoilBlock(clickedBlock)) {
            return false; // Not a custom soil block, don't handle
        }
        
        // Check if there's a plant on this soil block
        BlockPos soilPos = BlockPos.fromBukkitLocation(clickedBlock.getLocation());
        BlockPos plantPos = new BlockPos(soilPos.world(), soilPos.x(), soilPos.y() + 1, soilPos.z());
        PlantInstance plantOnSoil = plantRepo.get(plantPos);
        
        if (plantOnSoil != null) {
            // There's a plant on this soil - don't allow soil pickup
            player.sendMessage("§cYou cannot remove soil that has a plant growing on it!");
            return true; // Handle the event to prevent default behavior
        }
        
        // Extract soil data from the block
        SoilBucketData soilData = SoilBlockData.extractFromBlock(clickedBlock);
        if (soilData == null) {
            return false; // Couldn't extract soil data
        }
        
        // Create filled bucket with soil data using the convenient wrapper method
        ItemStack filledBucket = SoilBucketData.createSoilBucket(
            soilData.getSoilType(),
            soilData.getSecondaryPercentage(),
            aliasManager
        );
        
        // Replace empty bucket with filled bucket in inventory
        if (emptyBucket.getAmount() > 1) {
            // Stack has multiple buckets, remove one and add filled bucket
            emptyBucket.setAmount(emptyBucket.getAmount() - 1);
            player.getInventory().addItem(filledBucket);
        } else {
            // Single bucket, replace it
            player.getInventory().setItemInMainHand(filledBucket);
        }
        
        // Remove the soil block
        clickedBlock.setType(Material.AIR);
        
        // Remove the soil instance from simulation
        BlockPos pos = BlockPos.fromBukkitLocation(clickedBlock.getLocation());
        soilRepo.delete(pos); // Remove from simulation
        
        // Play pickup sound
        player.playSound(clickedBlock.getLocation(), "item.bucket.fill", 1.0f, 1.0f);
        
        event.setCancelled(true);
        return true;
    }
    
    /**
     * Handle filled soil bucket placement.
     */
    private boolean handleFilledBucketPlacement(PlayerInteractEvent event, Player player, ItemStack filledBucket, Block clickedBlock) {
        // Extract soil data from bucket
        SoilBucketData bucketData = SoilBucketData.fromBucket(filledBucket);
        if (bucketData == null) {
            return false; // Invalid bucket data
        }
        
        // Determine where to place the soil block (like normal block placement)
        Block targetBlock = getPlacementBlock(event, clickedBlock);
        if (targetBlock == null) {
            return false; // Can't place here
        }
        
        // Check if the target location is available for placement
        if (targetBlock.getType() != Material.AIR && !isReplaceableBlock(targetBlock)) {
            return false; // Can't place here
        }
        
        // Get the appropriate block alias for this soil data
        String blockAlias = SoilBlockData.getBlockAliasForSoilData(bucketData, false); // Start with untilled
        if (blockAlias == null) {
            return false; // Couldn't determine block to place
        }
        
        // Resolve the block alias to actual block data
        String resolvedBlock = aliasManager.resolve(blockAlias);
        if (resolvedBlock == null) {
            return false; // Couldn't resolve block alias
        }
        
        // Parse and place the custom block at the target location
        placeCustomBlock(targetBlock, resolvedBlock);
        
        // Store the soil data in the placed block's memory map
        SoilBlockData.storeSoilDataInBlock(targetBlock, bucketData);
        
        // Create SoilInstance for simulation (untilled by default)
        createSoilInstance(targetBlock, bucketData);
        
        // Convert filled bucket back to empty bucket
        if (filledBucket.getAmount() > 1) {
            // Stack has multiple buckets, remove one and add empty bucket
            filledBucket.setAmount(filledBucket.getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.BUCKET));
        } else {
            // Single bucket, replace it with empty bucket
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }
        
        // Play placement sound
        player.playSound(clickedBlock.getLocation(), "item.bucket.empty", 1.0f, 1.0f);
        
        event.setCancelled(true);
        return true;
    }
    
    /**
     * Place a custom block using the resolved block data string.
     */
    private void placeCustomBlock(Block block, String resolvedBlock) {
        // Parse block data similar to DisplayEntityManager
        String baseMaterial = resolvedBlock;
        String blockStates = null;
        
        int bracketIndex = resolvedBlock.indexOf('[');
        if (bracketIndex != -1) {
            baseMaterial = resolvedBlock.substring(0, bracketIndex);
            blockStates = resolvedBlock.substring(bracketIndex + 1, resolvedBlock.length() - 1);
        }
        
        // Get Material enum
        Material material = Material.matchMaterial(baseMaterial);
        if (material == null) {
            material = Material.DIRT; // Fallback
        }
        
        // Create BlockData with states if present
        org.bukkit.block.data.BlockData blockData;
        try {
            if (blockStates != null && !blockStates.isEmpty()) {
                blockData = org.bukkit.Bukkit.createBlockData(material, "[" + blockStates + "]");
            } else {
                blockData = material.createBlockData();
            }
        } catch (IllegalArgumentException e) {
            // Invalid block state syntax, fallback to default state
            blockData = material.createBlockData();
        }
        
        // Set the block with the custom data
        block.setBlockData(blockData);
    }
    
    /**
     * Determine where to place a block based on the interaction.
     * This mimics vanilla block placement logic.
     */
    private Block getPlacementBlock(PlayerInteractEvent event, Block clickedBlock) {
        // If clicking on the top face of a block, place above it
        if (event.getBlockFace() == org.bukkit.block.BlockFace.UP) {
            return clickedBlock.getRelative(org.bukkit.block.BlockFace.UP);
        }
        // If clicking on the side or bottom of a block, place adjacent to it
        else {
            return clickedBlock.getRelative(event.getBlockFace());
        }
    }
    
    /**
     * Check if a block can be replaced when placing soil.
     */
    private boolean isReplaceableBlock(Block block) {
        return block.getType() == Material.AIR || 
               block.getType() == Material.GRASS_BLOCK || 
               block.getType() == Material.TALL_GRASS ||
               block.getType() == Material.FERN ||
               block.getType() == Material.LARGE_FERN;
    }
    
    /**
     * Prevent mushroom block auto-orientation for our custom soil blocks.
     * This cancels BlockPhysicsEvent for mushroom blocks to prevent them from
     * automatically changing their directional states based on neighbors.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        
        // Cancel physics updates for mushroom blocks (our custom soil blocks)
        if (block.getType() == Material.MUSHROOM_STEM || 
            block.getType() == Material.RED_MUSHROOM_BLOCK || 
            block.getType() == Material.BROWN_MUSHROOM_BLOCK) {
            event.setCancelled(true);
        }
        
        // Cancel physics updates for composters to prevent vanilla bonemeal behavior
        if (block.getType() == Material.COMPOSTER) {
            event.setCancelled(true);
        }
    }
    
    
    /**
     * Handle hoe tilling interactions on soil blocks.
     * @return true if interaction was handled, false otherwise
     */
    private boolean handleHoeTilling(PlayerInteractEvent event, Player player, ItemStack hoe) {
        // Only handle right-click block events
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return false;
        }
        
        Block clickedBlock = event.getClickedBlock();
        
        // Check if this is one of our custom soil blocks
        if (!SoilBlockData.isCustomSoilBlock(clickedBlock)) {
            return false; // Not our soil block, let other handlers deal with it
        }
        
        // Get the soil instance
        BlockPos pos = BlockPos.fromBukkitLocation(clickedBlock.getLocation());
        SoilInstance soil = soilRepo.get(pos);
        
        if (soil == null) {
            // No soil instance found, this shouldn't happen for custom soil blocks
            // But don't show error message - just let the interaction pass through
            return false;
        }
        
        // Check if there's a harvestable plant on this soil block
        BlockPos plantPos = new BlockPos(pos.world(), pos.x(), pos.y() + 1, pos.z());
        PlantInstance plantOnSoil = plantRepo.get(plantPos);
        
        boolean harvestedPlant = false;
        if (plantOnSoil != null && isPlantHarvestable(plantOnSoil)) {
            // Harvest the plant first
            harvestedPlant = attemptHarvestPlant(player, plantOnSoil, hoe);
        }
        
        // Check if already tilled (only show message if no plant was harvested)
        if (soil.tilled && !harvestedPlant) {
            player.sendMessage("§eThis soil is already tilled and ready for planting!");
            return true;
        }
        
        // Till the soil (if not already tilled)
        if (!soil.tilled) {
            soil.tilled = true;
            soil.lastUpdateAt = java.time.Instant.now();
            soilRepo.upsert(soil);
            
            // Update the visual block to tilled variant
            updateSoilBlockVisual(clickedBlock, soil, true);
            
            // Play tilling sound and effect
            player.playSound(clickedBlock.getLocation(), "item.hoe.till", 1.0f, 1.0f);
            player.sendMessage("§aSoil tilled! Ready for planting.");
        } else if (harvestedPlant) {
            // Soil was already tilled, but we harvested a plant
            player.sendMessage("§aPlant harvested and soil is ready for planting!");
        }
        
        event.setCancelled(true);
        return true;
    }
    
    /**
     * Check if a plant is ready for harvesting.
     * @return true if plant is harvestable, false otherwise
     */
    private boolean isPlantHarvestable(PlantInstance plant) {
        // Simple check: plant is harvestable if it's complete or has full progress
        return plant.complete || plant.progress >= 1.0;
    }
    
    /**
     * Attempt to harvest a plant using a hoe.
     * @return true if plant was successfully harvested, false otherwise
     */
    private boolean attemptHarvestPlant(Player player, PlantInstance plant, ItemStack hoe) {
        // Check if the plant is actually harvestable
        if (!isPlantHarvestable(plant)) {
            return false;
        }
        
        // Calculate loot level from hoe enchantments (for now, just use 0)
        double lootLevel = 0.0; // TODO: Add enchantment-based loot level calculation
        
        // Use the existing harvest logic from PlantActions
        boolean success = plantActions.harvestPlant(plant.pos, lootLevel);
        
        if (success) {
            player.sendMessage("§aPlant harvested!");
            // Play harvest sound
            player.playSound(plant.pos.toBukkitLocation(), "block.crop.break", 1.0f, 1.0f);
        }
        
        return success;
    }
    
    /**
     * Check if a material is a hoe.
     */
    private boolean isHoe(Material material) {
        return material == Material.WOODEN_HOE ||
               material == Material.STONE_HOE ||
               material == Material.IRON_HOE ||
               material == Material.GOLDEN_HOE ||
               material == Material.DIAMOND_HOE ||
               material == Material.NETHERITE_HOE;
    }
    
    /**
     * Update the visual block to match the soil state (tilled or untilled).
     */
    private void updateSoilBlockVisual(Block block, SoilInstance soil, boolean tilled) {
        // Get the soil data from the block
        SoilBucketData soilData = SoilBlockData.extractFromBlock(block);
        if (soilData == null) {
            return;
        }
        
        // Determine the appropriate block alias for the soil state
        String blockAlias = SoilBlockData.getBlockAliasForSoilData(soilData, tilled);
        if (blockAlias == null) {
            return;
        }
        
        // Resolve and place the block
        String resolvedBlock = aliasManager.resolve(blockAlias);
        if (resolvedBlock != null) {
            placeCustomBlock(block, resolvedBlock);
        }
    }
    
    /**
     * Create a SoilInstance for the placed soil block.
     * This connects our custom soil blocks with the simulation system.
     */
    private void createSoilInstance(Block block, SoilBucketData bucketData) {
        BlockPos pos = BlockPos.fromBukkitLocation(block.getLocation());
        
        // Get existing soil instance or create new one
        SoilInstance soil = soilRepo.get(pos);
        if (soil == null) {
            // Generate random water and nutrient levels between 10-20%
            double randomWater = 10.0 + (Math.random() * 10.0); // 10.0 to 20.0
            double randomNutrients = 10.0 + (Math.random() * 10.0); // 10.0 to 20.0
            
            // Create new soil instance (untilled by default)
            soil = new SoilInstance(
                pos, 
                bucketData.getSoilType(), 
                bucketData.getSecondaryPercentage(), // Use secondary percentage as quality multiplier
                randomWater, // Random water level 10-20%
                randomNutrients, // Random nutrient level 10-20%
                false, // Not tilled yet
                java.time.Instant.now()
            );
        } else {
            // Update existing soil instance
            soil.soilId = bucketData.getSoilType();
            soil.qualityMult = bucketData.getSecondaryPercentage();
            soil.lastUpdateAt = java.time.Instant.now();
        }
        
        // Set next update for evaporation
        soil.nextUpdateAt = java.time.Instant.now().plusSeconds(15); // Safety wake
        
        soilRepo.upsert(soil);
    }
    
    /**
     * Handle soil bin interactions (ingredient addition, soil retrieval, and bonemeal prevention).
     */
    private boolean handleSoilBinInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        // Check if the clicked block is a composter (soil bin)
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.COMPOSTER) {
            return false; // Not a soil bin, don't handle
        }
        
        Block composter = event.getClickedBlock();
        
        // Check if this is a level 8 composter (ready for bonemeal harvest)
        org.bukkit.block.data.BlockData blockData = composter.getBlockData();
        if (blockData instanceof org.bukkit.block.data.Levelled) {
            org.bukkit.block.data.Levelled levelledData = (org.bukkit.block.data.Levelled) blockData;
            if (levelledData.getLevel() == 8) {
                // This is a level 8 composter - prevent bonemeal harvest and handle as soil bin
                event.setCancelled(true);
                
                // If player is holding an empty bucket, handle as soil retrieval
                if (item != null && item.getType() == Material.BUCKET) {
                    return soilBinBlock.handleInteraction(composter, player, item);
                } else {
                    // Player is trying to harvest bonemeal - tell them to use a bucket
                    player.sendMessage("§eUse an empty bucket to retrieve soil from this bin!");
                    return true;
                }
            }
        }
        
        // For all other composter levels, delegate to SoilBinBlock for handling
        return soilBinBlock.handleInteraction(composter, player, item);
    }
    
    
}

