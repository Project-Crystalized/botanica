package gg.botanica.game;

import org.bukkit.Material;

public enum PlantType {
    STARTER(Plant.type.tree, 1.0, 10, 0, "Starter Tree", Material.COARSE_DIRT),
    APPLE(Plant.type.tree, 1.5, 30, 1, "Apple Tree", Material.COARSE_DIRT);
    final Plant.type type;
    final double nutrients;
    final int waterDuration;
    final int rarity;
    final String name;
    final Material dirt;
    PlantType(Plant.type type, double nutrients, int waterDuration, int rarity, String name, Material dirt){
        this.type = type;
        this.nutrients = nutrients;
        this.waterDuration = waterDuration;
        this.rarity = rarity;
        this.name = name;
        this.dirt = dirt;
    }
}
