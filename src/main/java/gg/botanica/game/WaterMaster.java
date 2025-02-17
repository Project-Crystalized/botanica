package gg.botanica.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import static org.bukkit.Material.CHARCOAL;
import static org.bukkit.Material.COAL;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class WaterMaster implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        //TODO check if player is the owner of the plant
        if(event.getAction() != RIGHT_CLICK_BLOCK){
            return;
        }
        Player p = event.getPlayer();
        //TODO implement CustomModelData
        if(p.getInventory().getItemInMainHand().getType() != COAL){
            return;
        }
        PlayerData pd = Botanica.playerDatas.get(p.getName());
        Location loc = event.getClickedBlock().getLocation();
        //TODO finish this
    }
}
