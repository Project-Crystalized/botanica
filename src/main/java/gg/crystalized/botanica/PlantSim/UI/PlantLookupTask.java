package gg.crystalized.botanica.PlantSim.UI;

import gg.crystalized.botanica.Botanica;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.Domain.SoilInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Repeating task that checks what block players are looking at
 * and displays plant/soil info in their action bar.
 * 
 * Runs every 10 ticks (0.5 seconds) for smooth updates without spam.
 */
public class PlantLookupTask extends BukkitRunnable {
    
    private final SoilRepo soilRepo;
    private final PlantRepo plantRepo;
    private final SchematicBlockIndex schematicBlockIndex;
    private final SimulationDataManager dataManager;
    private final ActionBarUI actionBarUI;
    
    // Track last displayed position per player to avoid redundant updates
    private final Map<UUID, BlockPos> lastDisplayedPos = new HashMap<>();
    
    public PlantLookupTask(
        SoilRepo soilRepo,
        PlantRepo plantRepo,
        SchematicBlockIndex schematicBlockIndex,
        SimulationDataManager dataManager,
        PlantStatusUI statusUI
    ) {
        this.soilRepo = soilRepo;
        this.plantRepo = plantRepo;
        this.schematicBlockIndex = schematicBlockIndex;
        this.dataManager = dataManager;
        this.actionBarUI = new ActionBarUI(statusUI);
    }
    
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerActionBar(player);
        }
    }
    
    /**
     * Update a single player's action bar based on what they're looking at.
     */
    private void updatePlayerActionBar(Player player) {
        // Get the block the player is looking at (max 5 blocks away)
        Block targetBlock = player.getTargetBlockExact(5);
        
        if (targetBlock == null) {
            // Not looking at anything, clear action bar if we were displaying something
            if (lastDisplayedPos.containsKey(player.getUniqueId())) {
                player.sendActionBar(Component.empty());
                lastDisplayedPos.remove(player.getUniqueId());
            }
            return;
        }
        
        BlockPos pos = BlockPos.fromBukkitLocation(targetBlock.getLocation());
        
        // Check if we're still looking at the same position (optimization)
        BlockPos lastPos = lastDisplayedPos.get(player.getUniqueId());
        if (pos.equals(lastPos)) {
            // Still looking at same block, but update display for changing values
            // (fall through to display logic)
        }
        
        // Try to find plant at this position (including schematic blocks)
        PlantInstance plant = findPlantAtPosition(pos);
        SoilInstance soil = null;
        
        if (plant != null) {
            // Found a plant, get its soil
            soil = soilRepo.get(plant.soilPos);
            if (soil != null) {
                PlantSpec spec = dataManager.plants().get(plant.speciesId);
                if (spec != null) {
                    Component message = actionBarUI.formatPlant(plant, spec, soil);
                    player.sendActionBar(message);
                    lastDisplayedPos.put(player.getUniqueId(), pos);
                    return;
                }
            }
        }
        
        // No plant, check for soil only
        soil = soilRepo.get(pos);
        if (soil != null) {
            SoilSpec soilSpec = dataManager.soils().get(soil.soilId);
            if (soilSpec != null) {
                Component message = actionBarUI.formatSoil(soil, soilSpec);
                player.sendActionBar(message);
                lastDisplayedPos.put(player.getUniqueId(), pos);
                return;
            }
        }
        
        // Not looking at plant or soil, clear action bar
        if (lastDisplayedPos.containsKey(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
            lastDisplayedPos.remove(player.getUniqueId());
        }
    }
    
    /**
     * Find plant at position (direct or via schematic index).
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
}

