package gg.botanica.game;

import org.bukkit.Material;

public enum PlantType {
    STARTER(Plant.type.tree, 1.0, 20, 20,0, "Starter Tree", Material.COARSE_DIRT),
    APPLE(Plant.type.tree, 1.5, 20, 30,1, "Apple Tree", Material.COARSE_DIRT);
    final Plant.type type;
    final double nutrients;
    //waterDuration = count down speed
    final int waterDuration;
    //max_water = maximal amount of water this plant can take up before being overwatered (min_water is always max_water/4)
    final int max_water;
    final int rarity;
    final String name;
    final Material dirt;
    PlantType(Plant.type type, double nutrients, int waterDuration, int max_water,int rarity, String name, Material dirt){
        this.type = type;
        this.nutrients = nutrients;
        this.waterDuration = waterDuration;
        this.max_water = max_water;
        this.rarity = rarity;
        this.name = name;
        this.dirt = dirt;
    }
}
