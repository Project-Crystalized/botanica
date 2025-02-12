package gg.botanica.game;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Botanica extends JavaPlugin{

    @Override
    public void onEnable(){

        DebugCommands dc = new DebugCommands();
        this.getCommand("botanica_start").setExecutor(dc);

    }

    @Override
    public void onDisable(){

    }

    public static Botanica getInstance(){
        return getPlugin(Botanica.class);
    }

    public static void startGame(Player p){
        p.sendMessage("starting Botanica...");
    }
}
