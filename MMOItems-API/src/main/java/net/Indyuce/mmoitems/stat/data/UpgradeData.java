package net.Indyuce.mmoitems.stat.data;

import io.lumine.mythic.lib.gson.JsonObject;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.item.build.MMOItemBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 物品强化数据类，包含以下信息：
 * <p> • 当前强化等级
 * </p> • 使用的强化模板
 * <p> • 强化成功率
 * </p> • 强化失败时的惩罚机制（销毁、掉级、碎裂）
 * <p> • 成功率衰减配置
 * </p> • 保护机制配置
 */
public class UpgradeData implements StatData, RandomStatData<UpgradeData> {

	// ========== 基础配置字段 ==========
	@Nullable private final String reference, template;
	private final boolean workbench, destroy;
	private final double success;
	private final int max;
	private final int min;
	private int level;

	// ========== 成功率衰减配置 ==========
	private final boolean decayEnabled;
	private final double decayFactor;

	// ========== 掉级惩罚配置 ==========
	private final int downgradeRangeMin;
	private final int downgradeRangeMax;
	private final double downgradeChance;
	private final int downgradeAmount;

	// ========== 碎裂惩罚配置 ==========
	private final int breakRangeMin;
	private final int breakRangeMax;
	private final double breakChance;

	// ========== 保护机制配置 ==========
	@Nullable private final String downgradeProtectKey;
	@Nullable private final String breakProtectKey;
	@Nullable private final String destroyProtectKey;

	// ========== 背包强化控制 ==========
	private final boolean disableBackpack;

	// ========== 一次升满配置 ==========
	private final boolean upgradeToMax;

	// ========== 跳级配置 ==========
	private final int upgradeAmount;

	// ========== Getter 方法 ==========

	/**
	 * @return 消耗品必须匹配的强化参考标识
	 */
	@Nullable public String getReference() { return reference; }

	/**
	 * @return 是否仅通过工作台强化
	 */
	public boolean isWorkbench() {
		return workbench;
	}

	/**
	 * @return 强化失败是否销毁物品
	 */
	public boolean isDestroy() {
		return destroy;
	}

	/**
	 * @return 最大强化等级
	 */
	public int getMax() { return max; }

	/**
	 * @return 最小强化等级（降级下限）
	 */
	public int getMin() { return min; }

	/**
	 * @return 是否启用成功率衰减
	 */
	public boolean isDecayEnabled() { return decayEnabled; }

	/**
	 * @return 成功率衰减系数
	 */
	public double getDecayFactor() { return decayFactor; }

	/**
	 * @return 掉级区间最小值
	 */
	public int getDowngradeRangeMin() { return downgradeRangeMin; }

	/**
	 * @return 掉级区间最大值
	 */
	public int getDowngradeRangeMax() { return downgradeRangeMax; }

	/**
	 * @return 掉级触发概率（0-1）
	 */
	public double getDowngradeChance() { return downgradeChance; }

	/**
	 * @return 每次掉级降低的等级数
	 */
	public int getDowngradeAmount() { return downgradeAmount; }

	/**
	 * @return 碎裂区间最小值
	 */
	public int getBreakRangeMin() { return breakRangeMin; }

	/**
	 * @return 碎裂区间最大值
	 */
	public int getBreakRangeMax() { return breakRangeMax; }

	/**
	 * @return 碎裂触发概率（0-1）
	 */
	public double getBreakChance() { return breakChance; }

	/**
	 * @return 掉级保护匹配标签
	 */
	@Nullable public String getDowngradeProtectKey() { return downgradeProtectKey; }

	/**
	 * @return 碎裂保护匹配标签
	 */
	@Nullable public String getBreakProtectKey() { return breakProtectKey; }

	/**
	 * @return 销毁保护匹配标签
	 */
	@Nullable public String getDestroyProtectKey() { return destroyProtectKey; }

	/**
	 * @return 是否禁用背包强化
	 */
	public boolean isBackpackDisabled() { return disableBackpack; }

	/**
	 * @return 是否一次升满
	 */
	public boolean isUpgradeToMax() { return upgradeToMax; }

	/**
	 * @return 每次升级的级数（默认 1）
	 */
	public int getUpgradeAmount() { return upgradeAmount; }

	/**
	 * 创建基础的 UpgradeData（兼容旧版本）
	 *
	 * @param reference 强化参考标识
	 * @param template 强化模板名称
	 * @param workbench 是否仅工作台强化
	 * @param destroy 强化失败是否销毁
	 * @param max 最大强化等级
	 * @param success 成功率（0-1）
	 */
	public UpgradeData(@Nullable String reference, @Nullable String template, boolean workbench, boolean destroy, int max, double success) {
		this(reference, template, workbench, destroy, max, 0, success);
	}

	/**
	 * 创建基础的 UpgradeData（兼容旧版本）
	 *
	 * @param reference 强化参考标识
	 * @param template 强化模板名称
	 * @param workbench 是否仅工作台强化
	 * @param destroy 强化失败是否销毁
	 * @param max 最大强化等级
	 * @param min 最小强化等级
	 * @param success 成功率（0-1）
	 */
	public UpgradeData(@Nullable String reference, @Nullable String template, boolean workbench, boolean destroy, int max, int min, double success) {
		this(reference, template, workbench, destroy, max, min, success,
				false, 1.0,
				-1, -1, 0, 1,
				-1, -1, 0,
				null, null, null, false, false, 1);
	}

	/**
	 * 创建完整的 UpgradeData（包含所有惩罚和保护配置）
	 *
	 * @param reference 强化参考标识
	 * @param template 强化模板名称
	 * @param workbench 是否仅工作台强化
	 * @param destroy 强化失败是否销毁
	 * @param max 最大强化等级
	 * @param min 最小强化等级
	 * @param success 成功率（0-1）
	 * @param decayEnabled 是否启用成功率衰减
	 * @param decayFactor 成功率衰减系数
	 * @param downgradeRangeMin 掉级区间最小值
	 * @param downgradeRangeMax 掉级区间最大值
	 * @param downgradeChance 掉级概率
	 * @param downgradeAmount 每次掉级数量
	 * @param breakRangeMin 碎裂区间最小值
	 * @param breakRangeMax 碎裂区间最大值
	 * @param breakChance 碎裂概率
	 * @param downgradeProtectKey 掉级保护物品标签
	 * @param breakProtectKey 碎裂保护物品标签
	 * @param destroyProtectKey 销毁保护物品标签
	 * @param disableBackpack 是否禁用背包强化
	 * @param upgradeToMax 是否一次升满
	 * @param upgradeAmount 每次升级的级数（默认 1）
	 */
	public UpgradeData(@Nullable String reference, @Nullable String template, boolean workbench, boolean destroy, int max, int min, double success,
					   boolean decayEnabled, double decayFactor,
					   int downgradeRangeMin, int downgradeRangeMax, double downgradeChance, int downgradeAmount,
					   int breakRangeMin, int breakRangeMax, double breakChance,
					   @Nullable String downgradeProtectKey, @Nullable String breakProtectKey, @Nullable String destroyProtectKey,
					   boolean disableBackpack, boolean upgradeToMax, int upgradeAmount) {
		this.reference = reference;
		this.template = template;
		this.workbench = workbench;
		this.destroy = destroy;
		this.max = max;
		this.min = min;
		this.success = success;
		this.decayEnabled = decayEnabled;
		this.decayFactor = decayFactor;
		this.downgradeRangeMin = downgradeRangeMin;
		this.downgradeRangeMax = downgradeRangeMax;
		this.downgradeChance = downgradeChance;
		this.downgradeAmount = downgradeAmount;
		this.breakRangeMin = breakRangeMin;
		this.breakRangeMax = breakRangeMax;
		this.breakChance = breakChance;
		this.downgradeProtectKey = downgradeProtectKey;
		this.breakProtectKey = breakProtectKey;
		this.destroyProtectKey = destroyProtectKey;
		this.disableBackpack = disableBackpack;
		this.upgradeToMax = upgradeToMax;
		this.upgradeAmount = upgradeAmount;
	}

	/**
	 * 从配置节点创建 UpgradeData
	 */
	public UpgradeData(ConfigurationSection section) {
		// 基础配置
		reference = section.getString("reference");
		template = section.getString("template");
		workbench = section.getBoolean("workbench");
		destroy = section.getBoolean("destroy");
		max = section.getInt("max");
		min = section.getInt("min", 0);
		success = section.getDouble("success") / 100;

		// 成功率衰减配置
		decayEnabled = section.getBoolean("decay-enabled", false);
		decayFactor = section.getDouble("decay-factor", 1.0);

		// 掉级惩罚配置（使用临时变量避免 final 字段在 try-catch 中赋值问题）
		int tempDowngradeMin = -1;
		int tempDowngradeMax = -1;
		String downgradeRangeStr = section.getString("downgrade-range");
		if (downgradeRangeStr != null && downgradeRangeStr.contains("-")) {
			try {
				String[] parts = downgradeRangeStr.split("-");
				tempDowngradeMin = Integer.parseInt(parts[0].trim());
				tempDowngradeMax = Integer.parseInt(parts[1].trim());
			} catch (NumberFormatException e) {
				MMOItems.plugin.getLogger().warning("Invalid downgrade-range format: " + downgradeRangeStr + ". Expected format: 'min-max' (e.g. 5-15)");
				tempDowngradeMin = -1;
				tempDowngradeMax = -1;
			}
		}
		downgradeRangeMin = tempDowngradeMin;
		downgradeRangeMax = tempDowngradeMax;
		downgradeChance = section.getDouble("downgrade-chance", 0) / 100.0;
		downgradeAmount = section.getInt("downgrade-amount", 1);

		// 碎裂惩罚配置（使用临时变量避免 final 字段在 try-catch 中赋值问题）
		int tempBreakMin = -1;
		int tempBreakMax = -1;
		String breakRangeStr = section.getString("break-range");
		if (breakRangeStr != null && breakRangeStr.contains("-")) {
			try {
				String[] parts = breakRangeStr.split("-");
				tempBreakMin = Integer.parseInt(parts[0].trim());
				tempBreakMax = Integer.parseInt(parts[1].trim());
			} catch (NumberFormatException e) {
				MMOItems.plugin.getLogger().warning("Invalid break-range format: " + breakRangeStr + ". Expected format: 'min-max' (e.g. 10-20)");
				tempBreakMin = -1;
				tempBreakMax = -1;
			}
		}
		breakRangeMin = tempBreakMin;
		breakRangeMax = tempBreakMax;
		breakChance = section.getDouble("break-chance", 0) / 100.0;

		// 保护配置
		downgradeProtectKey = section.getString("downgrade-protect-key");
		breakProtectKey = section.getString("break-protect-key");
		destroyProtectKey = section.getString("destroy-protect-key");

		// 背包强化控制（默认 false，向后兼容）
		disableBackpack = section.getBoolean("disable-backpack", false);

		// 一次升满配置（默认 false，向后兼容）
		upgradeToMax = section.getBoolean("upgrade-to-max", false);

		// 跳级配置（默认 1，向后兼容）
		upgradeAmount = section.getInt("upgrade-amount", 1);
	}

	/**
	 * 从 JSON 对象创建 UpgradeData
	 */
	public UpgradeData(JsonObject object) {
		// 基础配置
		workbench = object.get("Workbench").getAsBoolean();
		destroy = object.get("Destroy").getAsBoolean();
		template = object.has("Template") ? object.get("Template").getAsString() : null;
		reference = object.has("Reference") ? object.get("Reference").getAsString() : null;
		level = object.get("Level").getAsInt();
		max = object.get("Max").getAsInt();
		min = object.has("Min") ? object.get("Min").getAsInt() : 0;
		success = object.get("Success").getAsDouble();

		// 成功率衰减配置
		decayEnabled = object.has("DecayEnabled") && object.get("DecayEnabled").getAsBoolean();
		decayFactor = object.has("DecayFactor") ? object.get("DecayFactor").getAsDouble() : 1.0;

		// 掉级惩罚配置
		downgradeRangeMin = object.has("DowngradeRangeMin") ? object.get("DowngradeRangeMin").getAsInt() : -1;
		downgradeRangeMax = object.has("DowngradeRangeMax") ? object.get("DowngradeRangeMax").getAsInt() : -1;
		downgradeChance = object.has("DowngradeChance") ? object.get("DowngradeChance").getAsDouble() : 0;
		downgradeAmount = object.has("DowngradeAmount") ? object.get("DowngradeAmount").getAsInt() : 1;

		// 碎裂惩罚配置
		breakRangeMin = object.has("BreakRangeMin") ? object.get("BreakRangeMin").getAsInt() : -1;
		breakRangeMax = object.has("BreakRangeMax") ? object.get("BreakRangeMax").getAsInt() : -1;
		breakChance = object.has("BreakChance") ? object.get("BreakChance").getAsDouble() : 0;

		// 保护配置
		downgradeProtectKey = object.has("DowngradeProtectKey") ? object.get("DowngradeProtectKey").getAsString() : null;
		breakProtectKey = object.has("BreakProtectKey") ? object.get("BreakProtectKey").getAsString() : null;
		destroyProtectKey = object.has("DestroyProtectKey") ? object.get("DestroyProtectKey").getAsString() : null;

		// 背包强化控制（默认 false，向后兼容）
		disableBackpack = object.has("DisableBackpack") && object.get("DisableBackpack").getAsBoolean();

		// 一次升满配置（默认 false，向后兼容）
		upgradeToMax = object.has("UpgradeToMax") && object.get("UpgradeToMax").getAsBoolean();

		// 跳级配置（默认 1，向后兼容）
		upgradeAmount = object.has("UpgradeAmount") ? object.get("UpgradeAmount").getAsInt() : 1;
	}

	/**
	 * @return The template associated to this data, if it is loaded.
	 */
	@Nullable public UpgradeTemplate getTemplate() {
		if (template == null) { return null; }
		return MMOItems.plugin.getUpgrades().getTemplate(template);
	}

	@Nullable public String getTemplateName() {return template; }

	public int getLevel() { return level; }

	/**
	 * Dont you mean {@link UpgradeTemplate#upgradeTo(MMOItem, int)}?
	 * This sets the level the item thinks it is, does not apply no changes.
	 * <p></p>
	 * <b>Make sure you know what you are doing before using this</b>
	 */
	public void setLevel(int l) { level = l; }

	public int getMaxUpgrades() { return max; }

	public boolean canLevelUp() {
		return max == 0 || level < max;
	}

	public boolean destroysOnFail() {
		return destroy;
	}

	public double getSuccess() {
		return success == 0 ? 1 : success;
	}

	/**
	 * 计算考虑衰减后的实际成功率
	 *
	 * @param currentLevel 当前强化等级
	 * @return 应用衰减后的成功率（0-1）
	 */
	public double getActualSuccess(int currentLevel) {
		if (!decayEnabled || decayFactor >= 1.0) {
			return getSuccess();
		}
		return getSuccess() * Math.pow(decayFactor, currentLevel);
	}

	/**
	 * 判断当前等级是否在掉级惩罚区间内
	 *
	 * @param level 当前强化等级
	 * @return 是否在掉级区间内
	 */
	public boolean isInDowngradeRange(int level) {
		// 使用 >= 0 判断，因为默认值是 -1（表示未配置），0 是有效的起始等级
		return downgradeRangeMin >= 0 && level >= downgradeRangeMin && level <= downgradeRangeMax;
	}

	/**
	 * 判断当前等级是否在碎裂惩罚区间内
	 *
	 * @param level 当前强化等级
	 * @return 是否在碎裂区间内
	 */
	public boolean isInBreakRange(int level) {
		// 使用 >= 0 判断，因为默认值是 -1（表示未配置），0 是有效的起始等级
		return breakRangeMin >= 0 && level >= breakRangeMin && level <= breakRangeMax;
	}

	@Deprecated
	public boolean matchesReference(UpgradeData data) {
		return MMOUtils.checkReference(reference, data.reference);
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	/**
	 *  Upgrade this MMOItem by 1 Level
	 */
	@Deprecated
	public void upgrade(@NotNull MMOItem mmoitem) {

		/*
		 *  Find Upgrade Template
		 */
		if (getTemplate() == null) {
			MMOItems.plugin.getLogger().warning("Couldn't find upgrade template '" + template + "'. Does it exist?");
			return;
		}

		/*
		 *  Go through every stat that must be ugpraded and apply
		 */
		getTemplate().upgrade(mmoitem);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();

		// 基础配置
		if (reference != null && !reference.isEmpty())
			json.addProperty("Reference", reference);
		if (template != null && !template.isEmpty())
			json.addProperty("Template", template);
		json.addProperty("Workbench", workbench);
		json.addProperty("Destroy", destroy);
		json.addProperty("Level", level);
		json.addProperty("Max", max);
		json.addProperty("Min", min);
		json.addProperty("Success", success);

		// 成功率衰减配置
		if (decayEnabled)
			json.addProperty("DecayEnabled", true);
		if (decayFactor != 1.0)
			json.addProperty("DecayFactor", decayFactor);

		// 掉级惩罚配置（使用 >= 0 判断，因为 -1 表示未配置，0 是有效值）
		if (downgradeRangeMin >= 0) {
			json.addProperty("DowngradeRangeMin", downgradeRangeMin);
			json.addProperty("DowngradeRangeMax", downgradeRangeMax);
		}
		if (downgradeChance > 0)
			json.addProperty("DowngradeChance", downgradeChance);
		if (downgradeAmount != 1)
			json.addProperty("DowngradeAmount", downgradeAmount);

		// 碎裂惩罚配置（使用 >= 0 判断，因为 -1 表示未配置，0 是有效值）
		if (breakRangeMin >= 0) {
			json.addProperty("BreakRangeMin", breakRangeMin);
			json.addProperty("BreakRangeMax", breakRangeMax);
		}
		if (breakChance > 0)
			json.addProperty("BreakChance", breakChance);

		// 保护配置
		if (downgradeProtectKey != null && !downgradeProtectKey.isEmpty())
			json.addProperty("DowngradeProtectKey", downgradeProtectKey);
		if (breakProtectKey != null && !breakProtectKey.isEmpty())
			json.addProperty("BreakProtectKey", breakProtectKey);
		if (destroyProtectKey != null && !destroyProtectKey.isEmpty())
			json.addProperty("DestroyProtectKey", destroyProtectKey);

		// 背包强化控制（仅在启用时序列化）
		if (disableBackpack)
			json.addProperty("DisableBackpack", true);

		// 一次升满配置（仅在启用时序列化）
		if (upgradeToMax)
			json.addProperty("UpgradeToMax", true);

		// 跳级配置（仅在非默认值时序列化）
		if (upgradeAmount != 1)
			json.addProperty("UpgradeAmount", upgradeAmount);

		return json;
	}

	@Override
	public String toString() {
		return toJson().toString();
	}

	@Override
	public UpgradeData randomize(MMOItemBuilder builder) {
		return this;
	}

	@Override
	public UpgradeData clone() {
		UpgradeData cloned = new UpgradeData(reference, template, workbench, destroy, max, min, success,
				decayEnabled, decayFactor,
				downgradeRangeMin, downgradeRangeMax, downgradeChance, downgradeAmount,
				breakRangeMin, breakRangeMax, breakChance,
				downgradeProtectKey, breakProtectKey, destroyProtectKey, disableBackpack, upgradeToMax, upgradeAmount);
		cloned.setLevel(this.level);
		return cloned;
	}
}
