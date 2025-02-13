package gg.botanica.game;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.bukkit.Material.*;

public class Dirt {
    double nutrients;
    static final List<Material> dirt = Arrays.asList(FARMLAND, ROOTED_DIRT, COARSE_DIRT, DIRT, GRASS_BLOCK);
    Material dirtType;
    boolean withPlant;

    public Dirt(double nutrients, Material dirtType, boolean withPlant){
        this.nutrients = nutrients;
        this.dirtType = dirtType;
        this.withPlant = withPlant;
    }

    //TODO public static isOptimal(Plant plant){}
}
