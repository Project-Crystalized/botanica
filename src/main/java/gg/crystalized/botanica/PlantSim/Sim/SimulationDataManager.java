package gg.crystalized.botanica.PlantSim.Sim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gg.crystalized.botanica.Botanica;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantRegistry;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSetRegistry;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSetSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.PlantSpec;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilRegistry;
import gg.crystalized.botanica.PlantSim.Domain.Data.SoilSpec;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

public class SimulationDataManager {

    // ----- Catalog & accessors ------------------------------------------------

    record ContentCatalog(
            PlantRegistry plants,
            SoilRegistry soils,
            PlantSetRegistry sets
    ) {}

    private volatile ContentCatalog catalog; // atomic snapshot for readers

    public PlantRegistry plants() { return catalog.plants(); }
    public SoilRegistry soils()   { return catalog.soils(); }
    public PlantSetRegistry sets(){ return catalog.sets(); }

    // ----- Threading ----------------------------------------------------------

    private final ExecutorService contentExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "botanica-content-loader");
        t.setDaemon(true);
        return t;
    });

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Call on plugin enable (sync is fine at startup)
    public void init() {
        validate();
        catalog = buildCatalog(); // blocking build at startup
    }

    // Call on /reload (non-blocking)
    public CompletableFuture<Void> reloadAsync() {
        return CompletableFuture
                .supplyAsync(this::buildCatalog, contentExec) // off main thread
                .thenAccept(newCat -> {
                    this.catalog = newCat; // atomic swap
                    Botanica.INSTANCE.getLogger().info(String.format(
                            "Content reloaded: plants=%d, soils=%d, sets=%d",
                            newCat.plants().all().size(),
                            newCat.soils().all().size(),
                            newCat.sets().all().size()
                    ));
                })
                .exceptionally(ex -> {
                    Botanica.INSTANCE.getLogger().severe("Content reload FAILED: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }

    public void shutdownContentLoader() { contentExec.shutdownNow(); }

    // ----- Build catalog ------------------------------------------------------

    private ContentCatalog buildCatalog() {
        Map<String, SoilSpec> soilsById = loadSoils();
        SetsAndPlants sp = loadPlantSetsAndPlants(); // one pass → sets (IDs) + plants
        return new ContentCatalog(
                new PlantRegistry(sp.plantsById()),
                new SoilRegistry(soilsById),
                new PlantSetRegistry(sp.setsById())
        );
    }

    // ----- Loaders ------------------------------------------------------------

    // SoilSpec.json is an ARRAY of soil objects
    private Map<String, SoilSpec> loadSoils() {
        File soilSpecFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/specs/SoilSpec.json");
        List<SoilSpec> list = new ArrayList<>();
        if (soilSpecFile.exists()) {
            try (FileReader reader = new FileReader(soilSpecFile)) {
                Type listType = new TypeToken<List<SoilSpec>>(){}.getType();
                List<SoilSpec> loaded = gson.fromJson(reader, listType);
                if (loaded != null) list = loaded;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // index by id
        Map<String, SoilSpec> byId = new LinkedHashMap<>();
        for (SoilSpec s : list) {
            if (s == null || s.id == null || s.id.isBlank()) continue;
            if (byId.putIfAbsent(s.id, s) != null) {
                Botanica.INSTANCE.getLogger().warning("Duplicate soil id: " + s.id);
            }
        }
        return byId;
    }

    // Each file in sim/specs/plants is ONE PlantSetSpec JSON object
    private record SetsAndPlants(
            Map<String, PlantSetSpec> setsById,
            Map<String, PlantSpec> plantsById
    ) {}

    private static final class PlantSetFileDeserializer {
        String set;
        Integer reward;
        java.util.List<PlantSpec> plants; // embedded plants FROM DISK only
    }

    private SetsAndPlants loadPlantSetsAndPlants() {
        File dir = new File(Botanica.INSTANCE.getDataFolder(), "sim/specs/plants");
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));

        Map<String, PlantSetSpec> setsById = new LinkedHashMap<>();
        Map<String, PlantSpec>    plantsById = new LinkedHashMap<>();
        if (files == null) return new SetsAndPlants(setsById, plantsById);

        for (File f : files) {
            try (FileReader reader = new FileReader(f)) {
                PlantSetFileDeserializer raw = gson.fromJson(reader, PlantSetFileDeserializer.class);
                if (raw == null || raw.set == null || raw.set.isBlank()) {
                    Botanica.INSTANCE.getLogger().warning("Skipping invalid plant set file: " + f.getName());
                    continue;
                }

                // Build runtime set spec (IDs only)
                PlantSetSpec setSpec = new PlantSetSpec();
                setSpec.set = raw.set;
                setSpec.reward = raw.reward;
                setSpec.plants = new ArrayList<>();

                if (raw.plants != null) {
                    for (PlantSpec p : raw.plants) {
                        if (p == null || p.id == null || p.id.isBlank()) {
                            Botanica.INSTANCE.getLogger().warning("Skipping plant with missing id in set " + raw.set);
                            continue;
                        }
                        
                        // Validate destroyOnHarvest=false requires at least 2 stages
                        if (!p.destroyOnHarvest) {
                            if (p.mutations == null || p.mutations.stages == null || p.mutations.stages.size() < 2) {
                                Botanica.INSTANCE.getLogger().severe(
                                    "INVALID SPEC: Plant '" + p.id + "' has destroyOnHarvest=false but fewer than 2 growth stages! " +
                                    "Renewable harvest requires at least 2 stages (empty + full). Skipping this plant."
                                );
                                continue;
                            }
                        }

                        // Fill plants map (first-wins)
                        if (plantsById.putIfAbsent(p.id, p) != null) {
                            Botanica.INSTANCE.getLogger().warning("Duplicate plant id: " + p.id + " (in set " + raw.set + ")");
                        }
                        // Always add ID to set membership (dedupe if you want)
                        setSpec.plants.add(p.id);
                    }
                }

                // Put set spec (first-wins)
                if (setsById.putIfAbsent(setSpec.set, setSpec) != null) {
                    Botanica.INSTANCE.getLogger().warning("Duplicate plant set id: " + setSpec.set + " (" + f.getName() + ")");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new SetsAndPlants(setsById, plantsById);
    }

    // ----- Bootstrap defaults ------------------

    public void validate() {
        File base = Botanica.INSTANCE.getDataFolder();

        File soilSpecFile = new File(base, "sim/specs/SoilSpec.json");
        soilSpecFile.getParentFile().mkdirs();
        if (!soilSpecFile.exists()) {
            System.out.println("[!] SoilSpec missing, creating at /plugins/Botanica/sim/specs/SoilSpec.json");
            List<Map<String, Object>> soilSpecBase = List.of(
                    Map.of("id", "SANDY", "displayName", "Sandy soil", "block", "SAND"),
                    Map.of("id", "LOAMY", "displayName", "Loamy soil", "block", "DIRT"),
                    Map.of("id", "CLAY", "displayName", "Clay soil", "block", "CLAY")
            );
            createConfigs(soilSpecBase, "SoilSpec");
        }

        File plantSpecPath = new File(base, "sim/specs/plants");
        plantSpecPath.mkdirs();
        File[] files = plantSpecPath.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            System.out.println("[!] Plant set specs missing, creating Template at /plugins/Botanica/sim/specs/plants/Template.json");
            
            // Growth factors (nested)
            Map<String, Object> growthFactors = new HashMap<>();
            growthFactors.put("waterMin", 20);
            growthFactors.put("waterMax", 80);
            growthFactors.put("nutMin", 30);
            growthFactors.put("nutMax", 70);
            growthFactors.put("robustness", 10);
            growthFactors.put("waterPerSec", 0.5);
            growthFactors.put("nutrientsPerSec", 0.3);
            
            // Mutations (visual stages)
            Map<String, Object> mutations = Map.of(
                    "stages", List.of("OAK_SAPLING", "schematic:oak_stump", "schematic:oak_tree_empty", "schematic:oak_tree_full")
            );
            
            // Drops with height, range, and grouping
            Map<String, Object> drop = new HashMap<>();
            drop.put("type", "FRUIT");
            drop.put("id", "APPLE");
            drop.put("min", 1);
            drop.put("max", 4);
            drop.put("dropHeight", 3.0);
            drop.put("dropRangeMin", 1.0);   // Drops spawn 1.0-1.5 blocks from tree center
            drop.put("dropRangeMax", 1.5);
            drop.put("makeGroups", true);    // Split drops into 2-3 groups at different positions
            
            // Plant spec
            Map<String, Object> plant = new HashMap<>();
            plant.put("id", "oak_tree");
            plant.put("name", "Oak tree");
            plant.put("kind", "TREE");
            plant.put("baseGrowthDuration", 30);
            plant.put("optimalSoil", "LOAMY");
            plant.put("destroyOnHarvest", false); // Renewable harvest for testing
            plant.put("allowRotation", false); // Set to true for random rotation on planting
            plant.put("growthFactors", growthFactors);
            plant.put("mutations", mutations);
            plant.put("drops", List.of(drop));
            
            Map<String, Object> plantSpecBase = Map.of(
                    "set", "Template",
                    "reward", 50,
                    "plants", List.of(plant)
            );
            createConfigs(plantSpecBase, "plants/Template");
        }
    }

    void createConfigs(Object baseSpec, String path) {
        File configFile = new File(Botanica.INSTANCE.getDataFolder(), "sim/specs/" + path + ".json");
        configFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(baseSpec, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
