package gg.botanica.game;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.bukkit.Material.*;

public class Botanica extends JavaPlugin{

    static HashMap<String, PlayerData> playerDatas = new HashMap<>();
    //TODO add Location of Botanica lobby


    @Override
    public void onEnable(){

        this.getServer().getPluginManager().registerEvents(new PlayerListener(), this);

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
        PlayerData pd = null;
        if(playerDatas.get(p.getName()) != null){
            pd = playerDatas.get(p.getName());
        }else {
            pd = new PlayerData(p.getName(), PlayerData.gameState.tutorial);
            //TODO start Tutorial here
            playerDatas.put(p.getName(), pd);
        }
        p.teleport(pd.spawn);
    }
}
