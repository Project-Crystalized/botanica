package gg.crystalized.botanica.PlantSim.Generation;

import gg.crystalized.botanica.PlantSim.Domain.Data.SoilBucketData;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.World.BlockAliasManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Manages soil bin block interactions and state.
 * Uses composter as base block with custom behavior.
 */
public class SoilBinBlock {
    
    private final SoilGenerationService generationService;
    private final SimulationDataManager dataManager;
    private final BlockAliasManager aliasManager;
    
    public SoilBinBlock(SoilGenerationService generationService, SimulationDataManager dataManager, BlockAliasManager aliasManager) {
        this.generationService = generationService;
        this.dataManager = dataManager;
        this.aliasManager = aliasManager;
    }
    
    /**
     * Handle right-click interaction with soil bin.
     * 
     * @param block The soil bin block
     * @param player The player interacting
     * @param item The item the player is holding
     * @return true if interaction was handled, false otherwise
     */
    public boolean handleInteraction(Block block, Player player, ItemStack item) {
        if (block.getType() != Material.COMPOSTER) {
            return false;
        }
        
        // Check if player is holding an empty bucket (retrieval)
        if (item != null && item.getType() == Material.BUCKET) {
            return handleSoilRetrieval(block, player, item);
        }
        
        // Check if player is holding a valid ingredient (addition)
        if (item != null && isValidIngredient(item)) {
            return handleIngredientAddition(block, player, item);
        }
        
        return false;
    }
    
    /**
     * Handle soil retrieval from bin.
     */
    private boolean handleSoilRetrieval(Block block, Player player, ItemStack bucket) {
        // Check if bin has soil
        int remainingSoil = SoilBinData.getRemainingSoil(block);
        if (remainingSoil <= 0) {
            // Check if there are any ingredients at all
            int totalVolume = SoilBinData.getTotalVolume(block);
            
            if (totalVolume > 0) {
                player.sendMessage("§cAdd dirt to create soil from your ingredients!");
            } else {
                player.sendMessage("§cSoil bin is empty!");
            }
            return true;
        }
        
        // Get soil type and concentration
        String soilType = SoilBinData.getSoilType(block);
        double concentration = SoilBinData.getSecondaryPercentage(block);
        
        // Create soil bucket using existing SoilBucketData system
        ItemStack soilBucket = SoilBucketData.createSoilBucket(soilType, concentration, aliasManager);
        if (soilBucket != null) {
            // Give the soil bucket to the player
            player.getInventory().addItem(soilBucket);
        } else {
            player.sendMessage("§cFailed to create soil bucket!");
            return true;
        }
        
        // Lock the bin when first soil is retrieved (if not already locked)
        if (!SoilBinData.isLocked(block)) {
            SoilBinData.setLocked(block, true);
        }
        
        // Decrement remaining soil count
        SoilBinData.setRemainingSoil(block, remainingSoil - 1);
        
        // Update visual level based on remaining soil count
        int newRemainingSoil = remainingSoil - 1;
        if (newRemainingSoil <= 0) {
            // Bin is empty, unlock and clear all data
            SoilBinData.setLocked(block, false);
            SoilBinData.setTotalVolume(block, 0);
            SoilBinData.setDirtCount(block, 0);
            SoilBinData.setDirtPercentage(block, 0.0);
            SoilBinData.setSecondaryPercentage(block, 0.0);
            SoilBinData.setSoilType(block, "");
            updateVisualLevel(block, 0);
        } else {
            // Calculate visual level based on remaining soil vs total soil ratio
            int totalSoil = SoilBinData.getRemainingSoil(block) + newRemainingSoil; // Current remaining + what we're calculating for
            if (totalSoil > 0) {
                double ratio = (double) newRemainingSoil / totalSoil;
                int totalIngredients = SoilBinData.getTotalVolume(block);
                int visualLevel = (int) Math.ceil(ratio * totalIngredients);
                updateVisualLevel(block, Math.max(1, Math.min(7, visualLevel)));
            } else {
                updateVisualLevel(block, 0);
            }
        }
        
        // Consume empty bucket and give back empty bucket
        bucket.setAmount(bucket.getAmount() - 1);
        player.getInventory().addItem(new ItemStack(Material.BUCKET));
        
        player.sendMessage("§aRetrieved soil bucket! (" + newRemainingSoil + " remaining)");
        return true;
    }
    
    /**
     * Handle ingredient addition to bin.
     */
    private boolean handleIngredientAddition(Block block, Player player, ItemStack ingredient) {
        // Determine ingredient type
        String ingredientType = getIngredientType(ingredient);
        if (ingredientType == null) {
            player.sendMessage("§cInvalid ingredient for soil bin!");
            return true;
        }
        
        // Check if bin is locked
        if (SoilBinData.isLocked(block)) {
            player.sendMessage("§eMust be emptied first!");
            return true;
        }
        
        // Get current state
        int currentVolume = SoilBinData.getTotalVolume(block);
        double currentDirtPercentage = SoilBinData.getDirtPercentage(block);
        double currentSecondaryPercentage = SoilBinData.getSecondaryPercentage(block);
        
        // Check if adding this ingredient would exceed capacity
        // For dirt, check if we're at the soil capacity limit (7 soil blocks)
        // For secondary ingredients, check if we're at the ingredient capacity limit (7 ingredients)
        if (ingredientType.equals("dirt")) {
            int currentDirtCount = SoilBinData.getDirtCount(block);
            if (currentDirtCount >= 7) {
                player.sendMessage("§cSoil bin is full! (Max 7 soil blocks)");
                return true;
            }
        } else {
            if (currentVolume >= 7) {
                player.sendMessage("§cSoil bin is full! (Max 7 ingredients)");
                return true;
            }
        }
        
        // Check if this would be the 7th ingredient and we need dirt validation
        if (currentVolume == 6) {
            boolean isAddingDirt = ingredientType.equals("dirt");
            
            if (currentDirtPercentage == 0.0 && !isAddingDirt) {
                player.sendMessage("§cYou need at least 1 bag of dirt to make soil! Add a bag of dirt first.");
                return true;
            }
        }
        
        // Update the mixture
        int newVolume = currentVolume + 1;
        double newDirtPercentage;
        double newSecondaryPercentage;
        
        if (ingredientType.equals("dirt")) {
            // Adding dirt: increment dirt count and recalculate percentages
            int currentDirtCount = SoilBinData.getDirtCount(block);
            int newDirtCount = currentDirtCount + 1;
            newDirtPercentage = (double) newDirtCount / newVolume;
            newSecondaryPercentage = (double) (int) Math.round(currentVolume * currentSecondaryPercentage) / newVolume;
            
            // Update dirt count
            SoilBinData.setDirtCount(block, newDirtCount);
        } else {
            // Adding secondary: recalculate percentages
            int currentSecondaryCount = (int) Math.round(currentVolume * currentSecondaryPercentage);
            newDirtPercentage = (double) (int) Math.round(currentVolume * currentDirtPercentage) / newVolume;
            newSecondaryPercentage = (double) (currentSecondaryCount + 1) / newVolume;
        }
        
        // Update the soil bin data
        SoilBinData.setTotalVolume(block, newVolume);
        SoilBinData.setDirtPercentage(block, newDirtPercentage);
        SoilBinData.setSecondaryPercentage(block, newSecondaryPercentage);
        
        // Update visual level based on total ingredients added
        updateVisualLevel(block, newVolume);
        
        // Only add soil output if dirt was added
        if (ingredientType.equals("dirt")) {
            // Add 1 more soil to the existing remaining soil count
            int currentRemainingSoil = SoilBinData.getRemainingSoil(block);
            SoilBinData.setRemainingSoil(block, currentRemainingSoil + 1);
            
            // Update soil type and concentration based on new mixture
            updateSoilTypeAndConcentration(block);
        } else {
            // For secondary ingredients, just update the soil type and concentration
            // without changing the total soil output quantity
            updateSoilTypeAndConcentration(block);
        }
        
        // Consume the item
        ingredient.setAmount(ingredient.getAmount() - 1);
        
        player.sendMessage("§aAdded ingredient to soil bin! (" + newVolume + "/7)");
        return true;
    }
    
    /**
     * Check if item is a valid ingredient.
     */
    private boolean isValidIngredient(ItemStack item) {
        if (item == null) return false;
        
        // Check for bagged dirt (primary ingredient)
        if (isCustomItem(item, "bagged_dirt")) {
            return true;
        }
        
        // Check for secondary ingredients from soil specs
        for (var soilSpec : dataManager.soils().all()) {
            if (soilSpec.requiredSecondary != null && 
                isCustomItem(item, soilSpec.requiredSecondary)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get ingredient type from item.
     */
    private String getIngredientType(ItemStack item) {
        if (item == null) return null;
        
        // Check for bagged dirt (primary ingredient)
        if (isCustomItem(item, "bagged_dirt")) {
            return "dirt";
        }
        
        // Check for secondary ingredients from soil specs
        for (var soilSpec : dataManager.soils().all()) {
            if (soilSpec.requiredSecondary != null && 
                isCustomItem(item, soilSpec.requiredSecondary)) {
                return "secondary";
            }
        }
        
        return null;
    }
    
    /**
     * Check if an item is a specific custom item using ResourcePackAliases.
     */
    private boolean isCustomItem(ItemStack item, String itemAlias) {
        if (item == null || itemAlias == null) return false;
        
        // Get the base material for this custom item from ResourcePackAliases
        String baseMaterial = aliasManager.resolveItemMaterial(itemAlias);
        if (baseMaterial == null) return false;
        
        // Check if the item's material matches the base material
        if (!item.getType().name().equalsIgnoreCase(baseMaterial)) return false;
        
        // Check if the item has custom model data that matches our alias
        if (!item.hasItemMeta()) return false;
        
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // Check if the item has the custom model data component
        if (!meta.hasCustomModelDataComponent()) return false;
        
        // Get the custom model data strings
        org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        java.util.List<String> modelDataStrings = cmd.getStrings();
        
        // Check if the model data contains our item alias
        return modelDataStrings.contains(itemAlias);
    }
    
    /**
     * Update visual level of composter.
     */
    private void updateVisualLevel(Block block, int level) {
        if (block.getType() != Material.COMPOSTER) {
            return;
        }
        
        // Clamp level to valid range (0-7, never 8 to prevent bonemeal)
        level = Math.max(0, Math.min(7, level));
        
        // Get current block data and update level
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        if (blockData instanceof org.bukkit.block.data.Levelled) {
            org.bukkit.block.data.Levelled levelledData = (org.bukkit.block.data.Levelled) blockData;
            levelledData.setLevel(level);
            block.setBlockData(levelledData);
        }
    }
    
    /**
     * Update soil type and concentration without changing total soil output.
     * Used when secondary ingredients are added, or when dirt is added (to update type/concentration).
     */
    private void updateSoilTypeAndConcentration(Block block) {
        if (block.getType() != Material.COMPOSTER) {
            return;
        }
        
        // Get current state
        int totalVolume = SoilBinData.getTotalVolume(block);
        double dirtPercentage = SoilBinData.getDirtPercentage(block);
        double secondaryPercentage = SoilBinData.getSecondaryPercentage(block);
        
        // Validate state
        if (totalVolume <= 0 || dirtPercentage < 0 || secondaryPercentage < 0) {
            return;
        }
        
        // Only update soil type and concentration, keep the same total soil output
        SoilGenerationService.SoilOutput output = generationService.calculateOutput(totalVolume, dirtPercentage, secondaryPercentage);
        SoilBinData.setSoilType(block, output.soilType);
        // Don't update remainingSoil - keep the same quantity
    }
}