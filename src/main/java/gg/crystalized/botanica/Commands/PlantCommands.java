package gg.crystalized.botanica.Commands;

import gg.crystalized.botanica.PlantSim.Actions.PlantActions;
import gg.crystalized.botanica.PlantSim.Bus.MutationBus;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.Soil.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.Sim.SimulationService;
import gg.crystalized.botanica.PlantSim.UI.PlantStatusUI;
import gg.crystalized.botanica.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import gg.crystalized.botanica.World.BlockAliasManager;
import gg.crystalized.botanica.World.DisplayEntityManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class PlantCommands implements CommandExecutor {
    private final PlantActions actions;
    private final PlantStatusUI ui;
    private final PlantRepo plantRepo;
    private final SoilRepo soilRepo;
    private final SchematicBlockIndex schematicBlockIndex;
    private final SimulationDataManager dataManager;
    private final gg.crystalized.botanica.PlantSim.World.SchematicManager schematicManager;
    private final BlockAliasManager aliasManager;

    public PlantCommands(
        SimulationDataManager data, 
        SoilRepo soilRepo, 
        PlantRepo plantRepo, 
        SimulationService simulationService, 
        MutationBus mutationBus,
        gg.crystalized.botanica.PlantSim.World.SchematicManager schematicManager,
        DisplayEntityManager displayEntityManager,
        BlockAliasManager aliasManager
    ) {
        // Use the shared mutation bus that MutationApplier is watching
        this.actions = new PlantActions(data, soilRepo, plantRepo, mutationBus, schematicManager);
        this.ui = new PlantStatusUI(data, plantRepo, soilRepo);
        this.plantRepo = plantRepo;
        this.soilRepo = soilRepo;
        this.schematicBlockIndex = simulationService.getSchematicBlockIndex();
        this.dataManager = data;
        this.schematicManager = schematicManager;
        this.aliasManager = aliasManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        // Handle reload command separately (doesn't need target block)
        if (args[0].equalsIgnoreCase("reload")) {
            player.sendMessage(ChatColor.YELLOW + "Reloading plant specs, soil specs, and schematics...");
            
            // Clear schematic cache FIRST to prevent stale cache hits during spec reload
            schematicManager.reloadAll();
            
            // Then reload specs asynchronously
            dataManager.reloadAsync().thenRun(() -> {
                // Notify player
                player.sendMessage(ChatColor.GREEN + "✓ Reload complete! All specs and schematics updated.");
            }).exceptionally(ex -> {
                player.sendMessage(ChatColor.RED + "✗ Reload failed: " + ex.getMessage());
                return null;
            });
            return true;
        }

        // Get the block the player is looking at (within 10 blocks)
        Block targetBlock = player.getTargetBlock(null, 10);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "You must be looking at a block!");
            return true;
        }
        
        BlockPos targetPos = BlockPos.fromBukkitLocation(targetBlock.getLocation());

        switch (args[0].toLowerCase()) {
            case "hoe" -> {
                // Hoe the block you're looking at to create soil there
                String soilType = args.length > 1 ? args[1] : "LOAMY";
                actions.hoeSoil(targetPos, soilType, new PlantActions.HoeStats(0.2, 1.0));
                player.sendMessage(ChatColor.GREEN + "Hoed soil at the block you're looking at with " + soilType + " soil type.");
            }
            
            case "water" -> {
                // Water/drain can target soil or plant (finds soil automatically)
                double amount = args.length > 1 ? Double.parseDouble(args[1]) : 20.0;
                BlockPos soilPos = findSoilPosition(targetPos);
                if (soilPos != null) {
                    actions.waterSoil(soilPos, amount);
                    player.sendMessage(ChatColor.BLUE + "Added " + amount + " water to soil.");
                } else {
                    player.sendMessage(ChatColor.RED + "No soil found at this location.");
                }
            }
            
            case "drain" -> {
                double amount = args.length > 1 ? Double.parseDouble(args[1]) : 20.0;
                BlockPos soilPos = findSoilPosition(targetPos);
                if (soilPos != null) {
                    actions.drainSoil(soilPos, amount);
                    player.sendMessage(ChatColor.BLUE + "Drained " + amount + " water from soil.");
                } else {
                    player.sendMessage(ChatColor.RED + "No soil found at this location.");
                }
            }
            
            case "fertilize" -> {
                double amount = args.length > 1 ? Double.parseDouble(args[1]) : 15.0;
                BlockPos soilPos = findSoilPosition(targetPos);
                if (soilPos != null) {
                    actions.fertilizeSoil(soilPos, amount);
                    player.sendMessage(ChatColor.GREEN + "Added " + amount + " nutrients to soil.");
                } else {
                    player.sendMessage(ChatColor.RED + "No soil found at this location.");
                }
            }
            
            case "plant" -> {
                // Plant goes above the clicked block (which should be soil)
                String species = args.length > 1 ? args[1] : "oak_tree";
                actions.plantSeed(player.getUniqueId().toString(), species, targetPos.above());
                player.sendMessage(ChatColor.GREEN + "Planted " + species + " above the block you're looking at.");
            }
            
            case "harvest" -> {
                // Harvest the plant - works on root block or any schematic block
                double lootLevel = args.length > 1 ? Double.parseDouble(args[1]) : 1.0;
                PlantInstance plant = findPlantAtPosition(targetPos);
                
                if (plant == null) {
                    player.sendMessage(ChatColor.RED + "No plant found to harvest at this location.");
                } else {
                    // Always harvest from the root position
                    boolean success = actions.harvestPlant(plant.pos, lootLevel);
                    if (success) {
                        player.sendMessage(ChatColor.GOLD + "Harvested plant!");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Plant is not ready to harvest yet!");
                    }
                }
            }
            
            case "status" -> {
                // Status checks for plant - works on root block or any schematic block
                PlantInstance plant = findPlantAtPosition(targetPos);
                
                if (plant != null) {
                    // Always show status from the root position
                    ui.showPlantStatus(player, plant.pos);
                } else {
                    player.sendMessage(ChatColor.RED + "No plant found at this location.");
                }
            }
            
            case "soil" -> {
                // Soil status - find the soil position
                BlockPos soilPos = findSoilPosition(targetPos);
                if (soilPos != null) {
                    ui.showSoilStatus(player, soilPos);
                } else {
                    player.sendMessage(ChatColor.RED + "No soil found at this location.");
                }
            }
            
            case "give" -> {
                // Give any custom item or block from ResourcePackAliases
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /botanica give <alias> [amount]");
                    player.sendMessage(ChatColor.GRAY + "Example: /botanica give bagged_dirt 5");
                    player.sendMessage(ChatColor.GRAY + "Example: /botanica give soil_bin_0");
                    player.sendMessage(ChatColor.GRAY + "Available: bagged_dirt, bagged_sand, soil_bucket, soil_bucket_sandy, soil_bin_0-7");
                    return true;
                }
                
                String alias = args[1];
                int amount = args.length > 2 ? Integer.parseInt(args[2]) : 1;
                
                // Try to resolve as item first
                String baseMaterial = aliasManager.resolveItemMaterial(alias);
                boolean isItem = baseMaterial != null;
                
                // If not an item, try to resolve as block
                if (!isItem) {
                    String blockState = aliasManager.resolve(alias);
                    if (blockState != null) {
                        // It's a block - create block item
                        try {
                            org.bukkit.block.data.BlockData blockData = org.bukkit.Bukkit.createBlockData(blockState);
                            ItemStack item = new ItemStack(blockData.getMaterial(), amount);
                            
                            // Set the block data on the item
                            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockDataMeta blockDataMeta) {
                                blockDataMeta.setBlockData(blockData);
                                item.setItemMeta(blockDataMeta);
                            }
                            
                            player.getInventory().addItem(item);
                            
                            // Get display name for success message
                            String displayName = alias.replace("_", " ").toLowerCase();
                            player.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + displayName);
                            return true;
                        } catch (Exception e) {
                            player.sendMessage(ChatColor.RED + "Invalid block state: " + blockState);
                            return true;
                        }
                    }
                }
                
                // Handle as item
                if (baseMaterial == null) {
                    player.sendMessage(ChatColor.RED + "Unknown alias: " + alias);
                    player.sendMessage(ChatColor.GRAY + "Available: bagged_dirt, bagged_sand, soil_bucket, soil_bucket_sandy, soil_bin_0-7");
                    return true;
                }
                
                // Create the item
                Material material = Material.matchMaterial(baseMaterial);
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "Invalid material: " + baseMaterial);
                    return true;
                }
                
                ItemStack item = new ItemStack(material, amount);
                
                // Set custom model data and display name
                ItemMeta meta = item.getItemMeta();
                
                // Get display name from ResourcePackAliases (if available)
                String displayName = aliasManager.getItemDisplayName(alias);
                if (displayName != null) {
                    meta.setDisplayName(ChatColor.WHITE + displayName);
                }
                
                // Set custom model data using CustomModelDataComponent
                org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
                cmd.setStrings(java.util.List.of(alias));
                
                // Clear any old numeric custom model data
                // meta.setCustomModelData(null);
                
                // Set the component back
                meta.setCustomModelDataComponent(cmd);
                
                item.setItemMeta(meta);
                
                player.getInventory().addItem(item);
                
                // Get display name for success message
                String successDisplayName = aliasManager.getItemDisplayName(alias);
                if (successDisplayName == null) {
                    successDisplayName = alias.replace("_", " ").toLowerCase();
                }
                player.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + successDisplayName);
            }
            
            default -> {
                showHelp(player);
            }
        }

        return true;
    }

    /**
     * Smart soil position finder:
     * 1. If there's a plant at targetPos, return its soil position
     * 2. If there's soil at targetPos, return targetPos
     * 3. If there's soil below targetPos, return below
     * 4. Otherwise, return null
     */
    private BlockPos findSoilPosition(BlockPos targetPos) {
        // 1. Check if there's a plant at the target position
        var plant = plantRepo.get(targetPos);
        if (plant != null) {
            return plant.soilPos; // Get plant's soil
        }
        
        // 2. Check if there's soil at the target position
        if (soilRepo.get(targetPos) != null) {
            return targetPos; // Use soil directly
        }
        
        // 3. Check if there's soil below the target position
        if (soilRepo.get(targetPos.below()) != null) {
            return targetPos.below(); // Use soil below
        }
        
        return null; // No soil found
    }

    /**
     * Find a plant at the given position.
     * Works for both single-block plants and multi-block schematic plants.
     * Uses spatial index for O(1) lookup instead of O(N×M).
     * 
     * 1. Check if there's a plant directly at the position (root block)
     * 2. Check spatial index for schematic blocks
     */
    private PlantInstance findPlantAtPosition(BlockPos targetPos) {
        // 1. Direct check - is this the root block? O(1)
        PlantInstance directPlant = plantRepo.get(targetPos);
        if (directPlant != null) {
            return directPlant;
        }
        
        // 2. Spatial index check - is this part of a schematic? O(1)
        BlockPos rootPos = schematicBlockIndex.getRootPosition(targetPos);
        if (rootPos != null) {
            // Look up the plant by root position
            return plantRepo.get(rootPos);
        }
        
        return null; // No plant found
    }

    /**
     * Create a soil block item with NBT data storing the soil composition
     */
    private ItemStack createSoilBlockItem(String blockAlias, String soilType, double secondaryPercentage) {
        // Get the actual block material from the alias
        String resolvedBlock = aliasManager.resolve(blockAlias);
        
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
        
        ItemStack item = new ItemStack(blockData.getMaterial());
        
        // Set the block data on the item
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockDataMeta blockDataMeta) {
            blockDataMeta.setBlockData(blockData);
            item.setItemMeta(blockDataMeta);
        }
        
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set generic display name
            String displayName = soilType + " Soil";
            meta.setDisplayName(ChatColor.GREEN + displayName);
            
            // Add lore with detailed composition
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(ChatColor.GRAY + "Composition:");
            // Get proper material name for lore
            String materialName = switch (soilType) {
                case "PLAIN" -> "Dirt";
                case "SANDY" -> "Sand";
                case "CLAY" -> "Clay";
                case "LOAMY" -> "Loam";
                default -> soilType;
            };
            lore.add(ChatColor.YELLOW + "• " + String.format("%.1f", secondaryPercentage * 100) + "% " + materialName);
            lore.add(ChatColor.YELLOW + "• " + String.format("%.1f", (1.0 - secondaryPercentage) * 100) + "% Dirt");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Place on ground to create soil");
            meta.setLore(lore);
            
            // Store soil data in NBT
            NamespacedKey soilTypeKey = new NamespacedKey(gg.crystalized.botanica.Botanica.INSTANCE, "soil_type");
            NamespacedKey secondaryContentKey = new NamespacedKey(gg.crystalized.botanica.Botanica.INSTANCE, "secondary_content");
            NamespacedKey blockAliasKey = new NamespacedKey(gg.crystalized.botanica.Botanica.INSTANCE, "block_alias");
            
            meta.getPersistentDataContainer().set(soilTypeKey, PersistentDataType.STRING, soilType);
            meta.getPersistentDataContainer().set(secondaryContentKey, PersistentDataType.DOUBLE, secondaryPercentage);
            meta.getPersistentDataContainer().set(blockAliasKey, PersistentDataType.STRING, blockAlias);
            
            item.setItemMeta(meta);
        }
        
        return item;
    }


    private void showHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Botanica Commands ===");
        player.sendMessage(ChatColor.GRAY + "All commands target the block you're looking at!");
        player.sendMessage(ChatColor.WHITE + "/botanica hoe [type] - Hoe soil (types: SANDY, LOAMY, CLAY)");
        player.sendMessage(ChatColor.WHITE + "/botanica water [amount] - Add water to soil/plant");
        player.sendMessage(ChatColor.WHITE + "/botanica drain [amount] - Drain water from soil/plant");
        player.sendMessage(ChatColor.WHITE + "/botanica fertilize [amount] - Add nutrients to soil/plant");
        player.sendMessage(ChatColor.WHITE + "/botanica plant [species] - Plant a seed above soil");
        player.sendMessage(ChatColor.WHITE + "/botanica harvest [loot_level] - Harvest mature plant");
        player.sendMessage(ChatColor.WHITE + "/botanica status - Show plant status (look at plant)");
        player.sendMessage(ChatColor.WHITE + "/botanica soil - Show soil status (look at soil or plant)");
        player.sendMessage(ChatColor.WHITE + "/botanica give <item_alias> [amount] - Give custom items");
        player.sendMessage(ChatColor.WHITE + "/botanica reload - Hot-reload all specs and schematics");
    }
}
