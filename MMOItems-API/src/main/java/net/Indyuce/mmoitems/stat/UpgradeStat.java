package net.Indyuce.mmoitems.stat;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.item.SupportedNBTTagValues;
import io.lumine.mythic.lib.api.util.AltChar;
import io.lumine.mythic.lib.gson.JsonParser;
import io.lumine.mythic.lib.gson.JsonSyntaxException;
import io.lumine.mythic.lib.version.Sounds;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.event.item.UpgradeItemEvent;
import net.Indyuce.mmoitems.api.interaction.Consumable;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.api.util.message.Message;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.gui.edition.UpgradingEdition;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.stat.type.ConsumableItemInteraction;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.util.MMOUtils;
import io.lumine.mythic.lib.util.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class UpgradeStat extends ItemStat<UpgradeData, UpgradeData> implements ConsumableItemInteraction {
	private static final Random RANDOM = new Random();

	/**
	 * 尝试从玩家背包中消耗指定保护标签的保护物品
	 *
	 * @param player 玩家
	 * @param protectKey 保护标签
	 * @return 是否成功找到并消耗保护物品
	 */
	private boolean tryConsumeProtection(@NotNull Player player, @Nullable String protectKey) {
		if (protectKey == null || protectKey.isEmpty()) return false;

		for (ItemStack item : player.getInventory().getContents()) {
			if (item == null || item.getType() == Material.AIR) continue;

			NBTItem nbt = NBTItem.get(item);
			if (!nbt.hasTag(ItemStats.UPGRADE_PROTECTION.getNBTPath())) continue;

			String itemProtectKey = nbt.getString(ItemStats.UPGRADE_PROTECTION.getNBTPath());
			if (protectKey.equals(itemProtectKey)) {
				// 消耗一个保护物品
				item.setAmount(item.getAmount() - 1);
				return true;
			}
		}
		return false;
	}

	public UpgradeStat() {
        super("UPGRADE", Material.FLINT, "Item Upgrading",
                new String[]{"Upgrading your item improves its", "current stats. It requires either a", "consumable or a specific crafting ",
                        "station. Upgrading may sometimes &cfail&7..."},
                new String[]{"equipment"});
    }

	@Override
	public UpgradeData whenInitialized(Object object) {
		Validate.isTrue(object instanceof ConfigurationSection, "Must specify a config section");
		return new UpgradeData((ConfigurationSection) object);
	}

	@Override
	public void whenApplied(@NotNull ItemStackBuilder item, @NotNull UpgradeData data) {
		if (!(data instanceof UpgradeData)) { return; }

		// Show in lore
		item.addItemTag(getAppliedNBT(data));

		// Special placeholder
		item.getLore().registerPlaceholder("upgrade_level", String.valueOf(data.getLevel()));

		// Show in lore
		if (data.getMaxUpgrades() > 0)
			item.getLore().insert(getPath(),
					getGeneralStatFormat().replace("{value}", String.valueOf(data.getMaxUpgrades())));
	}

	@NotNull
	@Override
	public ArrayList<ItemTag> getAppliedNBT(@NotNull UpgradeData data) {
		ArrayList<ItemTag> ret = new ArrayList<>();
		ret.add(new ItemTag(getNBTPath(), data.toString()));
		return ret;
	}

	@Override
	public void whenClicked(@NotNull EditionInventory inv, @NotNull InventoryClickEvent event) {
		if (event.getAction() == InventoryAction.PICKUP_ALL)
			new UpgradingEdition(inv.getNavigator(), inv.getEdited()).open(inv);

		if (event.getAction() == InventoryAction.PICKUP_HALF && inv.getEditedSection().contains("upgrade")) {
			inv.getEditedSection().set("upgrade", null);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "Successfully reset the upgrading setup.");
		}
	}

	@Override
	public void whenInput(@NotNull EditionInventory inv, @NotNull String message, Object... info) {

		if (info[0].equals("ref")) {
			inv.getEditedSection().set("upgrade.reference", message);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(
					MMOItems.plugin.getPrefix() + "Upgrading reference successfully changed to " + ChatColor.GOLD + message + ChatColor.GRAY + ".");
			return;
		}

		if (info[0].equals("max")) {
			int i = Integer.parseInt(message);
			inv.getEditedSection().set("upgrade.max", i);
			inv.registerTemplateEdition();
			inv.getPlayer()
					.sendMessage(MMOItems.plugin.getPrefix() + "Max upgrades successfully set to " + ChatColor.GOLD + i + ChatColor.GRAY + ".");
			return;
		}

		if (info[0].equals("min")) {
			int i = Integer.parseInt(message);
			inv.getEditedSection().set("upgrade.min", i);
			inv.registerTemplateEdition();
			inv.getPlayer()
					.sendMessage(MMOItems.plugin.getPrefix() + "Min level successfully set to " + ChatColor.GOLD + i + ChatColor.GRAY + ".");
			return;
		}

		if (info[0].equals("rate")) {
			double d = MMOUtils.parseDouble(message);
			inv.getEditedSection().set("upgrade.success", d);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(
					MMOItems.plugin.getPrefix() + "Upgrading rate successfully set to " + ChatColor.GOLD + d + "%" + ChatColor.GRAY + ".");
			return;
		}

		// 衰减系数
		if (info[0].equals("decay-factor")) {
			double factor = MMOUtils.parseDouble(message);
			Validate.isTrue(factor > 0 && factor <= 1.0, "Decay factor must be between 0 and 1.");
			inv.getEditedSection().set("upgrade.decay-factor", factor);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(
					MMOItems.plugin.getPrefix() + "Decay factor set to " + ChatColor.GOLD + factor + ChatColor.GRAY + ".");
			return;
		}

		// 掉级区间
		if (info[0].equals("downgrade-range")) {
			Validate.isTrue(message.contains("-"), "Range format must be 'min-max' (e.g. 5-15).");
			String[] parts = message.split("-");
			int min = Integer.parseInt(parts[0].trim());
			int max = Integer.parseInt(parts[1].trim());
			Validate.isTrue(min >= 0 && max >= min, "Invalid range values.");
			inv.getEditedSection().set("upgrade.downgrade-range", min + "-" + max);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(
					MMOItems.plugin.getPrefix() + "Downgrade range set to " + ChatColor.GOLD + min + "-" + max + ChatColor.GRAY + ".");
			return;
		}

		// 掉级配置（chance,amount,protectKey）
		if (info[0].equals("downgrade-config")) {
			String[] parts = message.split(",");
			if (parts.length >= 1) {
				double chance = MMOUtils.parseDouble(parts[0].trim());
				inv.getEditedSection().set("upgrade.downgrade-chance", chance);
			}
			if (parts.length >= 2) {
				try {
					int amount = Integer.parseInt(parts[1].trim());
					Validate.isTrue(amount > 0, "Downgrade amount must be positive.");
					inv.getEditedSection().set("upgrade.downgrade-amount", amount);
				} catch (NumberFormatException e) {
					inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + ChatColor.RED + "Invalid amount value: " + parts[1].trim());
					return;
				}
			}
			if (parts.length >= 3) {
				String protectKey = parts[2].trim();
				if (!protectKey.isEmpty()) {
					inv.getEditedSection().set("upgrade.downgrade-protect-key", protectKey);
				}
			}
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "Downgrade config updated.");
			return;
		}

		// 碎裂区间
		if (info[0].equals("break-range")) {
			Validate.isTrue(message.contains("-"), "Range format must be 'min-max' (e.g. 10-20).");
			String[] parts = message.split("-");
			int min = Integer.parseInt(parts[0].trim());
			int max = Integer.parseInt(parts[1].trim());
			Validate.isTrue(min >= 0 && max >= min, "Invalid range values.");
			inv.getEditedSection().set("upgrade.break-range", min + "-" + max);
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(
					MMOItems.plugin.getPrefix() + "Break range set to " + ChatColor.GOLD + min + "-" + max + ChatColor.GRAY + ".");
			return;
		}

		// 碎裂配置（chance,protectKey）
		if (info[0].equals("break-config")) {
			String[] parts = message.split(",");
			if (parts.length >= 1) {
				double chance = MMOUtils.parseDouble(parts[0].trim());
				inv.getEditedSection().set("upgrade.break-chance", chance);
			}
			if (parts.length >= 2) {
				String protectKey = parts[1].trim();
				if (!protectKey.isEmpty()) {
					inv.getEditedSection().set("upgrade.break-protect-key", protectKey);
				}
			}
			inv.registerTemplateEdition();
			inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "Break config updated.");
			return;
		}

		// 销毁保护标签（仅标签）
		if (info[0].equals("destroy-protect")) {
			String protectKey = message.trim();
			if (protectKey.isEmpty()) {
				inv.getEditedSection().set("upgrade.destroy-protect-key", null);
				inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "销毁保护标签已清除。");
			} else {
				inv.getEditedSection().set("upgrade.destroy-protect-key", protectKey);
				inv.getPlayer().sendMessage(MMOItems.plugin.getPrefix() + "销毁保护标签已设置为 " + ChatColor.GOLD + protectKey + ChatColor.GRAY + "。");
			}
			inv.registerTemplateEdition();
			return;
		}

		Validate.isTrue(MMOItems.plugin.getUpgrades().hasTemplate(message), "Could not find any upgrade template with ID '" + message + "'.");
		inv.getEditedSection().set("upgrade.template", message);
		inv.registerTemplateEdition();
		inv.getPlayer().sendMessage(
				MMOItems.plugin.getPrefix() + "Upgrading template successfully changed to " + ChatColor.GOLD + message + ChatColor.GRAY + ".");
	}

	@Override
	public void whenLoaded(@NotNull ReadMMOItem mmoitem) {

		// Get Tags
		ArrayList<ItemTag> tags = new ArrayList<>();
		if (mmoitem.getNBT().hasTag(getNBTPath()))
			tags.add(ItemTag.getTagAtPath(getNBTPath(), mmoitem.getNBT(), SupportedNBTTagValues.STRING));
		StatData data = getLoadedNBT(tags);
		if (data != null) { mmoitem.setData(this, data);}
	}

	@Nullable
	@Override
	public UpgradeData getLoadedNBT(@NotNull ArrayList<ItemTag> storedTags) {

		// Gettag
		ItemTag uTag = ItemTag.getTagAtPath(getNBTPath(), storedTags);

		if (uTag != null) {

			try {

				// Cook Upgrade Data
				return new UpgradeData(new JsonParser().parse((String) uTag.getValue()).getAsJsonObject());

			} catch (JsonSyntaxException |IllegalStateException exception) {
				/*
				 * OLD ITEM WHICH MUST BE UPDATED.
				 */
			}
		}

		// Nope
		return null;
	}

	@Override
	public void whenDisplayed(List<String> lore, Optional<UpgradeData> statData) {
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Left click to setup upgrading.");
		lore.add(ChatColor.YELLOW + AltChar.listDash + " Right click to reset.");
	}

	@NotNull
	@Override
	public UpgradeData getClearStatData() { return new UpgradeData(null, null, false, false, 0, 0, 0D); }

	@Override
	public boolean handleConsumableEffect(@NotNull InventoryClickEvent event, @NotNull PlayerData playerData, @NotNull Consumable consumable, @NotNull NBTItem target, Type targetType) {
		VolatileMMOItem mmoitem = consumable.getMMOItem();
		Player player = playerData.getPlayer();

		if (mmoitem.hasData(ItemStats.UPGRADE) && target.hasTag(ItemStats.UPGRADE.getNBTPath())) {
			if (target.getItem().getAmount() > 1) {
				Message.CANT_UPGRADED_STACK.format(ChatColor.RED).send(player);
				player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
				return false;
			}

			MMOItem targetMMO = new LiveMMOItem(target);
			UpgradeData targetSharpening = (UpgradeData) targetMMO.getData(ItemStats.UPGRADE);
			UpgradeTemplate template = targetSharpening.getTemplate();
            if (template == null) return false;

            if (targetSharpening.isWorkbench()) return false;

            // 检查是否禁用了背包强化
            if (targetSharpening.isBackpackDisabled()) {
                Message.UPGRADE_BACKPACK_DISABLED.format(ChatColor.RED).send(player);
                player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
                return false;
            }

            if (!targetSharpening.canLevelUp()) {
                Message.MAX_UPGRADES_HIT.format(ChatColor.RED).send(player);
                player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
				return false;
			}

			UpgradeData consumableSharpening = (UpgradeData) mmoitem.getData(ItemStats.UPGRADE);
			if (!MMOUtils.checkReference(consumableSharpening.getReference(), targetSharpening.getReference())) {
				Message.WRONG_UPGRADE_REFERENCE.format(ChatColor.RED).send(player);
				player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
				return false;
			}

			UpgradeItemEvent called = new UpgradeItemEvent(playerData, mmoitem, targetMMO, consumableSharpening, targetSharpening);
			Bukkit.getPluginManager().callEvent(called);
			if (called.isCancelled())
				return false;

			// 禁止堆叠升级，要求数量为 1
			if (event.getCurrentItem().getAmount() > 1) {
				Message.UPGRADE_FAIL.format(ChatColor.RED, "#item#", MMOUtils.getDisplayName(event.getCurrentItem())).send(player);
				player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
				return true;
			}

			// 保存升级前的等级（用于衰减计算和惩罚判定）
			int originalLevel = targetSharpening.getLevel();

			// 计算实际成功率（消耗品提供基础成功率，目标物品的衰减配置决定如何随等级衰减）
			double actualSuccess = consumableSharpening.getSuccess();
			if (targetSharpening.isDecayEnabled() && targetSharpening.getDecayFactor() < 1.0) {
				actualSuccess *= Math.pow(targetSharpening.getDecayFactor(), originalLevel);
			}

			if (RANDOM.nextDouble() > actualSuccess) {
				// 强化失败 - 按优先级判定惩罚（使用升级前的等级）
				String itemName = MMOUtils.getDisplayName(event.getCurrentItem());
				boolean penaltyApplied = false;

				// 优先级1：碎裂判定
				if (targetSharpening.isInBreakRange(originalLevel) && targetSharpening.getBreakChance() > 0) {
					if (RANDOM.nextDouble() < targetSharpening.getBreakChance()) {
						// 触发碎裂，检查保护
						if (tryConsumeProtection(player, targetSharpening.getBreakProtectKey())) {
							Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
							player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
						} else {
							// 执行碎裂
							event.getCurrentItem().setAmount(0);
							Message.UPGRADE_FAIL_BREAK.format(ChatColor.RED, "#item#", itemName).send(player);
							player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 0.5f);
							return true;
						}
						penaltyApplied = true;
					}
				}

				// 优先级2：掉级判定（仅在碎裂未触发时）
				if (!penaltyApplied && targetSharpening.isInDowngradeRange(originalLevel) && targetSharpening.getDowngradeChance() > 0) {
					if (RANDOM.nextDouble() < targetSharpening.getDowngradeChance()) {
						// 触发掉级，检查保护
						if (tryConsumeProtection(player, targetSharpening.getDowngradeProtectKey())) {
							Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
							player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
						} else {
							// 执行掉级（基于升级前的等级）
							int downgradeAmount = targetSharpening.getDowngradeAmount();
							int newLevel = Math.max(targetSharpening.getMin(), originalLevel - downgradeAmount);
							int actualDowngrade = originalLevel - newLevel;

							if (actualDowngrade > 0) {
								// 获取模板并降级
								UpgradeTemplate downgradeTemplate = targetSharpening.getTemplate();
								if (downgradeTemplate != null) {
									downgradeTemplate.upgradeTo(targetMMO, newLevel);
									NBTItem downgradedResult = targetMMO.newBuilder().buildNBT();
									event.setCurrentItem(downgradedResult.toItem());
								} else {
									// 模板不存在，记录警告
									MMOItems.plugin.getLogger().warning("Upgrade template not found for item " + itemName + ", cannot downgrade.");
								}
								Message.UPGRADE_FAIL_DOWNGRADE.format(ChatColor.RED, "#item#", itemName, "#amount#", String.valueOf(actualDowngrade)).send(player);
								player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1.5f);
							} else {
								// 已经在最低等级，无法掉级
								Message.UPGRADE_FAIL.format(ChatColor.RED).send(player);
								player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1.5f);
							}
							return true;
						}
						penaltyApplied = true;
					}
				}

				// 优先级3：销毁判定（可保护）
				if (!penaltyApplied) {
					if (targetSharpening.destroysOnFail()) {
						if (tryConsumeProtection(player, targetSharpening.getDestroyProtectKey())) {
							Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
							player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
						} else {
							Message.UPGRADE_FAIL.format(ChatColor.RED).send(player);
							event.getCurrentItem().setAmount(0);
							player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 2);
						}
					} else {
						Message.UPGRADE_FAIL.format(ChatColor.RED).send(player);
						player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 2);
					}
				}

				return true;
			}

			// 强化成功 - 执行升级
			if (consumableSharpening.isUpgradeToMax() && targetSharpening.getMax() > 0) {
				// 一次升满模式：直接升到目标物品的满级
				template.upgradeTo(targetMMO, targetSharpening.getMax());
			} else {
				// 计算目标等级
				int currentLevel = targetSharpening.getLevel();
				int amount = consumableSharpening.getUpgradeAmount();
				int targetLevel = currentLevel + amount;

				// 如果有最大等级限制，不超过最大等级
				if (targetSharpening.getMax() > 0 && targetLevel > targetSharpening.getMax()) {
					targetLevel = targetSharpening.getMax();
				}

				// 执行升级
				if (amount == 1) {
					template.upgrade(targetMMO);
				} else {
					template.upgradeTo(targetMMO, targetLevel);
				}
			}
			NBTItem result = targetMMO.newBuilder().buildNBT();

			/*
			 * Safe check, if the specs the item has after upgrade are too high
			 * for the player, then cancel upgrading because the player would
			 * not be able to use it.
			 */
			if (MMOItems.plugin.getLanguage().upgradeRequirementsCheck && !playerData.getRPG().canUse(result, false)) {
				Message.UPGRADE_REQUIREMENT_SAFE_CHECK.format(ChatColor.RED).send(player);
				player.playSound(player.getLocation(), Sounds.ENTITY_VILLAGER_NO, 1, 2);
				return false;
			}

			Message.UPGRADE_SUCCESS.format(ChatColor.YELLOW, "#item#", MMOUtils.getDisplayName(event.getCurrentItem())).send(player);
			event.setCurrentItem(result.toItem());
			player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 2);
			return true;
		}
		return false;
	}
}
