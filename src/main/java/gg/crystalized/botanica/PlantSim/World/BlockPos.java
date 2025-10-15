package gg.crystalized.botanica.PlantSim.World;

import org.bukkit.Location;

public record BlockPos(String world, int x, int y, int z) {
    public int chunkX() { return x >> 4; }
    public int chunkZ() { return z >> 4; }
    public long chunkKey() { return (((long) x) >> 4) << 32 | ((((long) z) >> 4) & 0xffffffffL); }
    
    public BlockPos below() { return new BlockPos(world, x, y - 1, z); }
    public BlockPos above() { return new BlockPos(world, x, y + 1, z); }
    
    public BlockPos north() { return new BlockPos(world, x, y, z - 1); }
    public BlockPos south() { return new BlockPos(world, x, y, z + 1); }
    public BlockPos east() { return new BlockPos(world, x + 1, y, z); }
    public BlockPos west() { return new BlockPos(world, x - 1, y, z); }
    
    public Location toBukkitLocation() {
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z);
    }
    
    public static BlockPos fromBukkitLocation(Location location) {
        return new BlockPos(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }
    
    public static BlockPos fromBukkitLocation(Location location, int offsetY) {
        return new BlockPos(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY() + offsetY,
            location.getBlockZ()
        );
    }
}
