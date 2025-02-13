package gg.botanica.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.Material.*;
import static org.bukkit.entity.EntityType.BLOCK_DISPLAY;

public class Plant<T> {

    enum state{
        in_inventory, seed, sprout, sapling, grown_up
    }
    enum type{
        tree, flower, vine
    }
    Location location;
    List<Location> blockLocation = new ArrayList<>();
    int growUpTime;
    static state state;
    Player owner;
    T exactType;

    public Plant(Location location, int growUpTime, state state, Player owner, T exactType){
        this.location = location;
        this.growUpTime = growUpTime;
        this.state = state;
        this.owner = owner;
        this.exactType = exactType;
        //TODO plant()
        //TODO generateBlockLoc()
    }

    public static void plantFromSeed(Player p, Location loc){
        //TODO check treeType
        Plant plant = new Plant(loc, 0, state.sprout, p, TreeType.STARTER);
        Location location = loc;
        location.setY(loc.getY()+0.5);
        BlockDisplay block = (BlockDisplay)location.getWorld().spawnEntity(loc,BLOCK_DISPLAY);
        Display.Brightness bright = new Display.Brightness(14, 15);
        block.setBrightness(bright);
        block.setBlock(Bukkit.createBlockData(MANGROVE_PROPAGULE));
    }

}
