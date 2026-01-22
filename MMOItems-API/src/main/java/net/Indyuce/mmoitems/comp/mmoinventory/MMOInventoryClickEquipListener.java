package net.Indyuce.mmoitems.comp.mmoinventory;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.item.NBTItem;

import net.Indyuce.inventory.MMOInventory;
import net.Indyuce.inventory.api.event.ItemEquipEvent;
import net.Indyuce.inventory.inventory.CustomInventory;
import net.Indyuce.inventory.inventory.slot.CustomSlot;
import net.Indyuce.inventory.inventory.slot.SlotType;
import net.Indyuce.inventory.player.CustomInventoryData;
import net.Indyuce.inventory.player.PlayerData;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.logging.Level;

/**
 * 监听玩家在背包中左键点击MMOItems饰品，自动装备到MMOInventory的饰品槽。
 * <p>
 * 功能：
 * 1. 左键点击背包中的ACCESSORY类型物品 → 装备到空饰品槽
 * 2. 如果没有空槽，与第一个可用槽位交换
 * 3. 交换时将原槽位物品返还到背包（满则掉落）
 */
public class MMOInventoryClickEquipListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        // 1) 基础过滤：只处理玩家
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 创造模式和观察者模式不处理（避免物品复制漏洞）
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // 只处理左键点击
        if (event.getClick() != ClickType.LEFT) return;

        Inventory topInventory = event.getView().getTopInventory();

        // MMOInventory 饰品槽点击（鼠标有物品时尝试装入）
        if (clickedInv.equals(topInventory)) {
            if (!isMMOInventoryTopInventory(topInventory)) {
                return;
            }
            handleAccessorySlotClick(event, player);
            return;
        }

        if (event.isCancelled()) return;

        // 只在玩家底部背包触发（避免点箱子等其他界面也触发）
        if (!clickedInv.equals(event.getView().getBottomInventory())) return;

        // 仅在玩家自己的背包视图中触发（排除箱子、工作台、铁砧等GUI）
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CRAFTING && topType != InventoryType.PLAYER) return;

        // 只处理左键单击拾取物品的行为
        if (event.getAction() != InventoryAction.PICKUP_ALL) return;

        // 鼠标上已有物品时不处理
        if (!UtilityMethods.isAir(event.getCursor())) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (UtilityMethods.isAir(clickedItem)) return;

        // MMOInventory 配置：禁止装备叠加物品
        boolean disableStacked;
        try {
            disableStacked = MMOInventory.plugin.getConfig().getBoolean("disable-equiping-stacked-items", true);
        } catch (Exception e) {
            disableStacked = true; // 默认禁止堆叠
        }
        if (disableStacked && clickedItem.getAmount() > 1) return;

        // 2) 判断是否为 MMOItems 的 ACCESSORY 类型，并检查特殊NBT
        NBTItem nbtItem = NBTItem.get(clickedItem);
        if (!isMMOItemsAccessory(nbtItem)) return;

        // 未鉴定物品不可装备
        if (nbtItem.hasTag("MMOITEMS_UNIDENTIFIED_ITEM")) return;

        // 禁用交互的物品不可装备
        if (nbtItem.getBoolean("MMOITEMS_DISABLE_INTERACTION")) return;

        // 检查物品使用限制（灵魂绑定、等级、职业等）
        net.Indyuce.mmoitems.api.player.PlayerData mmoPlayerData =
                net.Indyuce.mmoitems.api.player.PlayerData.get(player);
        if (mmoPlayerData != null && !mmoPlayerData.getRPG().canUse(nbtItem, true, true)) return;

        // 3) 获取 MMOInventory PlayerData
        PlayerData playerData;
        try {
            playerData = (PlayerData) MMOInventory.plugin.getDataManager().get(player);
        } catch (Exception e) {
            MMOItems.plugin.getLogger().log(Level.WARNING,
                    "Failed to get MMOInventory PlayerData for " + player.getName() + ": " + e.getMessage());
            return;
        }
        if (playerData == null) return;

        // 4) 找一个合适的 ACCESSORY 槽位：优先空槽，否则允许交换
        SlotPick pick = findAccessorySlot(playerData, clickedItem);
        if (pick == null) return;

        // 5) 发送 ItemEquipEvent（允许其他插件拦截）
        ItemEquipEvent.EquipAction action = UtilityMethods.isAir(pick.previousItem)
                ? ItemEquipEvent.EquipAction.EQUIP
                : ItemEquipEvent.EquipAction.SWAP_ITEMS;

        ItemEquipEvent equipEvent = new ItemEquipEvent(
                player,
                pick.inventory,
                pick.previousItem,
                clickedItem,
                pick.slot,
                action
        );
        Bukkit.getPluginManager().callEvent(equipEvent);
        if (equipEvent.isCancelled()) {
            // 装备被取消时，同时取消原始事件，物品保持原位
            event.setCancelled(true);
            return;
        }

        // 6) 执行装备操作
        event.setCancelled(true);

        ItemStack toEquip = clickedItem.clone();
        toEquip.setAmount(1);

        // 如果槽位有旧物品，先返还到背包
        if (!UtilityMethods.isAir(pick.previousItem)) {
            // 先清空槽位
            pick.data.setItem(pick.slot, null);

            // 返还旧物品到玩家背包，满则掉落
            ItemStack prev = pick.previousItem.clone();
            Collection<ItemStack> leftovers = player.getInventory().addItem(prev).values();
            for (ItemStack left : leftovers) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // 装备新物品（会自动触发 InventoryUpdateEvent）
        pick.data.setItem(pick.slot, toEquip);

        // 7) 从玩家背包扣除点击的物品
        if (clickedItem.getAmount() <= 1) {
            event.setCurrentItem(null);
        } else {
            clickedItem.setAmount(clickedItem.getAmount() - 1);
            event.setCurrentItem(clickedItem);
        }
    }

    private void handleAccessorySlotClick(InventoryClickEvent event, Player player) {
        ItemStack cursor = event.getCursor();
        if (UtilityMethods.isAir(cursor)) {
            return;
        }

        // 只处理左键放入/交换
        InventoryAction action = event.getAction();
        if (action != InventoryAction.PLACE_ALL && action != InventoryAction.PLACE_ONE
                && action != InventoryAction.PLACE_SOME && action != InventoryAction.SWAP_WITH_CURSOR) {
            return;
        }

        // MMOInventory 配置：禁止装备叠加物品
        boolean disableStacked;
        try {
            disableStacked = MMOInventory.plugin.getConfig().getBoolean("disable-equiping-stacked-items", true);
        } catch (Exception e) {
            disableStacked = true;
        }
        if (disableStacked && cursor.getAmount() > 1) {
            return;
        }

        NBTItem nbtItem = NBTItem.get(cursor);
        if (!isMMOItemsAccessory(nbtItem)) {
            return;
        }
        if (nbtItem.hasTag("MMOITEMS_UNIDENTIFIED_ITEM")) {
            return;
        }
        if (nbtItem.getBoolean("MMOITEMS_DISABLE_INTERACTION")) {
            return;
        }

        net.Indyuce.mmoitems.api.player.PlayerData mmoPlayerData =
                net.Indyuce.mmoitems.api.player.PlayerData.get(player);
        if (mmoPlayerData != null && !mmoPlayerData.getRPG().canUse(nbtItem, true, true)) {
            return;
        }

        PlayerData playerData;
        try {
            playerData = (PlayerData) MMOInventory.plugin.getDataManager().get(player);
        } catch (Exception e) {
            MMOItems.plugin.getLogger().log(Level.WARNING,
                    "Failed to get MMOInventory PlayerData for " + player.getName() + ": " + e.getMessage());
            return;
        }
        if (playerData == null) return;

        SlotPick pick = findAccessorySlotByIndex(playerData, event.getSlot(), cursor);
        if (pick == null) {
            return;
        }

        if (!UtilityMethods.isAir(pick.previousItem) && cursor.getAmount() > 1) {
            return;
        }

        ItemEquipEvent.EquipAction equipAction = UtilityMethods.isAir(pick.previousItem)
                ? ItemEquipEvent.EquipAction.EQUIP
                : ItemEquipEvent.EquipAction.SWAP_ITEMS;

        ItemEquipEvent equipEvent = new ItemEquipEvent(
                player,
                pick.inventory,
                pick.previousItem,
                cursor,
                pick.slot,
                equipAction
        );
        Bukkit.getPluginManager().callEvent(equipEvent);
        if (equipEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        ItemStack toEquip = cursor.clone();
        toEquip.setAmount(1);
        pick.data.setItem(pick.slot, toEquip);

        if (UtilityMethods.isAir(pick.previousItem)) {
            if (cursor.getAmount() <= 1) {
                event.setCursor(null);
            } else {
                ItemStack remain = cursor.clone();
                remain.setAmount(cursor.getAmount() - 1);
                event.setCursor(remain);
            }
        } else {
            event.setCursor(pick.previousItem.clone());
        }

    }

    private boolean isMMOInventoryTopInventory(Inventory topInventory) {
        if (topInventory == null) return false;
        Object holder = topInventory.getHolder();
        if (holder == null) return false;
        return holder.getClass().getName().startsWith("net.Indyuce.inventory.");
    }

    /**
     * 判断物品是否为 MMOItems 的饰品类型（ACCESSORY 或 ORNAMENT，包括其子类型）
     */
    private boolean isMMOItemsAccessory(NBTItem nbtItem) {
        Type type = Type.get(nbtItem);
        if (type == null) return false;

        // 获取最顶层父类型，支持自定义子类型（如 RING、NECKLACE 等继承自 ACCESSORY 的类型）
        Type supertype = type.getSupertype();

        // 检查是否为 ACCESSORY 或 ORNAMENT 类型（或其子类型）
        return supertype == Type.ACCESSORY || supertype == Type.ORNAMENT;
    }

    /**
     * 在所有 MMOInventory 自定义背包中查找合适的 ACCESSORY 槽位
     *
     * @return 空槽优先；无空槽则返回第一个可交换槽位；都没有则返回 null
     */
    private SlotPick findAccessorySlot(PlayerData playerData, ItemStack item) {
        Collection<CustomInventory> customInventories = MMOInventory.plugin.getInventoryManager().getAllCustom();
        if (customInventories == null) return null;

        SlotPick firstSwappable = null;

        for (CustomInventory inv : customInventories) {
            CustomInventoryData data = playerData.getCustom(inv);
            if (data == null) continue;

            for (CustomSlot slot : inv.getSlots()) {
                if (slot == null) continue;

                // 只找 ACCESSORY 类型槽位
                if (slot.getType() != SlotType.ACCESSORY) continue;

                // 使用 MMOInventory 自己的兼容性检查
                if (!slot.canHost(data, item)) continue;

                ItemStack existing = data.getItem(slot);
                if (UtilityMethods.isAir(existing)) {
                    // 找到空槽，直接返回
                    return new SlotPick(inv, data, slot, null);
                } else if (firstSwappable == null) {
                    // 记录第一个可交换的槽位
                    firstSwappable = new SlotPick(inv, data, slot, existing);
                }
            }
        }

        return firstSwappable;
    }

    private SlotPick findAccessorySlotByIndex(PlayerData playerData, int slotIndex, ItemStack item) {
        Collection<CustomInventory> customInventories = MMOInventory.plugin.getInventoryManager().getAllCustom();
        if (customInventories == null) return null;

        for (CustomInventory inv : customInventories) {
            CustomInventoryData data = playerData.getCustom(inv);
            if (data == null) continue;

            for (CustomSlot slot : inv.getSlots()) {
                if (slot == null) continue;
                if (slot.getType() != SlotType.ACCESSORY) continue;
                if (slot.getIndex() != slotIndex) continue;

                if (!slot.canHost(data, item)) return null;

                ItemStack existing = data.getItem(slot);
                return new SlotPick(inv, data, slot, existing);
            }
        }

        return null;
    }

    /**
     * 槽位选择结果
     */
    private static final class SlotPick {
        final CustomInventory inventory;
        final CustomInventoryData data;
        final CustomSlot slot;
        final ItemStack previousItem;

        SlotPick(CustomInventory inventory, CustomInventoryData data, CustomSlot slot, ItemStack previousItem) {
            this.inventory = inventory;
            this.data = data;
            this.slot = slot;
            this.previousItem = previousItem;
        }
    }
}
