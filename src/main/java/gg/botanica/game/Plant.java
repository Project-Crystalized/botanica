package gg.botanica.game;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bukkit.Material.*;

public class Plant {

    enum state{
        in_inventory, sprout, sapling, grown_up
    }
    enum type{
        tree, flower, vine, crop
    }
    static final List<Material> dirt = Arrays.asList(FARMLAND, ROOTED_DIRT, COARSE_DIRT, DIRT, GRASS_BLOCK);
    Location location;
    List<Location> blockLocation = new ArrayList<>();
    int waterDuration;
    int growUpTime;
    int optimalDirt;
    state state;
    String owner;
    type type;

    public Plant(Location location, int waterDuration, int growUpTime, int optimalDirt, state state, String owner, type type){
        this.location = location;
        this.waterDuration = waterDuration;
        this.growUpTime = growUpTime;
        this.optimalDirt = optimalDirt;
        this.state = state;
        this.owner = owner;
        this.type = type;
        //TODO plant()
        //TODO generateBlockLoc()
    }



}
