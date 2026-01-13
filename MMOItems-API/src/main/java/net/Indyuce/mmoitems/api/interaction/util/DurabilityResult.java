package net.Indyuce.mmoitems.api.interaction.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 耐久度操作的结果封装
 * <p>
 * 使用抽象类 + 静态内部类设计，调用方根据结果类型决定后续处理。
 * 这种设计确保所有可能的结果都被显式处理，避免静默失败。
 * </p>
 *
 * @author jules
 * @since 7.0
 */
public abstract class DurabilityResult {

    // 私有构造函数，禁止外部继承
    private DurabilityResult() {}

    /**
     * 物品已更新（耐久度发生变化）
     */
    public static final class Updated extends DurabilityResult {
        private final ItemStack item;
        private final int previousDurability;
        private final int currentDurability;

        public Updated(@NotNull ItemStack item, int previousDurability, int currentDurability) {
            this.item = item;
            this.previousDurability = previousDurability;
            this.currentDurability = currentDurability;
        }

        @NotNull
        public ItemStack item() {
            return item;
        }

        public int previousDurability() {
            return previousDurability;
        }

        public int currentDurability() {
            return currentDurability;
        }

        /**
         * @return 耐久度变化量（正值为增加，负值为减少）
         */
        public int durabilityChange() {
            return currentDurability - previousDurability;
        }
    }

    /**
     * 物品已损坏，应从玩家库存中移除
     */
    public static final class Broken extends DurabilityResult {
        private final int previousDurability;

        public Broken(int previousDurability) {
            this.previousDurability = previousDurability;
        }

        public int previousDurability() {
            return previousDurability;
        }
    }

    /**
     * 物品已损坏但通过降级机制修复
     */
    public static final class Downgraded extends DurabilityResult {
        private final ItemStack item;
        private final int previousLevel;
        private final int currentLevel;

        public Downgraded(@NotNull ItemStack item, int previousLevel, int currentLevel) {
            this.item = item;
            this.previousLevel = previousLevel;
            this.currentLevel = currentLevel;
        }

        @NotNull
        public ItemStack item() {
            return item;
        }

        public int previousLevel() {
            return previousLevel;
        }

        public int currentLevel() {
            return currentLevel;
        }
    }

    /**
     * 物品无变化（耐久度未改变）
     */
    public static final class NoChange extends DurabilityResult {
        private final ItemStack item;
        private final int currentDurability;

        public NoChange(@NotNull ItemStack item, int currentDurability) {
            this.item = item;
            this.currentDurability = currentDurability;
        }

        @NotNull
        public ItemStack item() {
            return item;
        }

        public int currentDurability() {
            return currentDurability;
        }
    }

    // ===== 便捷方法 =====

    /**
     * 是否有可用的结果物品
     *
     * @return true 如果物品未损坏（Updated/Downgraded/NoChange），false 如果物品已损坏（Broken）
     */
    public boolean hasItem() {
        return this instanceof Updated || this instanceof Downgraded || this instanceof NoChange;
    }

    /**
     * 获取结果物品
     *
     * @return 结果物品，如果物品已损坏则返回 null
     */
    @Nullable
    public ItemStack getItem() {
        if (this instanceof Updated) return ((Updated) this).item();
        if (this instanceof Downgraded) return ((Downgraded) this).item();
        if (this instanceof NoChange) return ((NoChange) this).item();
        return null;
    }

    /**
     * 将结果物品放入玩家指定槽位
     * <p>
     * 如果物品已损坏（Broken），则会将 null 设置到槽位中（移除物品）。
     * </p>
     *
     * @param player 目标玩家
     * @param slot   目标槽位
     * @return true 如果成功放置了物品，false 如果物品已损坏被移除
     */
    public boolean applyToInventory(@NotNull Player player, @NotNull EquipmentSlot slot) {
        ItemStack item = getItem();
        player.getInventory().setItem(slot, item);
        return item != null;
    }

    /**
     * 检查是否为损坏状态
     *
     * @return true 如果物品已损坏
     */
    public boolean isBroken() {
        return this instanceof Broken;
    }

    /**
     * 检查是否为降级状态
     *
     * @return true 如果物品已降级
     */
    public boolean isDowngraded() {
        return this instanceof Downgraded;
    }
}
