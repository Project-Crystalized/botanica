package gg.crystalized.botanica.PlantSim.World;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;


public class PaperWorldAdapter implements WorldAdapter {
    private final Server server;
    public PaperWorldAdapter(Server server) { this.server = server; }

    @Override
    public void setBlock(BlockPos pos, String materialName, String blockDataJson) {
        World w = server.getWorld(pos.world());
        if (w == null) return;
        Block b = w.getBlockAt(pos.x(), pos.y(), pos.z());
        Material m = Material.matchMaterial(materialName);
        if (m == null) return;
        b.setType(m, false);
        // probably something with block state here idk
    }

    @Override
    public void dropItem(BlockPos pos, String materialName, int amount, String nbtJson) {
        World w = server.getWorld(pos.world());
        if (w == null) return;
        Material m = Material.matchMaterial(materialName);
        if (m == null) return;
        ItemStack stack = new ItemStack(m, Math.max(1, amount));
        w.dropItemNaturally(new Location(w, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), stack);
    }

    @Override
    public void playSound(BlockPos pos, String soundKey, float volume, float pitch) {
        World w = server.getWorld(pos.world());
        if (w == null) return;
        w.playSound(new Location(w, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), soundKey, volume, pitch);
    }

    @Override
    public void spawnParticle(BlockPos pos, String particleKey, int count, double extra, double xOff, double yOff, double zOff) {
        World w = server.getWorld(pos.world());
        if (w == null) return;
        w.spawnParticle(Particle.valueOf(particleKey),pos.x(), pos.y(), pos.z(), count, xOff, yOff, zOff, extra);
    }
}

