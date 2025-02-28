package gg.botanica.game;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.Material.*;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class PlayerListener implements Listener {
static List<String> startedEvent = new ArrayList<>();
 @EventHandler
 public void onInteract(PlayerInteractEvent event){
     event.setCancelled(true);
     if(event.getAction() != RIGHT_CLICK_BLOCK){
         return;
     }

     Block b = event.getClickedBlock();

     if(b.getType() != GRASS_BLOCK && b.getType() != DIRT && b.getType() != COARSE_DIRT && b.getType() != ROOTED_DIRT){
         //TODO this makes it impossible to water farmland ^
         return;
     }

     Player p = event.getPlayer();
     PlayerData pd = Botanica.playerDatas.get(p.getName());
     //TODO make hoe types
     if(p.getInventory().getItemInMainHand().getType() != STONE_HOE){
         //TODO distinguish seeds (is this already done? idk)
         if(p.getInventory().getItemInMainHand().getType() == BEETROOT_SEEDS){
            pd.plants.add(Plant.plantFromSeed(p, b.getLocation(), p.getInventory().getItemInMainHand()));
         }else if(p.getInventory().getItemInMainHand().getType() == COAL){
             if(pd.waterMaster == null){
                 pd.waterMaster = new WaterMaster();
                 pd.waterMaster.player = event.getPlayer();
             }
             pd.waterMaster.water(event);
         }
         return;
     }
     if(Dirt.dirt.indexOf(b.getType()) <= 0){
         return;
     }
     Material m = Dirt.dirt.get(Dirt.dirt.indexOf(b.getType()) -1);
     b.setType(m);
 }

}
