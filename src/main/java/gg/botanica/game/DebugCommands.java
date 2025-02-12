package gg.botanica.game;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DebugCommands implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args){
        switch(label){
            case "botanica_start":
                return startCommand(commandSender);
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


}
