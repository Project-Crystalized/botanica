package gg.crystalized.botanica.Soil.Generation;

import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.World.BlockPos;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages UI text displays for soil bins.
 * Shows information when players look at soil bins.
 */
public class SoilBinUIManager {
    
    private final SimulationDataManager dataManager;
    private final Map<UUID, TextDisplay> playerUIDisplays = new HashMap<>(); // Player UUID -> Text Display
    private final Map<UUID, BlockPos> playerLookingAt = new HashMap<>(); // Player UUID -> Soil bin position
    
    public SoilBinUIManager(SimulationDataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    /**
     * Start the periodic look detection task.
     * Runs every 10 ticks (0.5 seconds) to check what players are looking at.
     */
    public void startLookDetection(org.bukkit.plugin.Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerLook(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Run every 10 ticks
    }
    
    /**
     * Check what block a player is looking at and show/hide UI accordingly.
     */
    private void checkPlayerLook(Player player) {
        // Raycast to see what block player is looking at
        RayTraceResult result = player.rayTraceBlocks(5.0); // 5 block range
        
        Block lookingAtBlock = null;
        if (result != null && result.getHitBlock() != null) {
            Block block = result.getHitBlock();
            // Check if it's a barrier (potential soil bin)
            if (block.getType() == Material.BARRIER) {
                // Verify it's actually a soil bin by checking if it has data
                int totalVolume = SoilBinData.getTotalVolume(block);
                if (totalVolume >= 0) {
                    lookingAtBlock = block;
                }
            }
        }
        
        BlockPos currentLookPos = playerLookingAt.get(player.getUniqueId());
        BlockPos newLookPos = lookingAtBlock != null ? BlockPos.fromBukkitLocation(lookingAtBlock.getLocation()) : null;
        
        // Check if player's look target changed
        if (currentLookPos == null && newLookPos == null) {
            // Not looking at anything, no change
            return;
        }
        
        if (currentLookPos != null && newLookPos != null && currentLookPos.equals(newLookPos)) {
            // Still looking at same soil bin, no change needed
            return;
        }
        
        // Player's look target changed
        if (newLookPos == null) {
            // Stopped looking at soil bin
            hideUI(player);
        } else {
            // Started looking at soil bin or switched to different one
            showUI(player, lookingAtBlock);
        }
    }
    
    /**
     * Show UI for a soil bin.
     */
    private void showUI(Player player, Block soilBin) {
        // Remove old UI if exists
        hideUI(player);
        
        // Get soil bin data
        int totalVolume = SoilBinData.getTotalVolume(soilBin);
        int dirtCount = SoilBinData.getDirtCount(soilBin);
        double secondaryPercentage = SoilBinData.getSecondaryPercentage(soilBin);
        int remainingSoil = SoilBinData.getRemainingSoil(soilBin);
        String soilType = SoilBinData.getSoilType(soilBin);
        
        // Build UI text
        String uiText = buildUIText(totalVolume, dirtCount, secondaryPercentage, remainingSoil, soilType);
        
        // Spawn text display above soil bin
        Location loc = soilBin.getLocation().add(0.5, 1.2, 0.5); // Center and above block
        TextDisplay textDisplay = soilBin.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.setText(uiText);
            display.setBillboard(Display.Billboard.CENTER); // Always face player
            display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0)); // Semi-transparent black background
            display.setSeeThrough(false);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(200);
            
            // Scale down the text to 70% of normal size for a more compact UI
            org.bukkit.util.Transformation transform = display.getTransformation();
            float scale = 0.7f; // Adjust this value: 1.0 = normal, 0.5 = half size, etc.
            display.setTransformation(new org.bukkit.util.Transformation(
                transform.getTranslation(),
                transform.getLeftRotation(),
                new org.joml.Vector3f(scale, scale, scale), // Scale uniformly
                transform.getRightRotation()
            ));
        });
        
        // Store references
        playerUIDisplays.put(player.getUniqueId(), textDisplay);
        playerLookingAt.put(player.getUniqueId(), BlockPos.fromBukkitLocation(soilBin.getLocation()));
    }
    
    /**
     * Hide UI for a player.
     */
    private void hideUI(Player player) {
        TextDisplay display = playerUIDisplays.remove(player.getUniqueId());
        if (display != null) {
            display.remove();
        }
        playerLookingAt.remove(player.getUniqueId());
    }
    
    /**
     * Update UI text for a specific soil bin.
     * Call this when contents change.
     */
    public void updateUI(Block soilBin) {
        BlockPos pos = BlockPos.fromBukkitLocation(soilBin.getLocation());
        
        // Find all players looking at this soil bin
        for (Map.Entry<UUID, BlockPos> entry : playerLookingAt.entrySet()) {
            if (entry.getValue().equals(pos)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    // Get updated data
                    int totalVolume = SoilBinData.getTotalVolume(soilBin);
                    int dirtCount = SoilBinData.getDirtCount(soilBin);
                    double secondaryPercentage = SoilBinData.getSecondaryPercentage(soilBin);
                    int remainingSoil = SoilBinData.getRemainingSoil(soilBin);
                    String soilType = SoilBinData.getSoilType(soilBin);
                    
                    // Build updated text
                    String uiText = buildUIText(totalVolume, dirtCount, secondaryPercentage, remainingSoil, soilType);
                    
                    // Update display
                    TextDisplay display = playerUIDisplays.get(player.getUniqueId());
                    if (display != null) {
                        display.setText(uiText);
                    }
                }
            }
        }
    }
    
    /**
     * Build the UI text based on soil bin contents.
     */
    private String buildUIText(int totalVolume, int dirtCount, double secondaryPercentage, int remainingSoil, String soilType) {
        StringBuilder text = new StringBuilder();
        
        if (totalVolume == 0) {
            // Empty soil bin
            text.append("§l§fEmpty\n");
            text.append("§7Contents:\n");
            text.append("§8None\n");
            text.append("§7Total soil: §f0");
        } else {
            // Has contents
            // Get soil spec for display name
            String soilDisplayName = "Unknown Soil";
            if (soilType != null && !soilType.isEmpty()) {
                var spec = dataManager.soils().get(soilType);
                if (spec != null) {
                    soilDisplayName = spec.displayName;
                }
            } else {
                soilDisplayName = "Unknown";
            }
            
            // Calculate percentage
            int percentage = (int) Math.round(secondaryPercentage * 100);
            
            // Title line
            text.append("§l§f").append(soilDisplayName);
            if (percentage > 0) {
                text.append(" §7(§f").append(percentage).append("%§7)");
            }
            text.append("\n");
            
            // Contents
            text.append("§7Contents:\n");
            
            // Dirt count
            text.append("§f").append(dirtCount).append("x §7dirt\n");
            
            // Secondary ingredient count
            int secondaryCount = totalVolume - dirtCount;
            if (secondaryCount > 0) {
                // Get altName for secondary ingredient
                String secondaryName = "secondary";
                if (soilType != null && !soilType.isEmpty()) {
                    var spec = dataManager.soils().get(soilType);
                    if (spec != null && spec.altName != null) {
                        secondaryName = spec.altName;
                    }
                }
                text.append("§f").append(secondaryCount).append("x §7").append(secondaryName).append("\n");
            }
            
            // Total soil
            text.append("§7Total soil: §f").append(remainingSoil);
        }
        
        return text.toString();
    }
    
    /**
     * Cleanup - remove all UI displays.
     */
    public void cleanup() {
        for (TextDisplay display : playerUIDisplays.values()) {
            display.remove();
        }
        playerUIDisplays.clear();
        playerLookingAt.clear();
    }
}

