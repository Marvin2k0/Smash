package de.marvin2k0.smash.item.items;

import de.marvin2k0.smash.game.GamePlayer;
import de.marvin2k0.smash.item.SmashItem;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

public class IronSword extends SmashItem
{
    public IronSword()
    {
        super(new ItemStack(Material.IRON_SWORD));
    }

    @Override
    public void onUse(GamePlayer player, Action action)
    {
    }
}
