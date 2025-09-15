package gg.crystalized.botanica;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;

public class Botanica extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        System.out.println("Hello world.");
    }

    @Override
    public void onDisable() {

    }

    // Registration helpers
    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommand(String commandString, CommandExecutor executor) {
        PluginCommand command = getCommand(commandString);
        if (command == null) return;
        command.setExecutor(executor);
    }

    // System initialization
    private void initializeModules() {
        // Initialize systems here
    }
}
