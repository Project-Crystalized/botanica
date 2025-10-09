package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.World.BlockPos;

public record SoundMutation(BlockPos pos, String soundKey, float volume, float pitch) implements Mutation { }
