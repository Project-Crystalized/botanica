package gg.botanica.game;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class WateringCan {
    int filling;
    int max_filling;
    //durability??
    String owner;
    int TaskId;

    public WateringCan(int max_filling, String owner){
        this.max_filling = max_filling;
        this.owner = owner;
    }

    public void waterPlant(PlayerArmSwingEvent event){

    }
}
