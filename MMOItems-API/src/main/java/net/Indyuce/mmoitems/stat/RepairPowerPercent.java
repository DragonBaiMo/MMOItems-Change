package net.Indyuce.mmoitems.stat;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.interaction.Consumable;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.type.ConsumableItemInteraction;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RepairPowerPercent extends DoubleStat implements ConsumableItemInteraction {
    public RepairPowerPercent() {
        super("REPAIR_PERCENT", Material.DAMAGED_ANVIL, "Repair Percentage",
                new String[]{"The percentage of total durability getting", "repaired when dragged onto another item."},
                new String[]{"consumable"});
    }


    @Override
    public boolean handleConsumableEffect(@NotNull InventoryClickEvent event, @NotNull PlayerData playerData, @NotNull Consumable consumable, @NotNull NBTItem target, @Nullable Type targetType) {
        final double repairPower = consumable.getNBTItem().getStat(ItemStats.REPAIR_PERCENT.getId());
        if (repairPower <= 0)
            return false;

        RepairPower.RepairResult result = RepairPower.handleRepair(playerData, consumable, target, durItem -> {
            // For vanilla items, use the item's max durability from Material
            if (durItem == null) {
                int vanillaMaxDurability = target.getItem().getType().getMaxDurability();
                return (int) (repairPower * vanillaMaxDurability / 100);
            }
            final double maxDurability = durItem.getMaxDurability();
            return (int) (repairPower * maxDurability / 100);
        });

        // Use event.setCurrentItem() to correctly update the item in inventory
        if (result.success && result.repairedItem != null) {
            event.setCurrentItem(result.repairedItem);
        }
        return result.success;
    }
}
