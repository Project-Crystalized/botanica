package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.World.BlockPos;

public record BlockMutation(BlockPos pos, String materialName, String blockDataJson) implements Mutation { }
