package net.Indyuce.mmoitems.api.upgrade;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包扫描工具
 * <p>
 * 单次遍历玩家背包即可收集强化石与保护物品，避免多处重复扫描。
 * 按需收集：达到需求数量会立即停止，减少不必要的 NBT 构造。
 * </p>
 */
public class UpgradeInventoryScanner {
    private final Player player;
    private final String targetReference;
    private final int requiredStones;
    private final List<ItemStack> foundStones;
    private ItemStack protectionItem;

    public UpgradeInventoryScanner(@NotNull Player player, @Nullable String targetReference, int requiredStones) {
        this.player = player;
        this.targetReference = targetReference;
        this.requiredStones = Math.max(1, requiredStones);
        this.foundStones = new ArrayList<>(requiredStones);
    }

    /**
     * 执行扫描。按需收集强化石；如传入 protectKey 则同时尝试找到第一件保护物品。
     *
     * @param protectKey 保护标签，可为空
     */
    public void scan(@Nullable String protectKey) {
        boolean needProtection = protectKey != null && !protectKey.isEmpty();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            // 如果石头已够且保护也找到了，就提前结束
            if (foundStones.size() >= requiredStones && (!needProtection || protectionItem != null)) {
                break;
            }

            NBTItem nbt = NBTItem.get(item);

            // 保护物品扫描：简单字符串匹配，开销低
            if (needProtection && protectionItem == null && nbt.hasTag(ItemStats.UPGRADE_PROTECTION.getNBTPath())) {
                String itemProtectKey = nbt.getString(ItemStats.UPGRADE_PROTECTION.getNBTPath());
                if (protectKey.equals(itemProtectKey)) {
                    protectionItem = item;
                    if (foundStones.size() >= requiredStones) {
                        break;
                    }
                }
            }

            // 强化石扫描：只在还缺石头时进行
            if (foundStones.size() < requiredStones && nbt.hasTag(ItemStats.UPGRADE.getNBTPath())) {
                Type itemType = Type.get(nbt);
                if (itemType == null || !itemType.corresponds(Type.CONSUMABLE)) {
                    continue;
                }
                VolatileMMOItem mmoitem = new VolatileMMOItem(nbt);
                if (!mmoitem.hasData(ItemStats.UPGRADE)) {
                    continue;
                }
                UpgradeData data = (UpgradeData) mmoitem.getData(ItemStats.UPGRADE);
                if (MMOUtils.checkReference(data.getReference(), targetReference)) {
                    foundStones.add(item);
                }
            }
        }
    }

    @NotNull
    public List<ItemStack> getFoundStones() {
        return foundStones;
    }

    @Nullable
    public ItemStack getProtectionItem() {
        return protectionItem;
    }
}
