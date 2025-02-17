package gg.botanica.game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.BOLD;
import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static org.bukkit.Material.*;
import static org.bukkit.entity.EntityType.BLOCK_DISPLAY;
import static org.bukkit.entity.EntityType.TEXT_DISPLAY;

public class Plant {

    enum state{
        sprout, sapling, grown_up
    }
    enum type{
        tree, flower, vine
    }
    static NamespacedKey key = new NamespacedKey(Botanica.getInstance(), "plugin");
    Location location;
    List<Location> blockLocation = new ArrayList<>();
    int growUpTime;
    static state state;
    Player owner;
    PlantType exactType;
    int waterLevel;
    TextDisplay waterDisplay;
    int TimerId;
    boolean inInventory;

    public Plant(Location location, int growUpTime, state state, Player owner, PlantType exactType, boolean inInventory){
        this.location = location;
        this.growUpTime = growUpTime;
        this.state = state;
        this.owner = owner;
        this.exactType = exactType;
        this.inInventory = inInventory;
        //TODO plantFromSapling?()
        //TODO generateBlockLoc()
    }

    public static Plant plantFromSeed(Player p, Location loc, ItemStack item){
        PersistentDataContainer cont = item.getItemMeta().getPersistentDataContainer();
        Integer integer = cont.get(key, PersistentDataType.INTEGER);
        if(integer == null){
            return null;
        }
        Plant plant = new Plant(loc, 0, state.sprout, p, PlantType.values()[integer], false);
        Location location = loc;
        location.setY(loc.getY()+0.5);
        BlockDisplay block = (BlockDisplay)location.getWorld().spawnEntity(loc,BLOCK_DISPLAY);
        Display.Brightness bright = new Display.Brightness(14, 15);
        block.setBrightness(bright);
        block.setBlock(Bukkit.createBlockData(MANGROVE_PROPAGULE));
        plant.createTextDisplay();
        plant.waterDisplay = plant.waterDisplay();
        return plant;
    }

    public static ItemStack buildSeed(PlantType plantType){
        ItemStack seed = new ItemStack(BEETROOT_SEEDS);
        ItemMeta meta = seed.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, PersistentDataType.INTEGER, plantType.ordinal());
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

    public void createTextDisplay(){
        Location loc = new Location(location.getWorld(),location.getBlockX(),location.getBlockY(), location.getBlockZ());
        loc.setY(loc.getY()+2.5);
        loc.setZ(loc.getZ()+1);
        TextDisplay display = (TextDisplay)loc.getWorld().spawnEntity(loc, TEXT_DISPLAY);
        display.text(Component.text(exactType.name).color(GREEN).decoration(BOLD, true));
    }

    public TextDisplay waterDisplay(){
        Location loc = new Location(location.getWorld(),location.getBlockX(),location.getBlockY(), location.getBlockZ());
        loc.setY(loc.getY()+2.2);
        loc.setZ(loc.getZ()+1);
        TextDisplay display = (TextDisplay)loc.getWorld().spawnEntity(loc, TEXT_DISPLAY);
        display.text(Component.text("Water me!").color(AQUA));
        return display;
    }

}
