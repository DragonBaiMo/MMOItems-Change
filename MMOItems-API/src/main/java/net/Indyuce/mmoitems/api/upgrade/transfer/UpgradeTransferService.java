package net.Indyuce.mmoitems.api.upgrade.transfer;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 强化等级转移服务
 * <p>
 * 提供物品之间强化等级转移的核心逻辑：
 * <ul>
 *     <li>源物品与目标物品的类型校验</li>
 *     <li>转移比例计算（默认 80%）</li>
 *     <li>转移石消耗</li>
 *     <li>等级转移执行</li>
 * </ul>
 * </p>
 *
 * @author MMOItems Team
 * @since 强化系统扩展
 */
public class UpgradeTransferService {

    /**
     * 默认转移比例（80%）
     */
    public static final double DEFAULT_TRANSFER_RATIO = 0.8;

    /**
     * 转移模式枚举
     */
    public enum TransferMode {
        /** 仅同类型物品可转移 */
        STRICT,
        /** 同类型/同父类型/父子类型可转移（默认） */
        LOOSE,
        /** 使用白名单配置 */
        WHITELIST
    }

    /**
     * 检查强化转移功能是否启用
     *
     * @return 如果启用返回 true
     */
    public static boolean isTransferEnabled() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        return config == null || config.getBoolean("enabled", true);
    }

    /**
     * 从配置获取转移模式
     *
     * @return 转移模式
     */
    @NotNull
    public static TransferMode getTransferMode() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        if (config == null) {
            return TransferMode.LOOSE;
        }
        String modeStr = config.getString("mode", "loose").toUpperCase(Locale.ROOT);
        try {
            return TransferMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            return TransferMode.LOOSE;
        }
    }

    /**
     * 从配置获取转移比例
     *
     * @return 转移比例（0-1）
     */
    public static double getConfiguredRatio() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        if (config == null) {
            return DEFAULT_TRANSFER_RATIO;
        }
        double ratio = config.getDouble("ratio", DEFAULT_TRANSFER_RATIO);
        return Math.max(0, Math.min(1, ratio));
    }

    /**
     * 从配置获取最大可转移等级
     *
     * @return 最大等级，-1 表示无限制
     */
    public static int getMaxTransferLevel() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        if (config == null) {
            return -1;
        }
        return config.getInt("max-level", -1);
    }

    /**
     * 检查是否为叠加模式
     *
     * @return 如果叠加返回 true，覆盖返回 false
     */
    public static boolean isStackMode() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        return config == null || config.getBoolean("stack-mode", true);
    }

    /**
     * 检查转移后是否重置源物品等级
     *
     * @return 如果重置返回 true
     */
    public static boolean shouldResetSource() {
        ConfigurationSection config = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer");
        return config == null || config.getBoolean("reset-source", true);
    }

    /**
     * 执行强化等级转移
     * <p>
     * 规则：
     * <ol>
     *     <li>源物品与目标物品必须同类型或配置允许的兼容类型</li>
     *     <li>源物品强化等级 > 0</li>
     *     <li>目标等级 = 源等级 × 转移比例（向下取整）</li>
     *     <li>源物品等级重置为 0（可配置）</li>
     * </ol>
     * </p>
     *
     * @param player       执行转移的玩家
     * @param sourceItem   源物品
     * @param targetItem   目标物品
     * @param freeMode     是否免费模式（不消耗转移石）
     * @param transferRatio 转移比例（0-1），传 0 使用配置值
     * @return 转移结果
     */
    @NotNull
    public static TransferResult performTransfer(@NotNull Player player,
                                                  @NotNull ItemStack sourceItem,
                                                  @NotNull ItemStack targetItem,
                                                  boolean freeMode,
                                                  double transferRatio) {
        // 检查功能是否启用
        if (!isTransferEnabled()) {
            return TransferResult.error("强化转移功能已禁用");
        }

        // 使用配置比例
        if (transferRatio <= 0) {
            transferRatio = getConfiguredRatio();
        }

        // 1. 验证源物品
        if (sourceItem.getType() == Material.AIR) {
            return TransferResult.error("源物品为空");
        }
        NBTItem sourceNBT = NBTItem.get(sourceItem);
        if (!sourceNBT.hasType()) {
            return TransferResult.error("源物品不是 MMOItems 物品");
        }
        if (!sourceNBT.hasTag(ItemStats.UPGRADE.getNBTPath())) {
            return TransferResult.error("源物品没有强化属性");
        }

        // 2. 验证目标物品
        if (targetItem.getType() == Material.AIR) {
            return TransferResult.error("目标物品为空");
        }
        NBTItem targetNBT = NBTItem.get(targetItem);
        if (!targetNBT.hasType()) {
            return TransferResult.error("目标物品不是 MMOItems 物品");
        }
        if (!targetNBT.hasTag(ItemStats.UPGRADE.getNBTPath())) {
            return TransferResult.error("目标物品没有强化属性");
        }

        // 3. 类型兼容性检查
        Type sourceType = Type.get(sourceNBT);
        Type targetType = Type.get(targetNBT);
        if (sourceType == null || targetType == null) {
            return TransferResult.error("无法识别物品类型");
        }

        // 默认要求同类型，后续可以配置化允许兼容类型
        if (!isTypeCompatible(sourceType, targetType)) {
            return TransferResult.error("源物品与目标物品类型不兼容 (" + sourceType.getName() + " → " + targetType.getName() + ")");
        }

        // 4. 读取源物品强化数据
        MMOItem sourceMMO = new LiveMMOItem(sourceNBT);
        if (!sourceMMO.hasData(ItemStats.UPGRADE)) {
            return TransferResult.error("无法读取源物品强化数据");
        }
        UpgradeData sourceData = (UpgradeData) sourceMMO.getData(ItemStats.UPGRADE);
        int sourceLevel = sourceData.getLevel();

        if (sourceLevel <= 0) {
            return TransferResult.error("源物品没有可转移的强化等级 (当前等级: +" + sourceLevel + ")");
        }

        // 5. 读取目标物品强化数据
        MMOItem targetMMO = new LiveMMOItem(targetNBT);
        if (!targetMMO.hasData(ItemStats.UPGRADE)) {
            return TransferResult.error("无法读取目标物品强化数据");
        }
        UpgradeData targetData = (UpgradeData) targetMMO.getData(ItemStats.UPGRADE);
        int targetOriginalLevel = targetData.getLevel();

        // 6. 查找并消耗转移石（非免费模式）
        if (!freeMode) {
            ItemStack transferStone = findTransferStone(player);
            if (transferStone == null) {
                return TransferResult.error("背包中没有转移石");
            }
            transferStone.setAmount(transferStone.getAmount() - 1);
        }

        // 7. 计算转移后的等级
        int levelToTransfer = sourceLevel;

        // 检查最大可转移等级限制
        int maxTransfer = getMaxTransferLevel();
        if (maxTransfer > 0 && levelToTransfer > maxTransfer) {
            levelToTransfer = maxTransfer;
        }

        int transferredLevel = (int) Math.floor(levelToTransfer * transferRatio);

        // 根据叠加模式计算目标等级
        int newTargetLevel;
        if (isStackMode()) {
            // 叠加模式：叠加到目标物品现有等级
            newTargetLevel = targetOriginalLevel + transferredLevel;
        } else {
            // 覆盖模式：直接使用转移等级
            newTargetLevel = transferredLevel;
        }

        // 检查目标物品等级上限
        if (targetData.getMax() > 0 && newTargetLevel > targetData.getMax()) {
            newTargetLevel = targetData.getMax();
        }

        // 8. 获取强化模板
        UpgradeTemplate sourceTemplate = sourceData.getTemplate();
        UpgradeTemplate targetTemplate = targetData.getTemplate();
        if (sourceTemplate == null) {
            return TransferResult.error("源物品强化模板不存在: " + sourceData.getTemplateName());
        }
        if (targetTemplate == null) {
            return TransferResult.error("目标物品强化模板不存在: " + targetData.getTemplateName());
        }

        // 9. 执行转移
        // 根据配置决定是否重置源物品等级
        if (shouldResetSource()) {
            sourceTemplate.upgradeTo(sourceMMO, 0);
        }

        // 设置目标物品等级
        if (newTargetLevel > targetOriginalLevel) {
            targetTemplate.upgradeTo(targetMMO, newTargetLevel);
        }

        // 10. 更新 ItemStack
        NBTItem sourceResult = sourceMMO.newBuilder().buildNBT();
        ItemStack builtSource = sourceResult.toItem();
        sourceItem.setType(builtSource.getType());
        sourceItem.setItemMeta(builtSource.getItemMeta());

        NBTItem targetResult = targetMMO.newBuilder().buildNBT();
        ItemStack builtTarget = targetResult.toItem();
        targetItem.setType(builtTarget.getType());
        targetItem.setItemMeta(builtTarget.getItemMeta());

        return TransferResult.success(sourceLevel, targetOriginalLevel, newTargetLevel, sourceMMO, targetMMO);
    }

    /**
     * 检查两个物品类型是否兼容（可以转移）
     * <p>
     * 根据配置的转移模式判断：
     * <ul>
     *     <li>STRICT：仅同类型物品可转移</li>
     *     <li>LOOSE：同类型/同父类型/父子类型可转移</li>
     *     <li>WHITELIST：使用白名单配置判定</li>
     * </ul>
     * </p>
     *
     * @param source 源类型
     * @param target 目标类型
     * @return 是否兼容
     */
    public static boolean isTypeCompatible(@NotNull Type source, @NotNull Type target) {
        TransferMode mode = getTransferMode();

        // WHITELIST 模式：使用白名单配置
        if (mode == TransferMode.WHITELIST) {
            ConfigurationSection compat = MMOItems.plugin.getConfig().getConfigurationSection("item-upgrading.transfer-compatibility");
            if (compat != null && compat.getBoolean("enabled", false)) {
                List<String> whitelist = compat.getStringList("whitelist");
                if (isWhitelisted(whitelist, source, target)) {
                    return true;
                }
                // 白名单模式下未命中，检查是否允许回退
                if (compat.getBoolean("allow-legacy-auto", true)) {
                    // 回退到 LOOSE 模式
                    return checkLooseCompatibility(source, target);
                }
                return false;
            }
            // 白名单配置不存在，回退到 LOOSE
            return checkLooseCompatibility(source, target);
        }

        // STRICT 模式：仅同类型
        if (mode == TransferMode.STRICT) {
            return source.equals(target);
        }

        // LOOSE 模式（默认）：同类型/同父类型/父子类型
        return checkLooseCompatibility(source, target);
    }

    /**
     * LOOSE 模式兼容性检查
     *
     * @param source 源类型
     * @param target 目标类型
     * @return 是否兼容
     */
    private static boolean checkLooseCompatibility(@NotNull Type source, @NotNull Type target) {
        // 同类型
        if (source.equals(target)) {
            return true;
        }

        // 同父类型
        Type sourceParent = source.getParent();
        Type targetParent = target.getParent();

        if (sourceParent != null && targetParent != null && sourceParent.equals(targetParent)) {
            return true;
        }
        // 父子类型兼容
        if (sourceParent != null && sourceParent.equals(target)) {
            return true;
        }
        if (targetParent != null && targetParent.equals(source)) {
            return true;
        }

        return false;
    }

    private static boolean isWhitelisted(@NotNull List<String> whitelist, @NotNull Type source, @NotNull Type target) {
        if (whitelist.isEmpty()) {
            return false;
        }
        String sourceId = source.getId().toUpperCase(Locale.ROOT);
        String targetId = target.getId().toUpperCase(Locale.ROOT);

        for (String entry : whitelist) {
            if (entry == null || entry.isEmpty()) continue;
            String normalized = entry.replace(" ", "").toUpperCase(Locale.ROOT);
            String[] parts = normalized.split("->");
            if (parts.length != 2) {
                parts = normalized.split(":");
            }
            if (parts.length != 2) continue;
            String left = parts[0];
            String right = parts[1];

            boolean leftMatch = left.equals("ANY") || left.equals(sourceId);
            boolean rightMatch = right.equals("ANY") || right.equals(targetId);
            if (leftMatch && rightMatch) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从玩家背包中查找转移石
     * <p>
     * 转移石是带有 TRANSFER_STONE NBT 标签的消耗品
     * </p>
     *
     * @param player 玩家
     * @return 找到的转移石，未找到返回 null
     */
    @Nullable
    public static ItemStack findTransferStone(@NotNull Player player) {
        String nbtPath = ItemStats.TRANSFER_STONE.getNBTPath();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            NBTItem nbt = NBTItem.get(item);
            // 检查是否是消耗品类型
            Type type = Type.get(nbt);
            if (type == null || !type.corresponds(Type.CONSUMABLE)) continue;

            // 检查是否有转移石标签
            if (nbt.hasTag(nbtPath) && nbt.getBoolean(nbtPath)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取玩家背包中所有转移石
     *
     * @param player 玩家
     * @return 转移石列表
     */
    @NotNull
    public static List<ItemStack> findAllTransferStones(@NotNull Player player) {
        String nbtPath = ItemStats.TRANSFER_STONE.getNBTPath();
        List<ItemStack> stones = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            NBTItem nbt = NBTItem.get(item);
            Type type = Type.get(nbt);
            if (type == null || !type.corresponds(Type.CONSUMABLE)) continue;

            if (nbt.hasTag(nbtPath) && nbt.getBoolean(nbtPath)) {
                stones.add(item);
            }
        }
        return stones;
    }
}
