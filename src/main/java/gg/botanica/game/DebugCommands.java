package gg.botanica.game;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static org.bukkit.Material.COAL;

public class DebugCommands implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args){
        switch(label){
            case "botanica_start":
                return startCommand(commandSender);
            case "botanica_seed":
                return seedCommand(commandSender, args);
            case "water_can":
                //return waterCanCommand(commandSender);
            default: return false;
        }


    }

    public boolean startCommand(CommandSender sender){
        if(sender instanceof Player){
            Player player = (Player) sender;
            Botanica.startGame(player);
            return true;
        }
        return false;
    }

    public boolean seedCommand(CommandSender sender, String[] args){
        if(args.length == 0){
            return false;
        }
        if(sender instanceof Player){
            for(PlantType t : PlantType.values()){
                if(Objects.equals(t.toString(), args[0])){
                    ((Player)sender).getInventory().addItem(Plant.buildSeed(t));
                    return true;
                }
            }
        }
        return false;
    }
    /*

    public boolean waterCanCommand(CommandSender sender){
        if(sender instanceof Player){
            Player p = (Player) sender;
            ItemStack can = new ItemStack(COAL);
            ItemMeta meta = can.getItemMeta();
            meta.setCustomModelData(21);
            can.setItemMeta(meta);
            p.getInventory().addItem(can);
            //TODO finish this
        }
    }
     */


}
