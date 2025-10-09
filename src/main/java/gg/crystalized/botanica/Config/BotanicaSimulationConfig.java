package gg.crystalized.botanica.Config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gg.crystalized.botanica.Botanica;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class BotanicaSimulationConfig {
    
    // Simulation timing
    public int periodicSweepSeconds = 2;  // Background sweep interval
    public int maxPlantsPerCycle = 3000;  // Plants per sweep
    public int urgentPlantsPerBurst = 256; // Plants to run immediately on event bursts
    public int batchSize = 256;           // Plants per worker task
    public int mutationCapPerTick = 1000; // Max world changes per tick
    
    // Block break allow list (blocks that don't trigger plant removal)
    public List<String> blockBreakAllowList = List.of(
        // Common leaf blocks that don't trigger plant removal
        "OAK_LEAVES",
        "BIRCH_LEAVES",
        "SPRUCE_LEAVES",
        "JUNGLE_LEAVES",
        "ACACIA_LEAVES",
        "DARK_OAK_LEAVES",
        "MANGROVE_LEAVES",
        "CHERRY_LEAVES",
        "AZALEA_LEAVES",
        "FLOWERING_AZALEA_LEAVES",
        // Custom leaf types (add as needed)
        "APPLE_LEAVES"
    );
    
    // Helper method to convert periodicSweepSeconds to Duration
    public Duration getPeriodicSweepDuration() {
        return Duration.ofSeconds(periodicSweepSeconds);
    }
    
    /**
     * Loads config from file, or creates default if it doesn't exist.
     */
    public static BotanicaSimulationConfig load() {
        File configFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/SimConfig.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        if (configFile.exists()) {
            // Load from file
            try (FileReader reader = new FileReader(configFile)) {
                BotanicaSimulationConfig config = gson.fromJson(reader, BotanicaSimulationConfig.class);
                if (config != null) {
                    Botanica.INSTANCE.getLogger().info("Loaded simulation config from SimConfig.json");
                    return config;
                }
            } catch (IOException e) {
                Botanica.INSTANCE.getLogger().warning("Failed to load SimConfig.json, using defaults: " + e.getMessage());
            }
        } else {
            // Create default config file
            BotanicaSimulationConfig defaultConfig = new BotanicaSimulationConfig();
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(defaultConfig, writer);
                Botanica.INSTANCE.getLogger().info("Created default SimConfig.json at " + configFile.getAbsolutePath());
            } catch (IOException e) {
                Botanica.INSTANCE.getLogger().warning("Failed to create default SimConfig.json: " + e.getMessage());
            }
            return defaultConfig;
        }
        
        // Fallback to defaults
        return new BotanicaSimulationConfig();
    }
}
