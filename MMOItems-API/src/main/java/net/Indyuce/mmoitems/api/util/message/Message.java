package net.Indyuce.mmoitems.api.util.message;

import io.lumine.mythic.lib.MythicLib;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public enum Message {

    RECEIVED_ITEM("You received &6#item#&e#amount#."),

    // General restrictions
    HANDS_TOO_CHARGED("You can't do anything, your hands are too charged.", "two-handed"),
    SPELL_ON_COOLDOWN("#progress# &eYou must wait #left# second#s# before casting this spell.", "ability-cooldown"),
    ITEM_ON_COOLDOWN("This item is on cooldown! Please wait #left# second#s#.", "item-cooldown"),
    NOT_ENOUGH_PERMS_COMMAND("You don't have enough permissions."),

    // Item restrictions
    NOT_ENOUGH_LEVELS("You don't have enough levels to use this item!", "cant-use-item"),
    SOULBOUND_RESTRICTION("This item is linked to another player, you can't use it!", "cant-use-item"),
    NOT_ENOUGH_PERMS("You don't have enough permissions to use this.", "cant-use-item"),
    WRONG_CLASS("You don't have the right class!", "cant-use-item"),
    NOT_ENOUGH_MANA("You don't have enough mana!", "not-enough-mana"),
    NOT_ENOUGH_STAMINA("You don't have enough stamina!", "not-enough-stamina"),
    NOT_ENOUGH_ATTRIBUTE("You don't have enough #attribute#!", "cant-use-item"),
    NOT_ENOUGH_PROFESSION("You don't have enough levels in #profession#!", "cant-use-item"),
    UNIDENTIFIED_ITEM("You can't use an unidentified item!", "cant-use-item"),

    // Sustom durability
    // ITEM_BROKE("Your #item#&c broke."),
    ZERO_DURABILITY("This item is broken, you first need to repair it.", "item-break"),

    // Repair command
    REPAIR_CMD_SUCCESS("&aSuccessfully repaired the item you are holding."),
    REPAIR_CMD_FULL("&eThe item is already at full durability."),
    REPAIR_CMD_NOT_REPAIRABLE("&cThe item you are holding can't be repaired."),
    REPAIR_CMD_NO_ITEM("&cYou are not holding any item."),
    REPAIR_CMD_FAILED("&cFailed to repair the item."),

    // Consumables & Gem stones
    CANNOT_IDENTIFY_STACKED_ITEMS("You may only identify one item at a time."),
    SUCCESSFULLY_IDENTIFIED("You successfully identified &6#item#&e."),
    SUCCESSFULLY_DECONSTRUCTED("You successfully deconstructed &6#item#&e."),
    GEM_STONE_APPLIED("You successfully applied &6#gem#&e onto your &6#item#&e."),
    GEM_STONE_BROKE("Your gem stone &6#gem#&c broke while trying to apply it onto &6#item#&c."),
    REPAIRED_ITEM("You successfully repaired &6#item#&e for &6#amount# &euses."),
    REPAIR_CONSUMABLE_FULL("&eThis item is already at full durability."),
    REPAIR_CONSUMABLE_NOT_REPAIRABLE("&cThis item can't be repaired."),
    DURABILITY_BROKEN_KEEP("&eYour item is broken but remains in your inventory."),
    DURABILITY_BROKEN_LOST("&cYour item broke and disappeared."),
    DURABILITY_DOWNGRADED("&eYour item was downgraded to level &6#level#&e and fully repaired."),
    SKIN_APPLIED("You successfully applied the skin onto your &6#item#&e!"),
    SKIN_REMOVED("You successfully removed the skin from your &6#item#&e!"),
    SKIN_BROKE("Your skin broke while trying to apply it onto your &6#item#&c."),
    SKIN_REJECTED("A skin has already been applied onto your &6#item#&c!"),
    SKIN_INCOMPATIBLE("This skin is not compatible with your &6#item#&c!"),
    RANDOM_UNSOCKET_GEM_TOO_OLD("The gems have bonded strongly with your item. Cannot remove."),
    RANDOM_UNSOCKET_SUCCESS("&aYou removed &3#gem# &afrom your &6#item#&a!"),

    // Soulbound
    CANT_BIND_ITEM("This item is currently linked to #player# by a Lvl #level# soulbound. You will have to break this soulbound first."),
    NO_SOULBOUND("This item is not bound to anyone."),
    CANT_BIND_STACKED("You can't bind stacked items."),
    UNSUCCESSFUL_SOULBOUND("Your soulbound failed."),
    UNSUCCESSFUL_SOULBOUND_BREAK("You couldn't break the soulbound."),
    LOW_SOULBOUND_LEVEL("This item soulbound is Lvl #level#. You will need a higher soulbound level on your consumable to break this soulbound."),
    SUCCESSFULLY_BIND_ITEM("You successfully applied a Lvl &6#level# &esoulbound to your &6#item#&e."),
    SUCCESSFULLY_BREAK_BIND("You successfully broke the Lvl &6#level# &eitem soulbound!"),
    SOULBOUND_ITEM_LORE("&4Linked to #player#//&4Lvl #level# Soulbound"),

    // Upgrade
    CANT_UPGRADED_STACK("You can't upgrade stacked items."),
    MAX_UPGRADES_HIT("This item cannot be upgraded anymore."),
    UPGRADE_FAIL("Your upgrade failed and you lost your consumable."),
    UPGRADE_FAIL_STATION("Your upgrade failed and you lost your materials."),
    UPGRADE_FAIL_DOWNGRADE("&cUpgrade failed! Your &6#item#&c has been downgraded by #amount# level(s)."),
    UPGRADE_FAIL_BREAK("&cUpgrade failed! Your &6#item#&c has been destroyed!"),
    UPGRADE_FAIL_PROTECTED("&aYour protection scroll saved your &6#item#&a from the upgrade penalty!"),
    WRONG_UPGRADE_REFERENCE("You cannot upgrade this item with this consumable."),
    UPGRADE_SUCCESS("You successfully upgraded your &6#item#&e!"),
    NOT_HAVE_ITEM_UPGRADE("You don't have the item to upgrade!"),
    UPGRADE_REQUIREMENT_SAFE_CHECK("You would not meet the upgraded item requirements."),
    DEATH_DOWNGRADING("&cYour &6#item#&c got severely damaged that fight..."),
    UPGRADE_BACKPACK_DISABLED("&cThis item has backpack upgrading disabled. Please use the upgrade command instead."),
    UPGRADE_NO_STONES("&cYou don't have enough upgrade stones in your inventory. Required: #amount#."),
    UPGRADE_CMD_SUCCESS("&aYou successfully upgraded your &6#item#&a to +#level#!"),
    UPGRADE_CMD_FAIL_PROTECTED("&eUpgrade failed, but your item was protected by protect mode."),
    UPGRADE_CMD_FAIL_NO_PENALTY("&cUpgrade failed."),

    // 强化扩展消息（保底、直达石、每日限制）
    UPGRADE_GUARANTEE_TRIGGERED("&6保底触发！本次强化必定成功！"),
    UPGRADE_DIRECT_UP_TRIGGERED("&d直达石生效！额外升级 #levels# 级！"),
    UPGRADE_DAILY_LIMIT_REACHED("&c今日强化次数已用尽 (#used#/#max#)"),
    UPGRADE_BROADCAST("&6[强化通报] &a#player# 的 &e#item#&a 强化到 &c+#level#&a 级！"),
    UPGRADE_AUTO_BIND("&e你的 &6#item# &e在强化后已自动绑定（等级 #level#）。"),

    // 强化转移消息
    TRANSFER_SUCCESS("&a转移成功！&6#source#&a → &6#target#&a：+#from# → +#to#"),
    TRANSFER_NO_STONE("&c背包中没有转移石。"),
    TRANSFER_INCOMPATIBLE_TYPE("&c源物品与目标物品类型不兼容。"),
    TRANSFER_NO_LEVEL("&c源物品没有可转移的强化等级。"),

    // Crafting stations
    NOT_ENOUGH_MATERIALS("You do not have enough materials to craft this item."),
    CONDITIONS_NOT_MET("You cannot craft this item."),
    CRAFTING_CANCELED("You cancelled a crafting recipe."),
    CRAFTING_QUEUE_FULL("The crafting queue is currently full."),
    UNABLE_TO_REPAIR("This item can't be repaired by this consumable!"),
    ;

    private final String defaultMessage, actionBarConfigPath;

    @NotNull
    private String raw;

    private Message(@NotNull String defaultMessage, @Nullable String actionBarConfigPath) {
        this.defaultMessage = Objects.requireNonNull(defaultMessage);
        this.raw = defaultMessage;
        this.actionBarConfigPath = actionBarConfigPath;
    }

    private Message(String defaultMessage) {
        this(defaultMessage, null);
    }

    public String getDefault() {
        return defaultMessage;
    }

    @NotNull
    public String getFormatted() {
        return MythicLib.plugin.parseColors(raw);
    }

    @NotNull
    public String getRaw() {
        return raw;
    }

    public void setRaw(@NotNull String str) {
        this.raw = Objects.requireNonNull(str);
    }

    public boolean isActionBarMessage() {
        return actionBarConfigPath != null;
    }

    public String getActionBarConfigPath() {
        return actionBarConfigPath;
    }

    @Deprecated
    public String formatRaw(@Nullable ChatColor prefix, String... toReplace) {
        return format(prefix, toReplace).toString();
    }

    public FormattedMessage format(@Nullable ChatColor prefix, String... toReplace) {
        return new FormattedMessage(this).format(prefix, toReplace);
    }
}