package net.Indyuce.mmoitems.command.mmoitems.item;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityItem;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityResult;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RepairCommandTreeNode extends CommandTreeNode {
    public RepairCommandTreeNode(CommandTreeNode parent) {
        super(parent, "repair");
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command is only for players.");
            return CommandResult.FAILURE;
        }

        Player player = (Player) sender;

        // Mainhand priority, track which slot the item is in
        ItemStack stack = player.getInventory().getItemInMainHand();
        EquipmentSlot slot = EquipmentSlot.HAND;

        // Try offhand if mainhand is empty
        if (stack == null || stack.getType() == Material.AIR) {
            stack = player.getInventory().getItemInOffHand();
            slot = EquipmentSlot.OFF_HAND;
        }

        // Check if item is empty
        if (stack == null || stack.getType() == Material.AIR) {
            sender.sendMessage(MMOItems.plugin.getPrefix() + "You are not holding any item.");
            return CommandResult.FAILURE;
        }

        // Get DurabilityItem directly to handle repair
        DurabilityItem durItem = DurabilityItem.from(player, stack, slot);
        if (durItem == null) {
            sender.sendMessage(MMOItems.plugin.getPrefix() + "The item you are holding can't be repaired.");
            return CommandResult.FAILURE;
        }

        // Check if already at full durability
        if (durItem.getDurability() >= durItem.getMaxDurability()) {
            sender.sendMessage(MMOItems.plugin.getPrefix() + "The item is already at full durability.");
            return CommandResult.SUCCESS;
        }

        // Repair the item
        durItem.addDurability(durItem.getMaxDurability());
        DurabilityResult result = durItem.buildResult();

        if (result.hasItem()) {
            player.getInventory().setItem(slot, result.getItem());
            sender.sendMessage(MMOItems.plugin.getPrefix() + "Successfully repaired the item you are holding.");
            return CommandResult.SUCCESS;
        } else {
            sender.sendMessage(MMOItems.plugin.getPrefix() + "Failed to repair the item.");
            return CommandResult.FAILURE;
        }
    }
}
