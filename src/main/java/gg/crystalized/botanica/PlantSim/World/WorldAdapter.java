package gg.crystalized.botanica.PlantSim.World;

public interface WorldAdapter {
    void setBlock(BlockPos pos, String materialName, String blockDataJson);
    void dropItem(BlockPos pos, String materialName, int amount, String nbtJson);
    void playSound(BlockPos pos, String soundKey, float volume, float pitch);
    void spawnParticle(BlockPos pos, String particleKey, int count, double extra, double xOff, double yOff, double zOff);
}
