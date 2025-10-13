package gg.crystalized.botanica.PlantSim.Bus;

import gg.crystalized.botanica.PlantSim.World.BlockPos;

public sealed interface Mutation permits BlockMutation, ItemDropMutation, SoundMutation, DisplayEntityMutation { }

