package gg.botanica.game;

import org.bukkit.Material;

public enum TreeType{
    STARTER(1.0, 10, 0, "Starter Tree"),
    APPLE(1.5, 30, 1, "Apple Tree");
    final Plant.type type = Plant.type.tree;
    final double nutrients;
    final int waterDuration;
    final int rarity;
    final String name;
    final Material dirt = Material.COARSE_DIRT;
    TreeType(double nutrients, int waterDuration, int rarity, String name){
        this.nutrients = nutrients;
        this.waterDuration = waterDuration;
        this.rarity = rarity;
        this.name = name;
    }
}
