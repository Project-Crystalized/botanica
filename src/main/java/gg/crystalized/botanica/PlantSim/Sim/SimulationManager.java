package gg.crystalized.botanica.PlantSim.Sim;

import gg.crystalized.botanica.Botanica;
import gg.crystalized.botanica.Config.BotanicaSimulationConfig;
import gg.crystalized.botanica.PlantSim.Bus.Mutation;
import gg.crystalized.botanica.PlantSim.Bus.MutationBus;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;
import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.PlantSim.Domain.SoilRepo;
import gg.crystalized.botanica.PlantSim.Planner.LocalWorkPlanner;
import gg.crystalized.botanica.PlantSim.Planner.WorkPlanner;
import gg.crystalized.botanica.PlantSim.Domain.PlantRepo;
import gg.crystalized.botanica.PlantSim.World.SchematicBlockIndex;
import gg.crystalized.botanica.PlantSim.World.SchematicManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Periodic lane + fast lane orchestration. */
public class SimulationManager {
    private final BotanicaSimulationConfig cfg;
    private final ExecutorService workers;
    private final MutationBus bus;
    private final PlantRepo plantRepo;
    private final SoilRepo soilRepo;
    private final WorkPlanner planner;
    private final SimulationDataManager dataManager = new SimulationDataManager();
    private final SimulationService service;

    private BukkitTask periodicTask;

    public SimulationManager(BotanicaSimulationConfig cfg, ExecutorService workers, MutationBus bus, PlantRepo plantRepo, SoilRepo soilRepo, WorkPlanner planner, SchematicBlockIndex schematicBlockIndex, SchematicManager schematicManager, gg.crystalized.botanica.PlantSim.World.DisplayEntityManager displayEntityManager) {
        this.cfg = cfg; this.workers = workers; this.bus = bus; this.plantRepo = plantRepo; this.soilRepo = soilRepo; this.planner = planner;
        this.service = new SimulationService(dataManager, soilRepo, bus, schematicBlockIndex, schematicManager);
    }

    public void start(Plugin plugin) {
        stop();
        long ticks = Math.max(1L, cfg.getPeriodicSweepDuration().toMillis() / 50L);
        periodicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runPeriodic, ticks, ticks);
    }

    public void stop() { if (periodicTask != null) { periodicTask.cancel(); periodicTask = null; } }

    private void runPeriodic() {
        Instant now = Instant.now();
        List<PlantInstance> work = planner.takeDue(cfg.maxPlantsPerCycle, now);
        submitBatches(work, now);
    }

    /** Fast lane: call when a player action changes a plant. */
    public void runUrgent(List<PlantInstance> plants) {
        if (plants == null || plants.isEmpty()) return;
        Instant now = Instant.now();
        List<PlantInstance> limited = plants.size() > cfg.urgentPlantsPerBurst ? new ArrayList<>(plants.subList(0, cfg.urgentPlantsPerBurst)) : plants;
        submitBatches(limited, now);
    }

    private void submitBatches(List<PlantInstance> plants, Instant now) {
        if (plants.isEmpty()) return;
        int batchSize = Math.max(64, cfg.batchSize);
        for (int i = 0; i < plants.size(); i += batchSize) {
            List<PlantInstance> slice = plants.subList(i, Math.min(plants.size(), i + batchSize));
            workers.submit(() -> processBatch(slice, now));
        }
    }

    private void processBatch(List<PlantInstance> plants, Instant now) {
        try {
            // For now, we'll need to fetch soils separately
            // TODO: Implement proper soil fetching and batching
            List<Mutation> muts = service.simulatePlants(plants, now);
            plantRepo.upsertAll(plants);
            for (PlantInstance p : plants) if (p.nextUpdateAt != null && planner instanceof LocalWorkPlanner lp) lp.reschedule(p);
            bus.queueAll(muts);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Botanica sim error: " + e.getMessage());
        }
    }
}