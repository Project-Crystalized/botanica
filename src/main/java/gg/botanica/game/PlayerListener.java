package gg.botanica.game;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import static org.bukkit.Material.*;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class PlayerListener implements Listener {

 @EventHandler
 public void onInteract(PlayerInteractEvent event){
     event.setCancelled(true);
     if(event.getAction() != RIGHT_CLICK_BLOCK){
         return;
     }

     Block b = event.getClickedBlock();

     if(b.getType() != GRASS_BLOCK && b.getType() != DIRT && b.getType() != COARSE_DIRT && b.getType() != ROOTED_DIRT){
         return;
     }

     Player p = event.getPlayer();
     //TODO make hoe types
     if(p.getInventory().getItemInMainHand().getType() != STONE_HOE){
         //TODO distinguish seeds
         if(p.getInventory().getItemInMainHand().getType() == BEETROOT_SEEDS){
            Plant.plantFromSeed(p, b.getLocation());
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
