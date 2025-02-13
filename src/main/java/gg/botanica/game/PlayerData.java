package gg.botanica.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;

public class PlayerData {
    enum gameState{
        tutorial,
        game
    }
    //TODO add plants and plots
    int level = 0;
    int plantsAmount = 0;
    int money = 0;
    int plotsAmount = 1;
    gameState state;
    ArrayList<Plant> plants = new ArrayList<>();
    //TODO make spawn change depending on the plot
    Location spawn = new Location(Bukkit.getServer().getWorld("world"), 8, -50, 14);
    String name;

    public PlayerData(String name, gameState state){
        this.name = name;
        this.state = state;
    }
}
