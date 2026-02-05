package net.Indyuce.mmoitems.api.upgrade;

import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 强化操作上下文
 * <p>
 * 封装强化操作所需的所有上下文数据，包括：
 * <ul>
 *     <li>玩家信息</li>
 *     <li>目标物品及其强化数据</li>
 *     <li>强化模式（普通/防护）</li>
 *     <li>各种标志位（免费、强制、直达等级）</li>
 *     <li>成功率系数</li>
 * </ul>
 * </p>
 * <p>
 * 使用 Builder 模式构建实例，确保所有必需参数都被正确设置。
 * </p>
 *
 * @author MMOItems Team
 * @since 强化命令系统
 */
public class UpgradeContext {

    private final Player player;
    private final MMOItem targetItem;
    private final UpgradeData targetData;
    private final ItemStack targetItemStack;
    private final UpgradeMode mode;
    private final double chanceModifier;
    private final boolean freeMode;
    private final boolean forceMode;
    private final int directLevel;
    private final InventoryClickEvent event;

    // ===== 强化石数据 =====
    /**
     * 强化石的 UpgradeData（用于读取 upgradeAmount、upgradeToMax 等配置）
     */
    @Nullable
    private final UpgradeData consumableData;

    // ===== 辅料相关字段 =====
    /**
     * 辅料成功率加成（百分比）
     * <p>
     * 来自幸运石的加成，直接加到成功率上
     * </p>
     */
    private final double auxiliaryChanceBonus;

    /**
     * 辅料惩罚保护（百分比）
     * <p>
     * 来自保护石的加成，降低惩罚触发概率
     * </p>
     */
    private final double auxiliaryProtection;

    /**
     * 直达石触发概率（百分比）
     * <p>
     * 强化成功时触发额外升级的概率
     * </p>
     */
    private final double auxiliaryDirectUpChance;

    /**
     * 直达石跳级数量
     * <p>
     * 直达石触发时额外升级的等级数
     * </p>
     */
    private final int auxiliaryDirectUpLevels;

    /**
     * 私有构造函数，通过 Builder 创建实例
     *
     * @param builder 构建器实例
     */
    private UpgradeContext(Builder builder) {
        this.player = builder.player;
        this.targetItem = builder.targetItem;
        this.targetData = builder.targetData;
        this.targetItemStack = builder.targetItemStack;
        this.mode = builder.mode;
        this.chanceModifier = builder.chanceModifier;
        this.freeMode = builder.freeMode;
        this.forceMode = builder.forceMode;
        this.directLevel = builder.directLevel;
        this.event = builder.event;
        // 强化石数据
        this.consumableData = builder.consumableData;
        // 辅料字段
        this.auxiliaryChanceBonus = builder.auxiliaryChanceBonus;
        this.auxiliaryProtection = builder.auxiliaryProtection;
        this.auxiliaryDirectUpChance = builder.auxiliaryDirectUpChance;
        this.auxiliaryDirectUpLevels = builder.auxiliaryDirectUpLevels;
    }

    /**
     * 获取执行强化操作的玩家
     *
     * @return 玩家实例
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取目标 MMOItem 实例
     * <p>
     * 注意：强化操作可能会修改此实例的数据
     * </p>
     *
     * @return 目标 MMOItem
     */
    @NotNull
    public MMOItem getTargetItem() {
        return targetItem;
    }

    /**
     * 获取目标物品的强化数据
     *
     * @return 强化数据
     */
    @NotNull
    public UpgradeData getTargetData() {
        return targetData;
    }

    /**
     * 获取目标物品的 ItemStack 实例
     * <p>
     * 用于直接修改玩家背包中的物品
     * </p>
     *
     * @return ItemStack 实例，如果是命令模式可能为 null
     */
    @Nullable
    public ItemStack getTargetItemStack() {
        return targetItemStack;
    }

    /**
     * 获取强化模式
     *
     * @return 强化模式枚举
     */
    @NotNull
    public UpgradeMode getMode() {
        return mode;
    }

    /**
     * 检查是否为防护模式
     * <p>
     * 防护模式下，强化失败不会触发任何惩罚
     * </p>
     *
     * @return 如果是防护模式返回 true
     */
    public boolean isProtectMode() {
        return mode == UpgradeMode.PROTECT;
    }

    /**
     * 获取成功率系数
     * <p>
     * 实际成功率 = 基础成功率 × 成功率系数
     * </p>
     *
     * @return 成功率系数，默认为 1.0
     */
    public double getChanceModifier() {
        return chanceModifier;
    }

    /**
     * 检查是否为免费模式
     * <p>
     * 免费模式下，不查找也不消耗强化石
     * </p>
     *
     * @return 如果是免费模式返回 true
     */
    public boolean isFreeMode() {
        return freeMode;
    }

    /**
     * 检查是否为强制模式
     * <p>
     * 强制模式下，可以突破物品配置的等级上限（max）
     * </p>
     *
     * @return 如果是强制模式返回 true
     */
    public boolean isForceMode() {
        return forceMode;
    }

    /**
     * 获取直达等级
     * <p>
     * -direct:XX 参数指定的目标等级
     * </p>
     *
     * @return 直达等级，0 或负数表示未启用直达模式
     */
    public int getDirectLevel() {
        return directLevel;
    }

    /**
     * 检查是否启用了直达模式
     *
     * @return 如果启用了直达模式返回 true
     */
    public boolean isDirectMode() {
        return directLevel > 0;
    }

    /**
     * 获取关联的库存点击事件
     * <p>
     * 背包强化模式下有此事件，命令模式下为 null
     * </p>
     *
     * @return 库存点击事件，可能为 null
     */
    @Nullable
    public InventoryClickEvent getEvent() {
        return event;
    }

    /**
     * 检查是否来自命令调用
     * <p>
     * 命令模式下 event 为 null
     * </p>
     *
     * @return 如果是命令调用返回 true
     */
    public boolean isCommandMode() {
        return event == null;
    }

    /**
     * 获取强化石的 UpgradeData
     * <p>
     * 用于读取强化石配置的 upgradeAmount、upgradeToMax 等属性
     * </p>
     *
     * @return 强化石的 UpgradeData，如果未设置返回 null
     */
    @Nullable
    public UpgradeData getConsumableData() {
        return consumableData;
    }

    /**
     * 计算直达模式需要的强化石数量
     * <p>
     * 数量 = 目标等级 - 当前等级
     * </p>
     *
     * @return 需要的强化石数量，如果不是直达模式返回 1
     */
    public int getRequiredStoneCount() {
        if (!isDirectMode()) {
            return 1;
        }
        int currentLevel = targetData.getLevel();
        return Math.max(1, directLevel - currentLevel);
    }

    // ===== 辅料相关 Getter =====

    /**
     * 获取辅料成功率加成
     * <p>
     * 来自幸运石的百分比加成
     * </p>
     *
     * @return 成功率加成百分比，默认为 0
     */
    public double getAuxiliaryChanceBonus() {
        return auxiliaryChanceBonus;
    }

    /**
     * 获取辅料惩罚保护
     * <p>
     * 来自保护石的惩罚概率降低百分比
     * </p>
     *
     * @return 惩罚保护百分比，默认为 0
     */
    public double getAuxiliaryProtection() {
        return auxiliaryProtection;
    }

    /**
     * 获取直达石触发概率
     *
     * @return 直达石触发概率百分比，默认为 0
     */
    public double getAuxiliaryDirectUpChance() {
        return auxiliaryDirectUpChance;
    }

    /**
     * 获取直达石跳级数量
     *
     * @return 直达石触发时额外升级的等级数，默认为 0
     */
    public int getAuxiliaryDirectUpLevels() {
        return auxiliaryDirectUpLevels;
    }

    /**
     * 检查是否有辅料效果
     *
     * @return 如果有任何辅料效果返回 true
     */
    public boolean hasAuxiliaryEffect() {
        return auxiliaryChanceBonus > 0
                || auxiliaryProtection > 0
                || auxiliaryDirectUpChance > 0;
    }

    /**
     * 上下文构建器
     * <p>
     * 使用示例：
     * <pre>
     * UpgradeContext context = new UpgradeContext.Builder()
     *     .player(player)
     *     .targetItem(mmoItem)
     *     .targetData(upgradeData)
     *     .mode(UpgradeMode.COMMON)
     *     .chanceModifier(1.0)
     *     .build();
     * </pre>
     * </p>
     */
    public static class Builder {
        private Player player;
        private MMOItem targetItem;
        private UpgradeData targetData;
        private ItemStack targetItemStack;
        private UpgradeMode mode = UpgradeMode.COMMON;
        private double chanceModifier = 1.0;
        private boolean freeMode = false;
        private boolean forceMode = false;
        private int directLevel = 0;
        private InventoryClickEvent event;
        // 强化石数据
        private UpgradeData consumableData;
        // 辅料字段
        private double auxiliaryChanceBonus = 0;
        private double auxiliaryProtection = 0;
        private double auxiliaryDirectUpChance = 0;
        private int auxiliaryDirectUpLevels = 0;

        /**
         * 创建新的构建器实例
         */
        public Builder() {
        }

        /**
         * 设置玩家（必需）
         *
         * @param player 玩家实例
         * @return 构建器实例
         */
        public Builder player(@NotNull Player player) {
            this.player = player;
            return this;
        }

        /**
         * 设置目标 MMOItem（必需）
         *
         * @param targetItem MMOItem 实例
         * @return 构建器实例
         */
        public Builder targetItem(@NotNull MMOItem targetItem) {
            this.targetItem = targetItem;
            return this;
        }

        /**
         * 设置目标强化数据（必需）
         *
         * @param targetData 强化数据
         * @return 构建器实例
         */
        public Builder targetData(@NotNull UpgradeData targetData) {
            this.targetData = targetData;
            return this;
        }

        /**
         * 设置目标 ItemStack
         * <p>
         * 用于直接修改玩家背包中的物品
         * </p>
         *
         * @param targetItemStack ItemStack 实例
         * @return 构建器实例
         */
        public Builder targetItemStack(@Nullable ItemStack targetItemStack) {
            this.targetItemStack = targetItemStack;
            return this;
        }

        /**
         * 设置强化模式
         *
         * @param mode 强化模式枚举
         * @return 构建器实例
         */
        public Builder mode(@NotNull UpgradeMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * 设置是否为防护模式（便捷方法）
         *
         * @param protectMode 如果为 true 则使用 PROTECT 模式
         * @return 构建器实例
         */
        public Builder protectMode(boolean protectMode) {
            this.mode = protectMode ? UpgradeMode.PROTECT : UpgradeMode.COMMON;
            return this;
        }

        /**
         * 设置成功率系数
         *
         * @param chanceModifier 成功率系数
         * @return 构建器实例
         */
        public Builder chanceModifier(double chanceModifier) {
            this.chanceModifier = chanceModifier;
            return this;
        }

        /**
         * 设置是否为免费模式
         *
         * @param freeMode 是否免费
         * @return 构建器实例
         */
        public Builder freeMode(boolean freeMode) {
            this.freeMode = freeMode;
            return this;
        }

        /**
         * 设置是否为强制模式
         *
         * @param forceMode 是否强制
         * @return 构建器实例
         */
        public Builder forceMode(boolean forceMode) {
            this.forceMode = forceMode;
            return this;
        }

        /**
         * 设置直达等级
         *
         * @param directLevel 目标等级
         * @return 构建器实例
         */
        public Builder directLevel(int directLevel) {
            this.directLevel = directLevel;
            return this;
        }

        /**
         * 设置关联的库存点击事件
         *
         * @param event 库存点击事件
         * @return 构建器实例
         */
        public Builder event(@Nullable InventoryClickEvent event) {
            this.event = event;
            return this;
        }

        /**
         * 设置强化石的 UpgradeData
         * <p>
         * 用于传递强化石配置的 upgradeAmount、upgradeToMax 等属性
         * </p>
         *
         * @param consumableData 强化石的 UpgradeData
         * @return 构建器实例
         */
        public Builder consumableData(@Nullable UpgradeData consumableData) {
            this.consumableData = consumableData;
            return this;
        }

        // ===== 辅料字段 Setter =====

        /**
         * 设置辅料成功率加成
         *
         * @param bonus 成功率加成百分比
         * @return 构建器实例
         */
        public Builder auxiliaryChanceBonus(double bonus) {
            this.auxiliaryChanceBonus = bonus;
            return this;
        }

        /**
         * 设置辅料惩罚保护
         *
         * @param protection 惩罚概率降低百分比
         * @return 构建器实例
         */
        public Builder auxiliaryProtection(double protection) {
            this.auxiliaryProtection = protection;
            return this;
        }

        /**
         * 设置直达石触发概率
         *
         * @param chance 触发概率百分比
         * @return 构建器实例
         */
        public Builder auxiliaryDirectUpChance(double chance) {
            this.auxiliaryDirectUpChance = chance;
            return this;
        }

        /**
         * 设置直达石跳级数量
         *
         * @param levels 跳级数量
         * @return 构建器实例
         */
        public Builder auxiliaryDirectUpLevels(int levels) {
            this.auxiliaryDirectUpLevels = levels;
            return this;
        }

        /**
         * 构建 UpgradeContext 实例
         * <p>
         * 验证必需参数是否已设置
         * </p>
         *
         * @return 新的 UpgradeContext 实例
         * @throws IllegalStateException 如果缺少必需参数
         */
        public UpgradeContext build() {
            if (player == null) {
                throw new IllegalStateException("玩家（player）是必需参数");
            }
            if (targetItem == null) {
                throw new IllegalStateException("目标物品（targetItem）是必需参数");
            }
            if (targetData == null) {
                throw new IllegalStateException("强化数据（targetData）是必需参数");
            }
            return new UpgradeContext(this);
        }
    }

    @Override
    public String toString() {
        return "UpgradeContext{" +
                "player=" + player.getName() +
                ", targetItem=" + targetItem.getType().getName() +
                ", level=" + targetData.getLevel() +
                ", mode=" + mode +
                ", chanceModifier=" + chanceModifier +
                ", freeMode=" + freeMode +
                ", forceMode=" + forceMode +
                ", directLevel=" + directLevel +
                ", isCommandMode=" + isCommandMode() +
                ", auxiliaryChanceBonus=" + auxiliaryChanceBonus +
                ", auxiliaryProtection=" + auxiliaryProtection +
                ", auxiliaryDirectUpChance=" + auxiliaryDirectUpChance +
                ", auxiliaryDirectUpLevels=" + auxiliaryDirectUpLevels +
                '}';
    }
}
