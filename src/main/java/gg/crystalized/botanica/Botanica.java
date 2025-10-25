package gg.crystalized.botanica;

import gg.crystalized.botanica.Commands.PlantCommands;
import gg.crystalized.botanica.Config.BotanicaSimulationConfig;
import gg.crystalized.botanica.Interactions.ActionResolver;
import gg.crystalized.botanica.Interactions.ItemActionRegistry;
import gg.crystalized.botanica.Listeners.PlantBreakListener;
import gg.crystalized.botanica.Listeners.PlayerInteractListener;
import gg.crystalized.botanica.Soil.Generation.SoilBinBlock;
import gg.crystalized.botanica.Soil.Generation.SoilBinUIManager;
import gg.crystalized.botanica.Soil.Generation.SoilGenerationService;
import gg.crystalized.botanica.PlantSim.Actions.PlantActions;
import gg.crystalized.botanica.PlantSim.Bus.LocalMutationBus;
import gg.crystalized.botanica.PlantSim.Bus.MutationApplier;
import gg.crystalized.botanica.PlantSim.Bus.MutationBus;
import gg.crystalized.botanica.PlantSim.Domain.LocalPlantRepo;
import gg.crystalized.botanica.Soil.Domain.LocalSoilRepo;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.Soil.Domain.SoilInstance;
import gg.crystalized.botanica.Soil.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Planner.LocalWorkPlanner;
import gg.crystalized.botanica.PlantSim.Sim.SimulationDataManager;
import gg.crystalized.botanica.PlantSim.Sim.SimulationService;
import gg.crystalized.botanica.PlantSim.UI.PlantLookupTask;
import gg.crystalized.botanica.PlantSim.UI.PlantStatusUI;
import gg.crystalized.botanica.World.BlockAliasManager;
import gg.crystalized.botanica.PlantSim.World.PaperWorldAdapter;
import gg.crystalized.botanica.World.DisplayEntityManager;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import gg.crystalized.botanica.PlantSim.World.SchematicManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;

import java.io.File;
import java.time.Instant;
import java.util.List;

public class Botanica extends JavaPlugin implements Listener {
    public static Botanica INSTANCE;

    public static SimulationDataManager sdm = new SimulationDataManager();
    
    // Repositories and simulation components
    private PlantRepo plantRepo;
    private SoilRepo soilRepo;
    private SimulationService simulationService;
    private LocalWorkPlanner workPlanner;
    private MutationBus mutationBus;
    private MutationApplier mutationApplier;
    private SchematicBlockIndex schematicBlockIndex;
    private SchematicManager schematicManager;
    private BlockAliasManager aliasManager;
    private DisplayEntityManager displayEntityManager;
    private BotanicaSimulationConfig config;
    
    // Interaction system
    private ItemActionRegistry itemActionRegistry;
    private PlantActions plantActions;
    private ActionResolver actionResolver;

    public List<String> dataPaths = List.of(
      "sim", "sim/specs", "sim/specs/plants", "sim/schematics"
    );

    @Override
    public void onEnable() {
        INSTANCE = this;

        validateDataPaths();
        initializeModules();
    }

    @Override
    public void onDisable() {
        shutdownModules();
    }

    // Registration helpers
    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommand(String commandString, CommandExecutor executor) {
        PluginCommand command = getCommand(commandString);
        if (command == null) {
            getLogger().warning("Failed to get command '" + commandString + "' from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        getLogger().info("Successfully registered command: " + commandString);
    }

    private void validateDataPaths() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        for (String path : dataPaths) {
            File f = new File(getDataFolder() + "/" + path);
            if (!f.exists()) f.mkdirs();
        }
    }

    // Module lifecycle
    private void initializeModules() {
        // Simulation data manager
        sdm.init();
        
        // Initialize repositories
        plantRepo = new LocalPlantRepo();
        soilRepo = new LocalSoilRepo();
        
        // Load config from file
        config = BotanicaSimulationConfig.load();
        
        // Initialize simulation components
        workPlanner = new LocalWorkPlanner();
        mutationBus = new LocalMutationBus();
        schematicBlockIndex = new SchematicBlockIndex();
        aliasManager = new BlockAliasManager();
        schematicManager = new SchematicManager(aliasManager);
        displayEntityManager = new DisplayEntityManager(aliasManager);
        simulationService = new SimulationService(sdm, soilRepo, mutationBus, schematicBlockIndex, schematicManager);
        mutationApplier = new MutationApplier(mutationBus, new PaperWorldAdapter(getServer()), displayEntityManager, config.mutationCapPerTick);
        
        // Initialize interaction system
        itemActionRegistry = new ItemActionRegistry();
        itemActionRegistry.load();
        plantActions = new PlantActions(sdm, soilRepo, plantRepo, mutationBus, schematicManager);
        actionResolver = new ActionResolver(plantActions, soilRepo, plantRepo, schematicBlockIndex);
        
        // Create soil bin system
        SoilGenerationService soilGenerationService = new SoilGenerationService(sdm);
        SoilBinUIManager soilBinUIManager = new SoilBinUIManager(sdm);
        SoilBinBlock soilBinBlock = new SoilBinBlock(soilGenerationService, sdm, aliasManager, displayEntityManager, soilBinUIManager);
        
        // Start soil bin UI look detection
        soilBinUIManager.startLookDetection(this);
        
        // Register event listeners
        registerEvents(new PlantBreakListener(plantRepo, schematicBlockIndex, schematicManager, sdm, config, aliasManager));
        registerEvents(new PlayerInteractListener(itemActionRegistry, actionResolver, plantRepo, soilRepo, plantActions, aliasManager, soilBinBlock, displayEntityManager));
        
        // Start simulation loop
        startSimulation();
        
        // Start mutation applier
        mutationApplier.start(this);
        
        // Start action bar UI task (runs every 10 ticks = 0.5 seconds)
        PlantStatusUI statusUI = new PlantStatusUI(sdm, plantRepo, soilRepo);
        PlantLookupTask lookupTask = new PlantLookupTask(
            soilRepo, 
            plantRepo, 
            schematicBlockIndex, 
            sdm, 
            statusUI
        );
        lookupTask.runTaskTimer(this, 0L, 10L);

        registerCommand("botanica", new PlantCommands(sdm, soilRepo, plantRepo, simulationService, mutationBus, schematicManager, displayEntityManager, aliasManager));
        
        getLogger().info("Botanica plant simulation plugin enabled!");
        getLogger().info("Registered botanica command with " + (getCommand("botanica") != null ? "SUCCESS" : "FAILURE"));
    }

    private void shutdownModules() {
        // Stop simulation
        stopSimulation();
        if (mutationApplier != null) {
            mutationApplier.stop();
        }
        
        // Clean up display entities
        if (displayEntityManager != null) {
            displayEntityManager.removeAll();
        }
        
        // Simulation data manager
        sdm.shutdownContentLoader();
    }
    
    private void startSimulation() {
        // Run lightweight resource updates every 4 ticks (0.2 seconds) for smooth consumption
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                runResourceUpdate();
            } catch (Exception e) {
                getLogger().warning("Resource update error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 4L, 4L);
        
        // Run full simulation every 20 ticks (1 second) for growth logic
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                runSimulationStep();
            } catch (Exception e) {
                getLogger().warning("Simulation error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 20L, 20L);
    }
    
    private void stopSimulation() {
        // Scheduler will be cancelled when plugin disables
    }
    
    private void runResourceUpdate() {
        // Lightweight resource consumption and growth update for all active plants
        List<PlantInstance> activePlants = plantRepo.getAll();
        if (activePlants.isEmpty()) return; // Skip if no plants
        
        Instant now = Instant.now();
        
        // Process plants in batches to avoid performance issues
        int batchSize = Math.min(50, activePlants.size());
        for (int i = 0; i < batchSize; i++) {
            PlantInstance plant = activePlants.get(i);
            if (plant.complete) continue;
            
            // Update soil resource consumption
            SoilInstance soil = soilRepo.get(plant.soilPos);
            if (soil != null) {
                SoilInstance updatedSoil = simulationService.updateSoilResources(soil, now);
                soilRepo.upsert(updatedSoil);
                
                // Also update plant growth with current soil conditions
                PlantInstance updatedPlant = simulationService.updatePlantGrowth(plant, updatedSoil, now);
                if (updatedPlant != null) {
                    plantRepo.upsert(updatedPlant);
                }
            }
        }
    }
    
    private void runSimulationStep() {
        // Get plants that are due for simulation
        List<PlantInstance> duePlants = workPlanner.takeDue(100, java.time.Instant.now());
        if (duePlants.isEmpty()) return;
        
        // Run simulation
        List<gg.crystalized.botanica.PlantSim.Bus.Mutation> mutations = simulationService.simulatePlants(duePlants, java.time.Instant.now());
        
        // Save updated plants
        plantRepo.upsertAll(duePlants);
        
        // Reschedule plants for next update
        for (PlantInstance plant : duePlants) {
            if (plant.nextUpdateAt != null) {
                workPlanner.reschedule(plant);
            }
        }
        
        // Queue mutations for world changes
        mutationBus.queueAll(mutations);
    }
    
    // Getters for repositories
    public PlantRepo getPlantRepo() { return plantRepo; }
    public SoilRepo getSoilRepo() { return soilRepo; }
    public SimulationService getSimulationService() { return simulationService; }
}
