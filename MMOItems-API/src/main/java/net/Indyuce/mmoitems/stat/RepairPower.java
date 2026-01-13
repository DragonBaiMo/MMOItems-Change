package net.Indyuce.mmoitems.stat;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.Sounds;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.CustomSound;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.interaction.Consumable;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityItem;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityResult;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.api.util.message.Message;
import net.Indyuce.mmoitems.listener.CustomSoundListener;
import net.Indyuce.mmoitems.stat.type.ConsumableItemInteraction;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.util.MMOUtils;
import net.Indyuce.mmoitems.util.RepairUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class RepairPower extends DoubleStat implements ConsumableItemInteraction {
    public RepairPower() {
        super("REPAIR", Material.ANVIL, "Repair Power", new String[]{"The flat amount of durability your item", "can repair when set an item."},
                new String[]{"consumable"});
    }

    public static final String REPAIR_TYPE_TAG_KEY = "MMOITEMS_REPAIR_TYPE";

    @Override
    public boolean handleConsumableEffect(@NotNull InventoryClickEvent event, @NotNull PlayerData playerData, @NotNull Consumable consumable, @NotNull NBTItem target, Type targetType) {
        final int repairPower = (int) consumable.getNBTItem().getStat(ItemStats.REPAIR.getId());
        if (repairPower <= 0) return false;

        RepairResult result = handleRepair(playerData, consumable, target, ignored -> repairPower);
        if (result.success && result.repairedItem != null) {
            // Use event.setCurrentItem() to correctly update the item in inventory
            event.setCurrentItem(result.repairedItem);
        }
        return result.success;
    }

    /**
     * 修复物品的结果封装
     */
    public static final class RepairResult {
        public final boolean success;
        @Nullable
        public final ItemStack repairedItem;

        private RepairResult(boolean success, @Nullable ItemStack repairedItem) {
            this.success = success;
            this.repairedItem = repairedItem;
        }

        public static RepairResult failed() {
            return new RepairResult(false, null);
        }

        public static RepairResult success(@Nullable ItemStack item) {
            return new RepairResult(true, item);
        }

        public static RepairResult vanillaSuccess() {
            return new RepairResult(true, null);
        }
    }

    /**
     * 处理修复逻辑
     *
     * @return 修复结果，包含是否成功以及修复后的物品
     */
    @NotNull
    public static RepairResult handleRepair(@NotNull PlayerData playerData,
                                            @NotNull Consumable consumable,
                                            @NotNull NBTItem target,
                                            @NotNull Function<DurabilityItem, Integer> repairAmountSupplier) {

        // Check repair reference
        final Player player = playerData.getPlayer();
        final @Nullable String repairType1 = consumable.getNBTItem().getString(REPAIR_TYPE_TAG_KEY);
        final @Nullable String repairType2 = target.getString(REPAIR_TYPE_TAG_KEY);
        if (!MMOUtils.checkReference(repairType1, repairType2)) {
            Message.UNABLE_TO_REPAIR.format(ChatColor.RED, "#item#", MMOUtils.getDisplayName(target.getItem())).send(player);
            player.getPlayer().playSound(player.getPlayer().getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 1.5f);
            return RepairResult.failed();
        }

        // Custom durability
        final DurabilityItem durItem = DurabilityItem.from(player, target);
        if (durItem != null) {
            if (durItem.getDurability() >= durItem.getMaxDurability()) return RepairResult.failed();

            final int repairPower = repairAmountSupplier.apply(durItem);
            durItem.addDurability(repairPower);

            // Use the new buildResult() API to get the repaired item
            DurabilityResult result = durItem.buildResult();
            ItemStack repairedItem = result.getItem();

            if (repairedItem != null) {
                Message.REPAIRED_ITEM
                        .format(ChatColor.YELLOW, "#item#", MMOUtils.getDisplayName(target.getItem()), "#amount#", String.valueOf(repairPower))
                        .send(player);
                CustomSoundListener.playSound(consumable.getItem(), CustomSound.ON_CONSUME, player);
                return RepairResult.success(repairedItem);
            }

            return RepairResult.failed();
        }

        // vanilla durability
        boolean vanillaResult = RepairUtils.repairVanillaItem(playerData, target, consumable, repairAmountSupplier.apply(null));
        return vanillaResult ? RepairResult.vanillaSuccess() : RepairResult.failed();
    }
}
