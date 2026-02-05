package net.Indyuce.mmoitems.inventory.provided;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.util.Lazy;
import io.lumine.mythic.lib.util.Pair;
import net.Indyuce.inventory.MMOInventory;
import net.Indyuce.inventory.api.event.InventoryUpdateEvent;
import net.Indyuce.inventory.inventory.Inventory;
import net.Indyuce.inventory.inventory.slot.CustomSlot;
import net.Indyuce.inventory.player.PlayerData;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.inventory.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static net.Indyuce.mmoitems.inventory.InventoryWatcher.optionalOf;

public class MMOInventorySupplier implements InventorySupplier, Listener {

    /*
     * TODO
     *
     * There's a known issue with this implementation. When MMOInventory is reloaded
     * so that it leaves an existing inventory non existent, items are still registered
     * until a server reload or the player logs out. This is because when previous
     * inventories are flushed, the MMOItems representations of items are not flushed
     */

    @NotNull
    @Override
    public InventoryWatcher supply(@NotNull InventoryResolver resolver) {
        return new Watcher(resolver);
    }

    // 在 MMOInventory 提交更新前，优先尝试绑定；
    // 若该物品需要绑定但绑定未达成，尝试取消放入事件（若事件可取消），以达到“未绑定不得放入”。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void preBindBeforeEquip(InventoryUpdateEvent event) {
        final ItemStack equipped = event.getNewItem();
        if (equipped == null) return;

        final Player player = event.getPlayerData().getPlayer();
        final net.Indyuce.mmoitems.api.player.PlayerData pdata = net.Indyuce.mmoitems.api.player.PlayerData.get(player);
        // 尝试先行绑定（若失败则不改变物品）
        final boolean boundApplied = net.Indyuce.mmoitems.util.AutoBindUtil.applyAutoBindIfNeeded(pdata, equipped);
        if (boundApplied) {
            MMOItems.plugin.getLogger().info(
                    "调试: preBind 触发自动绑定，key=" + guardKey(event) + "，player=" + player.getName());
            FORCE_WRITEBACK.add(guardKey(event));
        }

        final io.lumine.mythic.lib.api.item.NBTItem nbt = io.lumine.mythic.lib.api.item.NBTItem.get(equipped);
        if (net.Indyuce.mmoitems.api.Type.get(nbt) == null) return;

        final net.Indyuce.mmoitems.api.item.mmoitem.MMOItem mmo = new net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem(nbt);
        final boolean needsBind = nbt.getBoolean("MMOITEMS_AUTO_BIND_ON_USE");
        final boolean isBound = mmo.hasData(net.Indyuce.mmoitems.ItemStats.SOULBOUND);

        if (needsBind && !isBound && event instanceof org.bukkit.event.Cancellable) {
            ((org.bukkit.event.Cancellable) event).setCancelled(true);
        }
    }

    private static class Watcher extends InventoryWatcher {
        private final Player player;

        private final Map<Pair<Integer, Integer>, EquippedItem> equipped = new HashMap<>();
        private final Lazy<PlayerData> playerData;

        private Watcher(InventoryResolver resolver) {
            this.player = resolver.getPlayerData().getPlayer();
            this.playerData = Lazy.persistent(() -> MMOInventory.plugin.getDataManager().get(player));
        }

        @Nullable
        public ItemUpdate watchAccessory(Inventory inventory, CustomSlot slot, @NotNull Optional<ItemStack> newItem) {
            final ItemStack stored = playerData.get().get(inventory).getItem(slot);
            final ItemStack stack = newItem.orElse(stored);
            // 若物品仍需要绑定但未绑定，则视作不可装备：
            // - 不进行装备注册（不施加属性/效果）；
            // - 若槽位原本有已注册物品，按移除处理，避免旧物品效果残留。
            final io.lumine.mythic.lib.api.item.NBTItem nbt = io.lumine.mythic.lib.api.item.NBTItem.get(stack);
            boolean blockEquip = false;
            if (net.Indyuce.mmoitems.api.Type.get(nbt) != null) {
                final net.Indyuce.mmoitems.api.item.mmoitem.MMOItem mmo = new net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem(nbt);
                final boolean needsBind = nbt.getBoolean("MMOITEMS_AUTO_BIND_ON_USE");
                final boolean isBound = mmo.hasData(net.Indyuce.mmoitems.ItemStats.SOULBOUND);
                blockEquip = needsBind && !isBound;
            }
            final Pair<Integer, Integer> uniqueMapKey = Pair.of(inventory.getIntegerId(), slot.getIndex());
            ItemUpdate update = blockEquip
                    ? checkForUpdate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR), equipped.get(uniqueMapKey), EquipmentSlot.ACCESSORY, slot.getIndex(), inventory.getIntegerId())
                    : checkForUpdate(stack, equipped.get(uniqueMapKey), EquipmentSlot.ACCESSORY, slot.getIndex(), inventory.getIntegerId());
            if (update != null) equipped.put(uniqueMapKey, update.getNew());
            return update;
        }

        @Override
        public void watchAll(@NotNull Consumer<ItemUpdate> callback) {
            for (Inventory inv : MMOInventory.plugin.getInventoryManager().getAll())
                for (CustomSlot slot : inv.getSlots())
                    if (slot.getType().isCustom())
                        callIfNotNull(watchAccessory(inv, slot, Optional.empty()), callback);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryUpdateEvent event) {
        ItemStack equipped = event.getNewItem();
        net.Indyuce.mmoitems.api.player.PlayerData.get(event.getPlayerData().getPlayer()).getInventory().watch(Watcher.class, watcher -> watcher.watchAccessory(event.getInventory(), event.getSlot(), optionalOf(equipped)));
    }


    // 在监听链末端（HIGHEST）回写：确保若 LOWEST 阶段完成了绑定和 NBT 更新，则把更新后的物品写回 MMOInventory 的真实存储
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void writeBackAfterBind(InventoryUpdateEvent event) {
        final ItemStack equipped = event.getNewItem();
        if (equipped == null) return;

        // 仅处理 MMOItems 物品
        final io.lumine.mythic.lib.api.item.NBTItem nbt = io.lumine.mythic.lib.api.item.NBTItem.get(equipped);
        if (net.Indyuce.mmoitems.api.Type.get(nbt) == null) return;

        // 生成重入键：playerUUID:inventoryId:slotIndex
        final String guardKey = guardKey(event);

        // 若已在本事件链中处理过，则跳过以避免递归
        if (!REENTRANCY_GUARD.add(guardKey)) return;
        try {
            // 仅当存储中的物品与事件中的不一致时回写
            final ItemStack stored = event.getPlayerData().get(event.getInventory()).getItem(event.getSlot());
            if (FORCE_WRITEBACK.remove(guardKey)) {
                MMOItems.plugin.getLogger().info(
                        "调试: writeBack 强制回写，key=" + guardKey);
                event.getPlayerData().get(event.getInventory()).setItem(event.getSlot(), equipped);
            } else {
                if (!areItemsSame(stored, equipped)) {
                    MMOItems.plugin.getLogger().info(
                            "调试: writeBack 差异回写，key=" + guardKey);
                    event.getPlayerData().get(event.getInventory()).setItem(event.getSlot(), equipped);
                }
            }
        } finally {
            REENTRANCY_GUARD.remove(guardKey);
        }
    }


    // 防止在 HIGHEST 阶段通过 setItem 回写时再次触发 InventoryUpdateEvent 而递归
    private static final Set<String> REENTRANCY_GUARD =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 记录需要强制回写的槽位，确保自动绑定后的 NBT 持久化
    private static final Set<String> FORCE_WRITEBACK =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());


    private static String guardKey(InventoryUpdateEvent event) {
        final java.util.UUID playerId = event.getPlayerData().getPlayer().getUniqueId();
        final int invId = event.getInventory().getIntegerId();
        final int slotIdx = event.getSlot().getIndex();
        return playerId + ":" + invId + ":" + slotIdx;
    }

    // 判等：避免不必要的回写触发二次事件
    private static boolean areItemsSame(org.bukkit.inventory.ItemStack a, org.bukkit.inventory.ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        // 数量变化也需要回写
        if (a.getAmount() != b.getAmount()) return false;
        // isSimilar 比较类型与元数据（含大部分可见/可比的 NBT 表现）
        return a.isSimilar(b);
    }
}
