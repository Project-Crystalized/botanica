package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.World.BlockPos;

public record ItemDropMutation(BlockPos pos, String materialName, int amount, String nbtJson) implements Mutation { }
