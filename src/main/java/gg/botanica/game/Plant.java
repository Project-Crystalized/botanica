package gg.botanica.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static org.bukkit.Material.*;
import static org.bukkit.entity.EntityType.BLOCK_DISPLAY;

public class Plant {

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
    PlantType exactType;

    public Plant(Location location, int growUpTime, state state, Player owner, PlantType exactType){
        this.location = location;
        this.growUpTime = growUpTime;
        this.state = state;
        this.owner = owner;
        this.exactType = exactType;
        //TODO plantFromSapling?()
        //TODO generateBlockLoc()
    }

    public static void plantFromSeed(Player p, Location loc){
        //TODO check treeType
        Plant plant = new Plant(loc, 0, state.sprout, p, PlantType.STARTER);
        Location location = loc;
        location.setY(loc.getY()+0.5);
        BlockDisplay block = (BlockDisplay)location.getWorld().spawnEntity(loc,BLOCK_DISPLAY);
        Display.Brightness bright = new Display.Brightness(14, 15);
        block.setBrightness(bright);
        block.setBlock(Bukkit.createBlockData(MANGROVE_PROPAGULE));
    }

    public ItemStack buildSeed(PlantType plantType){
        ItemStack seed = new ItemStack(BEETROOT_SEEDS);
        ItemMeta meta = seed.getItemMeta();
        meta.displayName(Component.text(plantType.name + " Seed").color(WHITE).decoration(ITALIC, false));
        List<Component> lore = new ArrayList<>();
        //TODO add more stuff to the lore
        lore.add(Component.text(""));
        lore.add(Component.text(plantType.type.toString()).color(GREEN).decoration(ITALIC, false));
        lore.add(Component.text("Rarity: " + plantType.rarity).color(LIGHT_PURPLE).decoration(ITALIC, false));
        lore.add(Component.text("Optimal dirt: " + plantType.dirt.toString()).color(WHITE).decoration(ITALIC, false));
        meta.lore(lore);
        seed.setItemMeta(meta);
        return seed;
    }

}
