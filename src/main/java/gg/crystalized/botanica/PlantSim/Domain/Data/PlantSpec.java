package gg.crystalized.botanica.PlantSim.Domain.Data;

import java.util.List;

public final class PlantSpec {
    public String id;
    public String name;
    public String kind;                // "GENERIC"/"TREE"/"FLOWER"/"VINE" (validate to enum in code)
    public double baseGrowthDuration;   // required
    public String optimalSoil;         // SoilSpec.id
    public boolean destroyOnHarvest = true;  // If false, plant regresses to 2nd-to-last stage and regrows
    public boolean allowRotation = false;    // If true, plant is randomly rotated on planting (90° increments)
    
    // Growth requirements and consumption grouped
    public GrowthFactors growthFactors;
    
    // Mutations (visual blocks, sounds, particles, etc.)
    public Mutations mutations;
    
    // Drops
    public List<Drop> drops;
    
    public static final class GrowthFactors {
        // Growth gates (windows)
        public Integer waterMin;
        public Integer waterMax;
        public Integer nutMin;
        public Integer nutMax;
        public Integer robustness;
        
        // Consumption rates (per second when active)
        public double waterPerSec;
        public double nutrientsPerSec;
    }

    public static final class Mutations {
        public List<String> stages;              // Growth stages (not harvestable)
        public List<String> harvestableStages;   // Harvestable stages (can harvest with drop multiplier)
        // Future: growthSounds, harvestSounds, particles, etc.
    }

    public static final class Drop {
        public String type;
        public String id;
        public int min;
        public int max;
        public double dropHeight = 0.0;      // Height offset from plant root for drops (default 0)
        public double dropRangeMin = 0.0;    // Minimum distance from plant center for drop spawn (blocks)
        public double dropRangeMax = 0.5;    // Maximum distance from plant center for drop spawn (blocks)
        public boolean makeGroups = false;   // If true, split drops into 2-3 groups at different positions
    }
}
