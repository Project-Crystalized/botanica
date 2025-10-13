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
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

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
        // STEP 1: Check for walkthrough plants along ray (they're AIR on server)
        PlantInstance walkthroughPlant = findWalkthroughPlantAlongRay(player, 5.0);
        double walkthroughDistance = Double.MAX_VALUE;
        
        if (walkthroughPlant != null) {
            // Calculate distance to walkthrough plant
            Location plantLoc = new Location(
                player.getWorld(),
                walkthroughPlant.pos.x() + 0.5,
                walkthroughPlant.pos.y() + 0.5,
                walkthroughPlant.pos.z() + 0.5
            );
            walkthroughDistance = player.getEyeLocation().distance(plantLoc);
        }
        
        // STEP 2: Get solid block target
        Block targetBlock = player.getTargetBlockExact(5);
        double solidBlockDistance = Double.MAX_VALUE;
        
        if (targetBlock != null && !targetBlock.getType().isAir()) {
            // Calculate distance to solid block
            Location blockLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
            solidBlockDistance = player.getEyeLocation().distance(blockLoc);
        }
        
        // STEP 3: Show whichever is CLOSER (walkthrough plant or solid block)
        if (walkthroughDistance < solidBlockDistance) {
            // Walkthrough plant is closer - show it!
            SoilInstance soil = soilRepo.get(walkthroughPlant.soilPos);
            if (soil != null) {
                PlantSpec spec = dataManager.plants().get(walkthroughPlant.speciesId);
                if (spec != null) {
                    Component message = actionBarUI.formatPlant(walkthroughPlant, spec, soil);
                    player.sendActionBar(message);
                    lastDisplayedPos.put(player.getUniqueId(), walkthroughPlant.pos);
                    return;
                }
            }
        } else if (solidBlockDistance < Double.MAX_VALUE) {
            // Solid block is closer (or walkthrough plant didn't have valid data)
            BlockPos pos = BlockPos.fromBukkitLocation(targetBlock.getLocation());
            
            // Try to find plant at this position (including schematic blocks)
            PlantInstance plant = findPlantAtPosition(pos);
            
            if (plant != null) {
                // Found a real plant!
                SoilInstance soil = soilRepo.get(plant.soilPos);
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
            
            // Check if it's soil
            SoilInstance soil = soilRepo.get(pos);
            if (soil != null) {
                SoilSpec soilSpec = dataManager.soils().get(soil.soilId);
                if (soilSpec != null) {
                    Component message = actionBarUI.formatSoil(soil, soilSpec);
                    player.sendActionBar(message);
                    lastDisplayedPos.put(player.getUniqueId(), pos);
                    return;
                }
            }
        }
        
        // Not looking at anything, clear action bar
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
        Location eyeLocation = player.getEyeLocation();
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
                PlantSpec spec = dataManager.plants().get(plant.speciesId);
                if (spec != null && spec.allowWalkthrough) {
                    // Found a walkthrough plant along the ray!
                    return plant;
                }
            }
        }
        
        return null;
    }
}

