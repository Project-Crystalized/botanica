package gg.botanica.game;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.Material.CHARCOAL;
import static org.bukkit.Material.COAL;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class WaterMaster implements Listener {
    Integer watering_plant;
    List<String> watering_players = new ArrayList<>();
    Block previous_block;
    Player player;
    public static final int MAX_WATER = 20;

    public WaterMaster(){
        new BukkitRunnable(){
            public void run(){
                if(watering_plant == null || watering_plant <= 0){
                    endWatering();
                    cancel();
                }
                if(player.getTargetBlockExact(5) != previous_block){
                    endWatering();
                    cancel();
                }
                waterPlant();
                watering_plant--;
            }
        }.runTaskTimer(Botanica.getInstance(), 1,1);
    }
    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        //TODO check if player is the owner of the plant
        if(event.getAction() != RIGHT_CLICK_BLOCK){
            endWatering();
            return;
        }
        Player p = event.getPlayer();
        //TODO implement CustomModelData
        if(p.getInventory().getItemInMainHand().getType() != COAL){
            return;
        }

        PlayerData pd = Botanica.playerDatas.get(p.getName());
        Location loc = event.getClickedBlock().getLocation();
        if(event.getClickedBlock() != previous_block){
            endWatering();
            return;
        }
        if(pd.waterMaster == null){
            pd.waterMaster = new WaterMaster();
        }
        pd.waterMaster.previous_block = event.getClickedBlock();
        pd.waterMaster.player = event.getPlayer();
        pd.waterMaster.watering_plant = 7;
    }

    public void endWatering(){
        PlayerData pd = PlayerData.getPlayerData(player.getName());
        pd.waterMaster = null;
        //TODO this might be done ^
    }

    public void waterPlant(){
        PlayerData pd = PlayerData.getPlayerData(player.getName());
        if(player.getTargetBlockExact(5) == null){
            endWatering();
            return;
        }
        Plant plant = pd.findPlant(player.getTargetBlockExact(5).getLocation());
        if(plant == null){
            player.sendMessage("There is no plant here."); //TODO make that message pretty
            endWatering();
            return;
        }
        plant.waterLevel++;
        pd.wateringCan.filling--;
        //TODO this needs more work like checking how much the plant has been watered
    }
}
