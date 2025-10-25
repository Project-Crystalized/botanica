package gg.crystalized.botanica.Soil.Domain;

import gg.crystalized.botanica.World.BlockPos;
import gg.crystalized.botanica.World.ChunkRef;

import java.time.Instant;

public class SoilInstance {
    public final BlockPos pos;
    public final ChunkRef chunk;
    public String soilId;            // -> SoilSpec
    public double qualityMult;       // per-tile growth speed multiplier, default 1.0
    public double water;             // 0..100
    public double nutrients;         // 0..100
    public boolean tilled;           // whether soil is prepared for planting
    public Instant nextUpdateAt;
    public Instant lastUpdateAt;

    public SoilInstance(BlockPos pos, String soilId, double qualityMult, double water, double nutrients, boolean tilled, Instant now) {
        this.pos = pos;
        this.chunk = ChunkRef.of(pos);
        this.soilId = soilId;
        this.qualityMult = qualityMult;
        this.water = water;
        this.nutrients = nutrients;
        this.tilled = tilled;
        this.nextUpdateAt = now;
        this.lastUpdateAt = now;
    }
}
