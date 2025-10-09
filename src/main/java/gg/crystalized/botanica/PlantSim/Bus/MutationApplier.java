package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.World.BlockPos;
import gg.crystalized.botanica.PlantSim.World.WorldAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class MutationApplier {
    private final MutationBus bus;
    private final WorldAdapter world;
    private final int perTickCap;
    private BukkitTask task;

    public MutationApplier(MutationBus bus, WorldAdapter world, int perTickCap) {
        this.bus = bus;
        this.world = world;
        this.perTickCap = Math.max(1, perTickCap);
    }

    public void start(Plugin plugin) {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Mutation> drained = new ArrayList<>(perTickCap);
            int n = bus.drain(drained, perTickCap);
            if (n == 0) return;

            Map<BlockPos, BlockMutation> lastBlockChange = new HashMap<>();
            List<Mutation> others = new ArrayList<>();
            for (Mutation m : drained) {
                if (m instanceof BlockMutation bm) lastBlockChange.put(bm.pos(), bm);
                else others.add(m);
            }

            lastBlockChange.values().stream()
                    .sorted(Comparator.<BlockMutation, String>comparing(bm -> bm.pos().world())
                            .thenComparingLong(bm -> bm.pos().chunkKey()))
                    .forEach(bm -> world.setBlock(bm.pos(), bm.materialName(), bm.blockDataJson()));

            for (Mutation m : others) {
                if (m instanceof ItemDropMutation(BlockPos pos, String materialName, int amount, String nbtJson)) world.dropItem(pos, materialName, amount, nbtJson);
                else if (m instanceof SoundMutation(BlockPos pos, String soundKey, float volume, float pitch)) world.playSound(pos, soundKey, volume, pitch);
            }
        }, 1L, 1L);
    }

    public void stop() { if (task != null) { task.cancel(); task = null; } }
}