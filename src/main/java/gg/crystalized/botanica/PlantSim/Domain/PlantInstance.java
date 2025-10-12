package gg.crystalized.botanica.PlantSim.Domain;

import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.ChunkRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlantInstance {
    public final UUID id;
    public final String speciesId;

    // Claim/access
    public final String ownerId;
    public final BlockPos pos;
    public final ChunkRef chunk;
    
    // Link to soil (resolved at plant placement)
    public final BlockPos soilPos;

    // Growth state
    public double progress = 0.0;    // 0..1
    public boolean complete = false;
    public String currentSchematicId = null;  // Tracks the current schematic at any growth stage (if applicable)
    public double fertility = 0.0;   // Bonus added to drop ranges (e.g., 2.5 = +2.5 to min/max)
    public int rotation = 0;         // 0, 90, 180, or 270 degrees (for Y-axis rotation of schematics)


    // Scheduling
    public Instant lastSimAt = Instant.now();
    public Instant nextUpdateAt = Instant.now();

    // Mods like nutrients, vines, etc.
    public final List<PlantModifier> modifiers = new ArrayList<>();

    public PlantInstance(UUID id, String speciesId, String ownerId, BlockPos pos, BlockPos soilPos) {
        this.id = id;
        this.speciesId = speciesId;
        this.ownerId = ownerId;
        this.pos = pos;
        this.soilPos = soilPos;
        this.chunk = ChunkRef.of(pos);
    }
}