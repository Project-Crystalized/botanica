package gg.crystalized.botanica.Interactions;

import gg.crystalized.botanica.PlantSim.Domain.PlantInstance;
import gg.crystalized.botanica.Soil.Domain.SoilInstance;
import gg.crystalized.botanica.World.BlockPos;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Context for player interactions with the plant simulation.
 * Holds all relevant data about the interaction.
 */
public class InteractionContext {
    public final Player player;
    public final ItemStack item;
    public final Block clickedBlock;
    public final BlockPos clickedPos;
    public final Location location;
    
    // Simulation state (resolved during interaction)
    public SoilInstance soil;
    public PlantInstance plant;
    
    public InteractionContext(Player player, ItemStack item, Block clickedBlock, BlockPos clickedPos, Location location) {
        this.player = player;
        this.item = item;
        this.clickedBlock = clickedBlock;
        this.clickedPos = clickedPos;
        this.location = location;
    }
}

