package net.Indyuce.mmoitems.manager;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ConfigFile;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.upgrade.bonus.UpgradeChanceBonusCalculator;
import net.Indyuce.mmoitems.api.upgrade.economy.UpgradeEconomyHandler;
import net.Indyuce.mmoitems.api.upgrade.guarantee.GuaranteeManager;
import net.Indyuce.mmoitems.api.upgrade.limit.DailyLimitManager;
import net.Indyuce.mmoitems.api.upgrade.log.UpgradeLogManager;
import net.Indyuce.mmoitems.api.upgrade.penalty.GlobalPenaltyConfig;
import net.Indyuce.mmoitems.api.upgrade.UpgradeRuntimeSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 强化模板管理器
 * <p>
 * 管理强化模板、保底机制和每日限制功能
 * </p>
 */
public class UpgradeManager implements Reloadable {
	private final Map<String, UpgradeTemplate> templates = new HashMap<>();

	/**
	 * 保底机制管理器
	 */
	private GuaranteeManager guaranteeManager;

	/**
	 * 每日限制管理器
	 */
	private DailyLimitManager dailyLimitManager;

	/**
	 * 经济消耗处理器
	 */
	private UpgradeEconomyHandler economyHandler;

	/**
	 * 全局惩罚梯度配置
	 */
	private GlobalPenaltyConfig globalPenaltyConfig;

	/**
	 * 概率加成计算器
	 */
	private UpgradeChanceBonusCalculator chanceBonusCalculator;

	/**
	 * 强化日志管理器
	 */
	private UpgradeLogManager logManager;

	/**
	 * 强化运行期配置缓存（广播、自动绑定等）
	 */
	private UpgradeRuntimeSettings runtimeSettings;

	public UpgradeManager() {
		reload();
	}

	public void reload() {
		templates.clear();

		FileConfiguration config = new ConfigFile("upgrade-templates").getConfig();
		for (String key : config.getKeys(false)) {

			// Register
			registerTemplate(new UpgradeTemplate(config.getConfigurationSection(key)));
		}

		// 初始化或重载保底管理器
		if (guaranteeManager == null) {
			guaranteeManager = new GuaranteeManager();
		} else {
			guaranteeManager.reload();
		}

		// 初始化或重载每日限制管理器
		if (dailyLimitManager == null) {
			dailyLimitManager = new DailyLimitManager();
		} else {
			dailyLimitManager.reload();
		}

		// 初始化或重载经济消耗处理器
		if (economyHandler == null) {
			economyHandler = new UpgradeEconomyHandler();
		} else {
			economyHandler.reload();
		}

		// 初始化或重载全局惩罚梯度配置
		if (globalPenaltyConfig == null) {
			globalPenaltyConfig = new GlobalPenaltyConfig();
		} else {
			globalPenaltyConfig.reload();
		}

		// 初始化或重载概率加成计算器
		if (chanceBonusCalculator == null) {
			chanceBonusCalculator = new UpgradeChanceBonusCalculator();
		} else {
			chanceBonusCalculator.reload();
		}

		// 初始化或重载日志管理器
		if (logManager == null) {
			logManager = new UpgradeLogManager();
		} else {
			logManager.reload();
		}

		// 初始化或重载运行期配置缓存
		if (runtimeSettings == null) {
			runtimeSettings = new UpgradeRuntimeSettings();
		} else {
			runtimeSettings.reload();
		}
	}

	public Collection<UpgradeTemplate> getAll() {
		return templates.values();
	}

	/**
	 * Get the <code>UpgradeTemplate</code> of this name.
	 * @return <code>null</code> if there is no such template loaded.
	 */
	@Nullable public UpgradeTemplate getTemplate(@NotNull String id) {
		return templates.get(id);
	}

	public boolean hasTemplate(String id) {
		return templates.containsKey(id);
	}

	public void registerTemplate(UpgradeTemplate template) {
		templates.put(template.getId(), template);
	}

	/**
	 * 获取保底机制管理器
	 *
	 * @return 保底管理器实例
	 */
	@NotNull
	public GuaranteeManager getGuaranteeManager() {
		return guaranteeManager;
	}

	/**
	 * 获取每日限制管理器
	 *
	 * @return 每日限制管理器实例
	 */
	@NotNull
	public DailyLimitManager getDailyLimitManager() {
		return dailyLimitManager;
	}

	/**
	 * 获取经济消耗处理器
	 *
	 * @return 经济消耗处理器实例
	 */
	@Nullable
	public UpgradeEconomyHandler getEconomyHandler() {
		return economyHandler;
	}

	/**
	 * 获取全局惩罚梯度配置
	 *
	 * @return 全局惩罚配置实例
	 */
	@NotNull
	public GlobalPenaltyConfig getGlobalPenaltyConfig() {
		return globalPenaltyConfig;
	}

	/**
	 * 获取概率加成计算器
	 *
	 * @return 概率加成计算器实例
	 */
	@NotNull
	public UpgradeChanceBonusCalculator getChanceBonusCalculator() {
		return chanceBonusCalculator;
	}

	/**
	 * 获取强化日志管理器
	 *
	 * @return 日志管理器实例
	 */
	@NotNull
	public UpgradeLogManager getLogManager() {
		return logManager;
	}

	/**
	 * 获取强化运行期配置缓存
	 *
	 * @return 运行期配置
	 */
	@NotNull
	public UpgradeRuntimeSettings getRuntimeSettings() {
		return runtimeSettings;
	}
}
