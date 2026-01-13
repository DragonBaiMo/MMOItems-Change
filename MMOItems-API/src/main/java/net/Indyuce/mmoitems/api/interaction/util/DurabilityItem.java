package net.Indyuce.mmoitems.api.interaction.util;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.item.SupportedNBTTagValues;
import io.lumine.mythic.lib.gson.JsonParser;
import io.lumine.mythic.lib.gson.JsonSyntaxException;
import io.lumine.mythic.lib.util.lang3.Validate;
import io.lumine.mythic.lib.version.Sounds;
import io.lumine.mythic.lib.version.VEnchantment;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;

/**
 * Handles durability decreasing logic for both vanilla and MMOItems custom
 * durability systems in an interface friendly way. See inheritors of this
 * class to check how custom and vanilla durability are handled respectively.
 *
 * @author jules
 */
public abstract class DurabilityItem {
    protected final ItemStack item;
    protected final NBTItem nbtItem;
    @Nullable
    protected final Player player;
    @Nullable
    protected final EquipmentSlot slot;
    private final int unbreakingLevel;

    @Nullable
    private ItemStack itemOutput;

    protected static final Random RANDOM = new Random();

    protected DurabilityItem(@Nullable Player player, @NotNull NBTItem nbtItem, @Nullable EquipmentSlot slot) {
        this.nbtItem = nbtItem;
        this.item = nbtItem.getItem();
        this.player = player;
        this.slot = slot;

        this.unbreakingLevel = MMOUtils.getLevel(nbtItem.getItem(), VEnchantment.UNBREAKING.get());
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public NBTItem getNBTItem() {
        return nbtItem;
    }

    @NotNull
    public DurabilityItem addDurability(int gain) {
        Validate.isTrue(itemOutput == null, "Item already generated");

        if (gain > 0) onDurabilityAdd(gain);
        return this;
    }

    @NotNull
    public DurabilityItem decreaseDurability(int loss) {
        Validate.isTrue(itemOutput == null, "Item already generated");

        // This happens when Unbreaking applies for a damageable item
        if (loss == 0) return this;

        /*
         * Calculate the chance of the item not losing any durability because of
         * the vanilla unbreaking enchantment ; an item with unbreaking X has 1
         * 1 chance out of (X + 1) to lose a durability point, that's 50% chance
         * -> 33% chance -> 25% chance -> 20% chance...
         */
        if (rollUnbreaking()) return this;

        // Apply durability decrease
        onDurabilityDecrease(loss);

        return this;
    }

    /**
     * 生成耐久度变化后的物品
     *
     * @return 更新后的物品，如果物品已损坏则返回 null
     * @deprecated 使用 {@link #buildResult()} 替代，提供更丰富的结果信息
     */
    @Deprecated
    @Nullable
    public ItemStack toItem() {

        // Cache result
        if (itemOutput != null) return itemOutput;

        if (isBroken()) {

            // Lost when broken
            if (isLostWhenBroken()) {

                // Play sound when item breaks
                if (player != null) {
                    if (item.getType().getMaxDurability() == 0) player.getWorld().playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1);
                    PlayerData.get(player).getInventory().watchVanillaSlot(io.lumine.mythic.lib.api.player.EquipmentSlot.fromBukkit(slot), Optional.empty());
                }

                return itemOutput = null;
            }

            // Checks for possible downgrade
            if (isDowngradedWhenBroken()) {
                ItemTag uTag = ItemTag.getTagAtPath(ItemStats.UPGRADE.getNBTPath(), getNBTItem(), SupportedNBTTagValues.STRING);
                if (uTag != null) try {
                    UpgradeData data = new UpgradeData(JsonParser.parseString((String) uTag.getValue()).getAsJsonObject());

                    // If it cannot be downgraded (reached min), DEATH
                    if (data.getLevel() <= data.getMin()) return null;

                    // Downgrade and FULLY repair item
                    LiveMMOItem mmo = new LiveMMOItem(getNBTItem());
                    //mmo.setData(ItemStats.CUSTOM_DURABILITY, new DoubleData(maxDurability));
                    mmo.getUpgradeTemplate().upgradeTo(mmo, data.getLevel() - 1);
                    NBTItem nbtItem = mmo.newBuilder().buildNBT();

                    // Fully repair item
                    DurabilityItem item = DurabilityItem.from(player, nbtItem);
                    Validate.notNull(item, "Internal error");
                    item.addDurability(item.getMaxDurability());

                    // Return
                    return itemOutput = item.toItem();

                } catch (JsonSyntaxException | IllegalStateException ignored) {
                    // Nothing
                }
            }
        }

        return itemOutput = applyChanges();
    }

    public abstract boolean isLostWhenBroken();

    private boolean isDowngradedWhenBroken() {
        return nbtItem.getBoolean("MMOITEMS_BREAK_DOWNGRADE");
    }

    @NotNull
    protected abstract ItemStack applyChanges();

    public abstract boolean isBroken();

    public abstract int getDurability();

    public abstract int getMaxDurability();

    /**
     * 获取初始耐久度（创建此 DurabilityItem 实例时的耐久度值）
     *
     * @return 初始耐久度
     */
    public abstract int getInitialDurability();

    public abstract void onDurabilityAdd(int gain);

    public abstract void onDurabilityDecrease(int loss);

    /**
     * 构建耐久度操作结果
     * <p>
     * 此方法将当前耐久度状态转换为 {@link DurabilityResult}，调用方根据结果类型
     * 决定如何处理物品（放回库存、移除、发送消息等）。
     * </p>
     * <p>
     * <b>推荐使用此方法替代 {@link #toItem()} 和 {@link #updateInInventory()}</b>，
     * 因为此方法提供更丰富的结果信息，避免静默失败。
     * </p>
     *
     * @return 操作结果，包含新物品或损坏/降级状态
     * @since 7.0
     */
    @NotNull
    public DurabilityResult buildResult() {
        final int initialDur = getInitialDurability();
        final int currentDur = getDurability();

        // 无变化
        if (currentDur == initialDur) {
            return new DurabilityResult.NoChange(nbtItem.getItem(), currentDur);
        }

        // 已损坏
        if (isBroken()) {
            // 损坏后丢失
            if (isLostWhenBroken()) {
                // 播放破碎音效
                if (player != null && slot != null) {
                    if (item.getType().getMaxDurability() == 0) {
                        player.getWorld().playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1);
                    }
                    PlayerData.get(player).getInventory().watchVanillaSlot(
                            io.lumine.mythic.lib.api.player.EquipmentSlot.fromBukkit(slot), Optional.empty());
                }
                return new DurabilityResult.Broken(initialDur);
            }

            // 检查降级修复
            if (isDowngradedWhenBroken()) {
                ItemTag uTag = ItemTag.getTagAtPath(ItemStats.UPGRADE.getNBTPath(), getNBTItem(), SupportedNBTTagValues.STRING);
                if (uTag != null) {
                    try {
                        UpgradeData data = new UpgradeData(JsonParser.parseString((String) uTag.getValue()).getAsJsonObject());

                        // 无法继续降级（已达最低等级）
                        if (data.getLevel() <= data.getMin()) {
                            return new DurabilityResult.Broken(initialDur);
                        }

                        // 降级并完全修复
                        int previousLevel = data.getLevel();
                        LiveMMOItem mmo = new LiveMMOItem(getNBTItem());
                        mmo.getUpgradeTemplate().upgradeTo(mmo, previousLevel - 1);
                        NBTItem downgradedNbt = mmo.newBuilder().buildNBT();

                        // 完全修复降级后的物品
                        DurabilityItem downgradedItem = DurabilityItem.from(player, downgradedNbt);
                        if (downgradedItem == null) throw new IllegalStateException("Downgraded item is null");
                        downgradedItem.addDurability(downgradedItem.getMaxDurability());
                        ItemStack result = downgradedItem.toItem();
                        Validate.notNull(result, "Internal error");

                        return new DurabilityResult.Downgraded(result, previousLevel, previousLevel - 1);
                    } catch (Exception ignored) {
                        // 降级失败，视为损坏
                    }
                }
            }

            // 默认损坏处理：如果配置了损坏不丢失，则保留物品
            if (!isLostWhenBroken()) {
                ItemStack result = applyChanges();
                return new DurabilityResult.Updated(result, initialDur, getDurability());
            }

            return new DurabilityResult.Broken(initialDur);
        }

        // 正常更新
        ItemStack result = applyChanges();
        return new DurabilityResult.Updated(result, initialDur, currentDur);
    }

    public void updateInInventory(@NotNull PlayerItemDamageEvent event) {
        ItemStack resultingItem = toItem();
        if (resultingItem == null) event.setDamage(BIG_DAMAGE);
        else {
            event.setCancelled(true);
            updateInInventory();
        }
    }

    protected static final int BIG_DAMAGE = 1000000;

    /**
     * 将结果物品放回玩家库存
     * <p>
     * <b>警告：当 slot 为 null 时，此方法只更新 ItemMeta，不会更新 NBT 标签，
     * 导致自定义耐久度修复失效！</b>
     * </p>
     *
     * @return this
     * @deprecated 使用 {@link #buildResult()} 获取结果，然后调用
     * {@link DurabilityResult#applyToInventory(Player, EquipmentSlot)} 或手动更新库存
     */
    @Deprecated
    @NotNull
    public DurabilityItem updateInInventory() {
        ItemStack resultingItem = toItem();

        // No player is provided, just update the item and inshallah
        if (player == null || slot == null) {
            Validate.notNull(resultingItem, "Null item, no slot/player provided");
            this.item.setItemMeta(resultingItem.getItemMeta());
        }

        // Place item
        else {
            player.getInventory().setItem(slot, resultingItem);
        }

        return this;
    }

    private boolean rollUnbreaking() {
        return unbreakingLevel > 0 && RANDOM.nextInt(unbreakingLevel + 1) != 0;
    }

    protected int retrieveMaxVanillaDurability(@NotNull ItemStack item, @NotNull ItemMeta meta) {
        if (MythicLib.plugin.getVersion().isAbove(1, 20, 5) && meta instanceof Damageable && ((Damageable) meta).hasMaxDamage()) {
            int maxDamage = ((Damageable) meta).getMaxDamage();
            if (maxDamage > 0) return maxDamage;
        }
        return item.getType().getMaxDurability();
    }

    @Nullable
    public static DurabilityItem vanilla(@Nullable Player player, @NotNull ItemStack item) {
        try {
            NBTItem nbtItem = NBTItem.get(item);
            Validate.isTrue(!nbtItem.hasTag(ItemStats.MAX_DURABILITY.getNBTPath()), "Custom durability detected");
            return new VanillaDurabilityItem(player, nbtItem, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    public static DurabilityItem custom(@Nullable Player player, @NotNull ItemStack item) {
        return custom(player, null, item);
    }

    @Nullable
    public static DurabilityItem custom(@Nullable Player player, @Nullable EquipmentSlot slot, @NotNull ItemStack item) {
        NBTItem nbtItem = NBTItem.get(item);
        return nbtItem.hasTag(ItemStats.MAX_DURABILITY.getNBTPath()) ? new CustomDurabilityItem(player, nbtItem, slot) : null;
    }

    @Nullable
    public static DurabilityItem from(@Nullable Player player, @NotNull ItemStack item) {
        return from(player, item, null, null);
    }

    @Nullable
    public static DurabilityItem from(@Nullable Player player, @NotNull NBTItem item) {
        return from(player, item.getItem(), item, null);
    }

    @Nullable
    public static DurabilityItem from(@Nullable Player player, @NotNull NBTItem item, @Nullable EquipmentSlot slot) {
        return from(player, item.getItem(), item, slot);
    }

    @Nullable
    public static DurabilityItem from(@Nullable Player player, @NotNull ItemStack item, @Nullable EquipmentSlot slot) {
        return from(player, item, null, slot);
    }

    @Nullable
    public static DurabilityItem from(@Nullable Player player, @NotNull ItemStack item, @Nullable NBTItem nbtItem, @Nullable EquipmentSlot slot) {

        // No durability applied in creative mode
        if (player != null && player.getGameMode() == GameMode.CREATIVE) return null;

        if (nbtItem == null) nbtItem = NBTItem.get(item);

        // Check for custom durability
        if (nbtItem.hasTag(ItemStats.MAX_DURABILITY.getNBTPath()))
            return new CustomDurabilityItem(player, nbtItem, slot);

        // Try vanilla durability item
        try {
            return new VanillaDurabilityItem(player, nbtItem, slot);
        } catch (Exception exception) {
            // No max durability
        }

        return null;
    }

    //region Deprecated

    @Deprecated
    public boolean isValid() {
        return true;
    }

    @Deprecated
    public boolean isBarHidden() {
        return true;
    }

    @Deprecated
    public int getUnbreakingLevel() {
        return unbreakingLevel;
    }

    //endregion
}
