package gg.crystalized.botanica.PlantSim.World;

/** Identifies a chunk in a world. Useful for grouping and loading plants per chunk. */
public record ChunkRef(String world, int chunkX, int chunkZ) {
    public static ChunkRef of(BlockPos p) { return new ChunkRef(p.world(), p.chunkX(), p.chunkZ()); }
    public long key() { return ((long) chunkX << 32) | (chunkZ & 0xffffffffL); }
    @Override public String toString() { return world + ":" + chunkX + "," + chunkZ; }
}