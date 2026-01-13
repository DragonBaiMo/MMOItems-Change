package net.Indyuce.mmoitems.api.interaction.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 耐久度更新辅助工具
 * <p>
 * 提供便捷的静态方法来处理常见的耐久度操作场景，避免调用方重复编写相同逻辑。
 * </p>
 *
 * @author jules
 * @since 7.0
 */
public final class DurabilityUpdateHelper {

    private DurabilityUpdateHelper() {
        // 工具类，禁止实例化
    }

    /**
     * 修复物品
     * <p>
     * 此方法封装了修复物品的完整逻辑，返回修复结果供调用方处理。
     * <b>调用方负责将结果物品放回正确位置</b>（使用 {@link RepairResult#getItem()} 获取物品，
     * 然后手动调用 {@code player.getInventory().setItem(slot, item)} 或
     * {@code event.setCurrentItem(item)}）。
     * </p>
     *
     * @param player 执行修复的玩家
     * @param item   待修复的物品
     * @param amount 修复量
     * @return 修复结果
     */
    @NotNull
    public static RepairResult repair(@NotNull Player player, @NotNull ItemStack item, int amount) {
        DurabilityItem durItem = DurabilityItem.from(player, item);
        if (durItem == null) {
            return RepairResult.notRepairable();
        }
        if (durItem.getDurability() >= durItem.getMaxDurability()) {
            return RepairResult.alreadyFull(item);
        }

        durItem.addDurability(amount);
        DurabilityResult result = durItem.buildResult();

        if (result instanceof DurabilityResult.Updated) {
            DurabilityResult.Updated updated = (DurabilityResult.Updated) result;
            return RepairResult.success(updated.item(), updated.durabilityChange());
        } else if (result instanceof DurabilityResult.NoChange) {
            DurabilityResult.NoChange noChange = (DurabilityResult.NoChange) result;
            return RepairResult.alreadyFull(noChange.item());
        }
        return RepairResult.notRepairable();
    }

    /**
     * 扣除耐久度
     * <p>
     * 此方法封装了扣除耐久度的完整逻辑，返回结果供调用方处理。
     * <b>调用方负责将结果物品放回正确位置</b>，或在物品损坏时进行处理。
     * </p>
     *
     * @param player 持有物品的玩家
     * @param item   物品
     * @param loss   扣除量
     * @return 耐久度变化结果
     */
    @NotNull
    public static DurabilityResult decreaseDurability(@NotNull Player player, @NotNull ItemStack item, int loss) {
        DurabilityItem durItem = DurabilityItem.from(player, item);
        if (durItem == null) {
            return new DurabilityResult.NoChange(item, 0);
        }

        durItem.decreaseDurability(loss);
        return durItem.buildResult();
    }

    /**
     * 修复结果封装
     */
    public static abstract class RepairResult {

        private RepairResult() {}

        /**
         * 修复成功
         */
        public static final class Success extends RepairResult {
            private final ItemStack item;
            private final int amountRepaired;

            private Success(@NotNull ItemStack item, int amountRepaired) {
                this.item = item;
                this.amountRepaired = amountRepaired;
            }

            @NotNull
            public ItemStack item() {
                return item;
            }

            public int amountRepaired() {
                return amountRepaired;
            }
        }

        /**
         * 物品耐久度已满，无需修复
         */
        public static final class AlreadyFull extends RepairResult {
            private final ItemStack item;

            private AlreadyFull(@NotNull ItemStack item) {
                this.item = item;
            }

            @NotNull
            public ItemStack item() {
                return item;
            }
        }

        /**
         * 物品不可修复（不是 MMOItems 物品或没有耐久度系统）
         */
        public static final class NotRepairable extends RepairResult {
            private static final NotRepairable INSTANCE = new NotRepairable();

            private NotRepairable() {}
        }

        // 工厂方法
        public static Success success(@NotNull ItemStack item, int amountRepaired) {
            return new Success(item, amountRepaired);
        }

        public static AlreadyFull alreadyFull(@NotNull ItemStack item) {
            return new AlreadyFull(item);
        }

        public static NotRepairable notRepairable() {
            return NotRepairable.INSTANCE;
        }

        /**
         * 是否有可用的结果物品
         *
         * @return true 如果有物品（Success 或 AlreadyFull）
         */
        public boolean hasItem() {
            return this instanceof Success || this instanceof AlreadyFull;
        }

        /**
         * 获取结果物品
         *
         * @return 结果物品，如果不可修复则返回 null
         */
        @Nullable
        public ItemStack getItem() {
            if (this instanceof Success) return ((Success) this).item();
            if (this instanceof AlreadyFull) return ((AlreadyFull) this).item();
            return null;
        }

        /**
         * 是否修复成功
         *
         * @return true 如果修复成功（Success）
         */
        public boolean isSuccess() {
            return this instanceof Success;
        }
    }
}
