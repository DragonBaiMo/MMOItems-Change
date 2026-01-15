package net.Indyuce.mmoitems.api.upgrade;

import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.Sounds;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.api.upgrade.UpgradeRuntimeSettings;
import net.Indyuce.mmoitems.api.upgrade.UpgradeInventoryScanner;
import net.Indyuce.mmoitems.api.upgrade.bonus.UpgradeChanceBonusCalculator;
import net.Indyuce.mmoitems.api.upgrade.economy.UpgradeEconomyHandler;
import net.Indyuce.mmoitems.api.upgrade.effects.UpgradeEffectsPlayer;
import net.Indyuce.mmoitems.api.upgrade.guarantee.GuaranteeData;
import net.Indyuce.mmoitems.api.upgrade.guarantee.GuaranteeManager;
import net.Indyuce.mmoitems.api.upgrade.limit.DailyLimitManager;
import net.Indyuce.mmoitems.api.upgrade.log.UpgradeLogEntry;
import net.Indyuce.mmoitems.api.upgrade.log.UpgradeLogManager;
import net.Indyuce.mmoitems.api.upgrade.penalty.GlobalPenaltyConfig;
import net.Indyuce.mmoitems.api.util.message.Message;
import net.Indyuce.mmoitems.stat.data.SoulboundData;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 强化服务核心类
 * <p>
 * 提供可复用的强化逻辑，支持：
 * <ul>
 *     <li>背包强化（UpgradeStat 调用）</li>
 *     <li>命令强化（UpgradeCommandTreeNode 调用）</li>
 *     <li>工作台强化（CraftingStation 调用）</li>
 * </ul>
 * </p>
 * <p>
 * 核心功能：
 * <ul>
 *     <li>强化石查找与消耗</li>
 *     <li>成功率计算（含衰减）</li>
 *     <li>成功/失败处理</li>
 *     <li>惩罚判定与执行</li>
 * </ul>
 * </p>
 *
 * @author MMOItems Team
 * @since 强化命令系统
 */
public class UpgradeService {

    private static final Random RANDOM = new Random();

    /**
     * 执行强化操作
     * <p>
     * 这是强化服务的核心入口方法，根据上下文执行强化：
     * <ol>
     *     <li>验证前置条件（等级上限等）</li>
     *     <li>查找并验证强化石（非免费模式）</li>
     *     <li>计算实际成功率</li>
     *     <li>执行成功/失败判定</li>
     *     <li>应用结果（升级或惩罚）</li>
     * </ol>
     * </p>
     *
     * @param context 强化上下文
     * @return 强化结果
     */
    @NotNull
    public static UpgradeResult performUpgrade(@NotNull UpgradeContext context) {
        Player player = context.getPlayer();
        MMOItem targetMMO = context.getTargetItem();
        UpgradeData targetData = context.getTargetData();
        UpgradeRuntimeSettings runtimeSettings = MMOItems.plugin.getUpgrades().getRuntimeSettings();

        // ========== 0. 每日限制检查（新增） ==========
        UpgradeManagerFacade upgradeManagers = UpgradeManagerFacade.from(MMOItems.plugin.getUpgrades());
        DailyLimitManager dailyLimitManager = upgradeManagers.dailyLimitManager;
        if (dailyLimitManager != null && dailyLimitManager.isEnabled()) {
            if (!dailyLimitManager.canUpgrade(player)) {
                int used = dailyLimitManager.getUsedAttempts(player);
                int max = dailyLimitManager.getMaxAttempts(player);
                return UpgradeResult.error("今日强化次数已用尽 (" + used + "/" + max + ")");
            }
        }

        // ========== 0.5 经济消耗检查（新增） ==========
        UpgradeEconomyHandler economyHandler = upgradeManagers.economyHandler;
        double economyCost = 0;
        boolean economyEnabled = economyHandler != null && economyHandler.isEnabled();
        if (economyEnabled) {
            economyCost = economyHandler.getCost(targetData.getLevel());
            if (economyCost > 0 && !economyHandler.canAfford(player, economyCost)) {
                String formattedCost = economyHandler.format(economyCost);
                return UpgradeResult.error("金币不足，需要 " + formattedCost);
            }
        }

        // 1. 验证强化模板
        UpgradeTemplate template = targetData.getTemplate();
        if (template == null) {
            return UpgradeResult.error("未找到强化模板: " + targetData.getTemplateName());
        }

        // 2. 等级检查（非强制模式）
        if (!context.isForceMode() && !targetData.canLevelUp()) {
            return UpgradeResult.error("已达到最大强化等级");
        }

        // 3. 直达模式等级检查
        if (context.isDirectMode()) {
            int currentLevel = targetData.getLevel();
            int directLevel = context.getDirectLevel();
            if (directLevel <= currentLevel) {
                return UpgradeResult.error("目标等级必须高于当前等级 (当前: " + currentLevel + ")");
            }
            if (!context.isForceMode() && targetData.getMax() > 0 && directLevel > targetData.getMax()) {
                return UpgradeResult.error("目标等级超出上限 (上限: " + targetData.getMax() + ")");
            }
        }

        // 4. 查找强化石（非免费模式）
        List<ItemStack> upgradeStones = new ArrayList<>();
        UpgradeData consumableData = null;
        int requiredStones = context.getRequiredStoneCount();

        if (!context.isFreeMode()) {
            UpgradeInventoryScanner scanner = new UpgradeInventoryScanner(player, targetData.getReference(), requiredStones);
            scanner.scan(null);
            upgradeStones = scanner.getFoundStones();
            if (upgradeStones.size() < requiredStones) {
                return UpgradeResult.error("背包中强化石不足，需要 " + requiredStones + " 个，当前 " + upgradeStones.size() + " 个");
            }
            // 使用第一个强化石的成功率
            NBTItem firstStoneNBT = NBTItem.get(upgradeStones.get(0));
            VolatileMMOItem firstStone = new VolatileMMOItem(firstStoneNBT);
            if (firstStone.hasData(ItemStats.UPGRADE)) {
                consumableData = (UpgradeData) firstStone.getData(ItemStats.UPGRADE);
            }
        }

        // ========== 4.5 执行经济扣款（新增） ==========
        if (economyEnabled && economyCost > 0) {
            UpgradeEconomyHandler.EconomyOperationResult withdrawResult = economyHandler.withdraw(player, economyCost);
            if (!withdrawResult.isSuccess()) {
                return UpgradeResult.error("扣款失败: " + withdrawResult.getErrorMessage());
            }
        }

        // 5. 计算实际成功率（含辅料加成）
        double actualSuccess = calculateActualSuccess(consumableData, targetData, context.getChanceModifier());
        // 应用辅料成功率加成（累乘口径：actualSuccess = actualSuccess * (1 + bonus/100) ...）
        if (context.getAuxiliaryChanceBonus() > 0) {
            actualSuccess *= 1.0 + (context.getAuxiliaryChanceBonus() / 100.0);
        }

        // ========== 5.2 全局概率加成（新增） ==========
        UpgradeChanceBonusCalculator chanceBonusCalculator = upgradeManagers.chanceBonusCalculator;
        if (chanceBonusCalculator.isEnabled()) {
            double globalBonus = chanceBonusCalculator.calculateBonus(player, targetData.getLevel());
            if (globalBonus > 0) {
                actualSuccess *= 1.0 + (globalBonus / 100.0);
            }
        }

        // ========== 5.5 保底机制检查（新增） ==========
        GuaranteeManager guaranteeManager = upgradeManagers.guaranteeManager;
        boolean guaranteeTriggered = false;
        if (guaranteeManager != null && guaranteeManager.isEnabled()) {
            if (guaranteeManager.isGuaranteed(context.getTargetItemStack())) {
                actualSuccess = 1.0;
                guaranteeTriggered = true;
                // 发送保底触发消息
                Message.UPGRADE_GUARANTEE_TRIGGERED.format(ChatColor.GOLD).send(player);
                // 播放保底特效
                UpgradeEffectsPlayer.playGuarantee(player);
            }
        }

        // 确保成功率在合理范围内
        actualSuccess = Math.min(1.0, Math.max(0, actualSuccess));

        // 6. 保存原始等级
        int originalLevel = targetData.getLevel();

        // 7. 判定成功或失败
        boolean success = RANDOM.nextDouble() <= actualSuccess;

        // ========== 8. 记录每日次数（新增） ==========
        if (dailyLimitManager != null && dailyLimitManager.isEnabled()) {
            dailyLimitManager.recordAttempt(player);
        }

        if (success) {
            // 成功时重置保底计数
            if (guaranteeManager != null && guaranteeManager.isEnabled() && context.getTargetItemStack() != null) {
                NBTItem nbtItem = NBTItem.get(context.getTargetItemStack());
                guaranteeManager.recordSuccess(nbtItem);
            }
            return handleUpgradeSuccess(context, targetMMO, targetData, template, upgradeStones, originalLevel, guaranteeTriggered, runtimeSettings);
        } else {
            // 失败时增加保底计数
            if (guaranteeManager != null && guaranteeManager.isEnabled() && context.getTargetItemStack() != null) {
                NBTItem nbtItem = NBTItem.get(context.getTargetItemStack());
                guaranteeManager.recordFail(nbtItem);
            }
            return handleUpgradeFailure(context, player, targetMMO, targetData, upgradeStones, originalLevel, upgradeManagers.globalPenaltyConfig);
        }
    }

    /**
     * 从玩家背包中查找符合条件的强化石
     * <p>
     * 强化石必须同时满足以下条件：
     * <ol>
     *     <li>物品类型为 CONSUMABLE（消耗品）</li>
     *     <li>拥有 UPGRADE 数据</li>
     *     <li>reference 与目标物品匹配</li>
     * </ol>
     * 通过类型检查，可被强化的装备（SWORD、ARMOR等类型）不会被错误识别为强化石。
     * </p>
     *
     * @param player          玩家
     * @param targetReference 目标物品的强化参考标识
     * @param count           需要的数量
     * @return 找到的强化石列表
     */
    @NotNull
    public static List<ItemStack> findUpgradeStones(@NotNull Player player, @Nullable String targetReference, int count) {
        UpgradeInventoryScanner scanner = new UpgradeInventoryScanner(player, targetReference, count);
        scanner.scan(null);
        return scanner.getFoundStones();
    }

    /**
     * 计算实际成功率
     * <p>
     * 公式：实际成功率 = 基础成功率 × 衰减系数^当前等级 × chance系数
     * </p>
     *
     * @param consumableData  消耗品强化数据（提供基础成功率）
     * @param targetData      目标物品强化数据（提供衰减配置）
     * @param chanceModifier  成功率系数
     * @return 实际成功率（0-1）
     */
    public static double calculateActualSuccess(@Nullable UpgradeData consumableData,
                                                @NotNull UpgradeData targetData,
                                                double chanceModifier) {
        // 获取基础成功率
        double baseSuccess;
        if (consumableData != null) {
            baseSuccess = consumableData.getSuccess();
        } else {
            // 免费模式或无消耗品数据时，使用 100% 成功率
            baseSuccess = 1.0;
        }

        // 应用衰减
        double actualSuccess = baseSuccess;
        if (targetData.isDecayEnabled() && targetData.getDecayFactor() < 1.0) {
            actualSuccess *= Math.pow(targetData.getDecayFactor(), targetData.getLevel());
        }

        // 应用 chance 系数
        actualSuccess *= chanceModifier;

        return actualSuccess;
    }

    /**
     * 处理强化成功
     *
     * @param context            强化上下文
     * @param targetMMO          目标 MMOItem
     * @param targetData         目标强化数据
     * @param template           强化模板
     * @param upgradeStones      强化石列表
     * @param originalLevel      原始等级
     * @param guaranteeTriggered 是否触发了保底
     * @return 强化结果
     */
    @NotNull
    private static UpgradeResult handleUpgradeSuccess(@NotNull UpgradeContext context,
                                                       @NotNull MMOItem targetMMO,
                                                       @NotNull UpgradeData targetData,
                                                       @NotNull UpgradeTemplate template,
                                                       @NotNull List<ItemStack> upgradeStones,
                                                       int originalLevel,
                                                       boolean guaranteeTriggered,
                                                       @NotNull UpgradeRuntimeSettings runtimeSettings) {
        Player player = context.getPlayer();

        // 消耗强化石
        int consumedStones = 0;
        if (!context.isFreeMode()) {
            int requiredStones = context.getRequiredStoneCount();
            consumedStones = consumeStones(upgradeStones, requiredStones);
        }

        // 执行升级
        int newLevel;
        int directUpBonusLevels = 0;

        if (context.isDirectMode()) {
            // 直达模式：直接到目标等级
            int targetLevel = context.getDirectLevel();
            // 检查上限（非强制模式）
            if (!context.isForceMode() && targetData.getMax() > 0 && targetLevel > targetData.getMax()) {
                targetLevel = targetData.getMax();
            }
            template.upgradeTo(targetMMO, targetLevel);
            newLevel = targetLevel;
        } else {
            // 普通模式：+1
            template.upgrade(targetMMO);
            newLevel = originalLevel + 1;

            // ========== 直达石跳级效果（新增） ==========
            if (context.getAuxiliaryDirectUpChance() > 0 && context.getAuxiliaryDirectUpLevels() > 0) {
                double directUpChance = context.getAuxiliaryDirectUpChance() / 100.0;
                if (RANDOM.nextDouble() <= directUpChance) {
                    directUpBonusLevels = context.getAuxiliaryDirectUpLevels();
                    int bonusTarget = newLevel + directUpBonusLevels;
                    // 检查上限
                    if (!context.isForceMode() && targetData.getMax() > 0 && bonusTarget > targetData.getMax()) {
                        bonusTarget = targetData.getMax();
                        directUpBonusLevels = bonusTarget - newLevel;
                    }
                    if (directUpBonusLevels > 0) {
                        template.upgradeTo(targetMMO, bonusTarget);
                        newLevel = bonusTarget;
                        // 发送直达石生效消息
                        Message.UPGRADE_DIRECT_UP_TRIGGERED.format(ChatColor.LIGHT_PURPLE, "#levels#",
                                String.valueOf(directUpBonusLevels)).send(player);
                        player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 2.5f);
                    }
                }
            }
        }

        // ========== 全服通报（新增） ==========
        broadcastUpgradeIfNeeded(player, targetMMO, newLevel, runtimeSettings);

        // ========== 强化后自动绑定（新增） ==========
        applyAutoBindOnUpgradeIfNeeded(player, targetMMO, runtimeSettings);

        // ========== 播放成功特效（新增） ==========
        UpgradeEffectsPlayer.playSuccess(player, newLevel);

        // ========== 记录强化日志（新增） ==========
        logUpgradeResult(player, targetMMO, originalLevel, newLevel, true, null, consumedStones, 0, guaranteeTriggered);

        return UpgradeResult.success(targetMMO, originalLevel, newLevel, consumedStones, directUpBonusLevels, guaranteeTriggered);
    }

    /**
     * 处理强化失败
     *
     * @param context       强化上下文
     * @param player        玩家
     * @param targetMMO     目标 MMOItem
     * @param targetData    目标强化数据
     * @param upgradeStones 强化石列表
     * @param originalLevel 原始等级
     * @return 强化结果
     */
    @NotNull
    private static UpgradeResult handleUpgradeFailure(@NotNull UpgradeContext context,
                                                       @NotNull Player player,
                                                       @NotNull MMOItem targetMMO,
                                                       @NotNull UpgradeData targetData,
                                                       @NotNull List<ItemStack> upgradeStones,
                                                       int originalLevel,
                                                       @Nullable GlobalPenaltyConfig globalPenaltyConfig) {
        // 消耗强化石
        int consumedStones = 0;
        if (!context.isFreeMode()) {
            int requiredStones = context.getRequiredStoneCount();
            consumedStones = consumeStones(upgradeStones, requiredStones);
        }

        // protect 模式：跳过所有惩罚
        if (context.isProtectMode()) {
            return UpgradeResult.failureProtected(consumedStones);
        }

        // 执行惩罚判定（含辅料保护）
        double protectionReduction = context.getAuxiliaryProtection() / 100.0;
        PenaltyApplicationResult penaltyResult = applyPenaltyDetailed(player, targetMMO, targetData, context.getTargetItemStack(),
                originalLevel, protectionReduction, globalPenaltyConfig);

        PenaltyResult penalty = penaltyResult.getResult();
        if (penalty == PenaltyResult.NONE) {
            // 播放普通失败特效
            UpgradeEffectsPlayer.playFailure(player);
            return UpgradeResult.failureNoPenalty(consumedStones);
        }

        if (penalty == PenaltyResult.PROTECTED) {
            // 保护生效，播放成功特效（表示保护成功）
            UpgradeEffectsPlayer.playSuccess(player, originalLevel);
            return UpgradeResult.failureProtected(consumedStones, penaltyResult.getInterceptedPenalty());
        }

        // 判定惩罚类型播放特效
        if (penalty == PenaltyResult.BREAK || penalty == PenaltyResult.DESTROY) {
            // 播放碎裂/销毁特效
            UpgradeEffectsPlayer.playBreak(player);
        } else {
            // 其他惩罚（如降级）播放普通失败特效
            UpgradeEffectsPlayer.playFailure(player);
        }

        int newLevel = originalLevel;
        if (penalty == PenaltyResult.DOWNGRADE) {
            // 计算新等级
            int downgradeAmount = targetData.getDowngradeAmount();
            newLevel = Math.max(targetData.getMin(), originalLevel - downgradeAmount);
        }

        // ========== 记录强化日志（新增） ==========
        logUpgradeResult(player, targetMMO, originalLevel, newLevel, false, penalty.name(), consumedStones, 0, false);

        return UpgradeResult.failureWithPenalty(penalty, originalLevel, newLevel, consumedStones);
    }

    /**
     * 消耗指定数量的强化石
     *
     * @param stones 强化石列表
     * @param count  需要消耗的数量
     * @return 实际消耗的数量
     */
    private static int consumeStones(@NotNull List<ItemStack> stones, int count) {
        int consumed = 0;
        for (ItemStack stone : stones) {
            if (consumed >= count) break;
            stone.setAmount(stone.getAmount() - 1);
            consumed++;
        }
        return consumed;
    }

    /**
     * 应用强化失败的惩罚（无辅料保护）
     * <p>
     * 惩罚优先级：碎裂 → 掉级 → 销毁
     * </p>
     *
     * @param player          玩家
     * @param targetMMO       目标物品
     * @param targetData      目标强化数据
     * @param targetItemStack 目标 ItemStack（用于修改物品，可为 null）
     * @param originalLevel   强化前的等级
     * @return 惩罚结果
     */
    @NotNull
    public static PenaltyResult applyPenalty(@NotNull Player player,
                                             @NotNull MMOItem targetMMO,
                                             @NotNull UpgradeData targetData,
                                             @Nullable ItemStack targetItemStack,
                                             int originalLevel) {
        GlobalPenaltyConfig globalPenaltyConfig = MMOItems.plugin.getUpgrades().getGlobalPenaltyConfig();
        return applyPenaltyDetailed(player, targetMMO, targetData, targetItemStack, originalLevel, 0, globalPenaltyConfig).getResult();
    }

    /**
     * 应用强化失败的惩罚（含辅料保护）
     * <p>
     * 惩罚优先级：碎裂 → 掉级 → 销毁
     * </p>
     * <p>
     * 辅料保护会按比例降低惩罚触发概率：
     * <code>实际惩罚概率 = 原始惩罚概率 × (1 - 辅料保护)</code>
     * </p>
     *
     * @param player              玩家
     * @param targetMMO           目标物品
     * @param targetData          目标强化数据
     * @param targetItemStack     目标 ItemStack（用于修改物品，可为 null）
     * @param originalLevel       强化前的等级
     * @param protectionReduction 辅料保护降低比例（0-1）
     * @return 惩罚结果
     */
    @NotNull
    public static PenaltyResult applyPenalty(@NotNull Player player,
                                             @NotNull MMOItem targetMMO,
                                             @NotNull UpgradeData targetData,
                                             @Nullable ItemStack targetItemStack,
                                             int originalLevel,
                                             double protectionReduction) {
        GlobalPenaltyConfig globalPenaltyConfig = MMOItems.plugin.getUpgrades().getGlobalPenaltyConfig();
        return applyPenaltyDetailed(player, targetMMO, targetData, targetItemStack, originalLevel, protectionReduction, globalPenaltyConfig).getResult();
    }

    /**
     * 应用强化失败的惩罚（含拦截类型信息）
     * <p>
     * 惩罚优先级：碎裂 → 掉级 → 销毁
     * </p>
     * <p>
     * 惩罚来源优先级：物品配置 > 全局配置
     * </p>
     */
    @NotNull
    public static PenaltyApplicationResult applyPenaltyDetailed(@NotNull Player player,
                                                                @NotNull MMOItem targetMMO,
                                                                @NotNull UpgradeData targetData,
                                                                @Nullable ItemStack targetItemStack,
                                                                int originalLevel,
                                                                double protectionReduction,
                                                                @Nullable GlobalPenaltyConfig globalPenaltyConfig) {

        String itemName = targetMMO.hasData(ItemStats.NAME)
                ? targetMMO.getData(ItemStats.NAME).toString()
                : "物品";

        // 辅料保护系数（降低惩罚触发概率）
        double protectionMultiplier = Math.max(0, 1.0 - protectionReduction);

        // ========== 全局惩罚梯度检查（新增） ==========
        // 如果物品未配置惩罚规则，则使用全局惩罚配置
        boolean useGlobalPenalty = globalPenaltyConfig != null && globalPenaltyConfig.isEnabled() && !hasItemPenaltyConfig(targetData, originalLevel);

        if (useGlobalPenalty) {
            return applyGlobalPenalty(player, targetMMO, targetData, targetItemStack, originalLevel, protectionMultiplier, globalPenaltyConfig, itemName);
        }

        // 优先级1：碎裂判定
        if (targetData.isInBreakRange(originalLevel) && targetData.getBreakChance() > 0) {
            double actualBreakChance = targetData.getBreakChance() * protectionMultiplier;
            if (RANDOM.nextDouble() < actualBreakChance) {
                // 触发碎裂，检查保护
                if (tryConsumeProtection(player, targetData.getBreakProtectKey())) {
                    Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                    return PenaltyApplicationResult.protectedIntercept(PenaltyResult.BREAK);
                } else {
                    // 执行碎裂
                    if (targetItemStack != null) {
                        targetItemStack.setAmount(0);
                    }
                    Message.UPGRADE_FAIL_BREAK.format(ChatColor.RED, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 0.5f);
                    return PenaltyApplicationResult.of(PenaltyResult.BREAK);
                }
            }
        }

        // 优先级2：掉级判定
        if (targetData.isInDowngradeRange(originalLevel) && targetData.getDowngradeChance() > 0) {
            double actualDowngradeChance = targetData.getDowngradeChance() * protectionMultiplier;
            if (RANDOM.nextDouble() < actualDowngradeChance) {
                // 触发掉级，检查保护
                if (tryConsumeProtection(player, targetData.getDowngradeProtectKey())) {
                    Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                    return PenaltyApplicationResult.protectedIntercept(PenaltyResult.DOWNGRADE);
                } else {
                    // 执行掉级
                    int downgradeAmount = targetData.getDowngradeAmount();
                    int newLevel = Math.max(targetData.getMin(), originalLevel - downgradeAmount);
                    int actualDowngrade = originalLevel - newLevel;

                    if (actualDowngrade > 0) {
                        UpgradeTemplate template = targetData.getTemplate();
                        if (template != null) {
                            template.upgradeTo(targetMMO, newLevel);
                            // 如果有 ItemStack，更新它
                            if (targetItemStack != null) {
                                NBTItem result = targetMMO.newBuilder().buildNBT();
                                ItemStack built = result.toItem();
                                targetItemStack.setType(built.getType());
                                targetItemStack.setItemMeta(built.getItemMeta());
                            }
                        }
                        Message.UPGRADE_FAIL_DOWNGRADE.format(ChatColor.RED, "#item#", itemName,
                                "#amount#", String.valueOf(actualDowngrade)).send(player);
                        player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1.5f);
                        return PenaltyApplicationResult.of(PenaltyResult.DOWNGRADE);
                    } else {
                        // 已经在最低等级，无法掉级，但掉级判定已触发，不再继续判定其他惩罚
                        Message.UPGRADE_CMD_FAIL_NO_PENALTY.format(ChatColor.RED).send(player);
                        player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1.5f);
                        return PenaltyApplicationResult.of(PenaltyResult.NONE);
                    }
                }
            }
        }

        // 优先级3：销毁判定（允许 destroy-protect-key 拦截）
        if (targetData.destroysOnFail()) {
            String destroyProtectKey = targetData.getDestroyProtectKey();
            if (destroyProtectKey != null && !destroyProtectKey.isEmpty() && tryConsumeProtection(player, destroyProtectKey)) {
                Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                return PenaltyApplicationResult.protectedIntercept(PenaltyResult.DESTROY);
            }

            if (targetItemStack != null) {
                targetItemStack.setAmount(0);
            }
            Message.UPGRADE_FAIL.format(ChatColor.RED).send(player);
            player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 2);
            return PenaltyApplicationResult.of(PenaltyResult.DESTROY);
        }

        // 无惩罚
        return PenaltyApplicationResult.of(PenaltyResult.NONE);
    }

    /**
     * 尝试从玩家背包中消耗指定保护标签的保护物品
     *
     * @param player     玩家
     * @param protectKey 保护标签
     * @return 是否成功找到并消耗保护物品
     */
    public static boolean tryConsumeProtection(@NotNull Player player, @Nullable String protectKey) {
        if (protectKey == null || protectKey.isEmpty()) return false;
        UpgradeInventoryScanner scanner = new UpgradeInventoryScanner(player, null, 1);
        scanner.scan(protectKey);
        ItemStack protectionItem = scanner.getProtectionItem();
        if (protectionItem == null) {
            return false;
        }
        protectionItem.setAmount(protectionItem.getAmount() - 1);
        return true;
    }

    /**
     * 更新玩家手中的物品
     * <p>
     * 用于命令模式下更新主手物品
     * </p>
     *
     * @param player      玩家
     * @param upgradedMMO 升级后的 MMOItem
     */
    public static void updateMainHandItem(@NotNull Player player, @NotNull MMOItem upgradedMMO) {
        NBTItem result = upgradedMMO.newBuilder().buildNBT();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack built = result.toItem();
        mainHand.setType(built.getType());
        mainHand.setItemMeta(built.getItemMeta());
        player.updateInventory();
    }

    /**
     * 如果满足条件，广播强化成功消息
     * <p>
     * 根据配置的广播等级列表，当物品达到指定等级时全服通报
     * </p>
     *
     * @param player    玩家
     * @param targetMMO 目标物品
     * @param newLevel  新等级
     */
    private static void broadcastUpgradeIfNeeded(@NotNull Player player, @NotNull MMOItem targetMMO, int newLevel,
                                                 @NotNull UpgradeRuntimeSettings runtimeSettings) {
        if (!runtimeSettings.shouldBroadcast(newLevel)) {
            return;
        }

        String itemName = resolveItemName(targetMMO, targetMMO.getType() != null ? targetMMO.getType().getName() : "物品");
        String finalMessage = runtimeSettings.formatBroadcastColored(player.getName(), itemName, newLevel);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(finalMessage));
        Bukkit.getConsoleSender().sendMessage(finalMessage);
    }

    /**
     * 强化成功后自动绑定物品给强化者
     * <p>
     * 根据配置 `item-upgrading.auto-bind-on-upgrade.enabled` 决定是否执行。
     * 如果物品已经绑定则跳过。
     * </p>
     *
     * @param player    强化者
     * @param targetMMO 目标物品
     */
    private static void applyAutoBindOnUpgradeIfNeeded(@NotNull Player player, @NotNull MMOItem targetMMO,
                                                       @NotNull UpgradeRuntimeSettings runtimeSettings) {
        if (!runtimeSettings.isAutoBindEnabled()) {
            return;
        }

        // 检查物品是否已经绑定
        if (targetMMO.hasData(ItemStats.SOULBOUND)) {
            return;
        }

        int bindLevel = runtimeSettings.getAutoBindLevel();

        // 执行绑定
        targetMMO.setData(ItemStats.SOULBOUND, new SoulboundData(player.getUniqueId(), player.getName(), bindLevel));

        // 是否显示消息
        boolean showMessage = runtimeSettings.isAutoBindShowMessage();
        if (showMessage) {
            String itemName = resolveItemName(targetMMO, targetMMO.getType() != null ? targetMMO.getType().getName() : "物品");
            Message.UPGRADE_AUTO_BIND.format(ChatColor.YELLOW, "#item#", itemName,
                    "#level#", MMOUtils.intToRoman(bindLevel)).send(player);
            player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 2f);
        }
    }

    /**
     * 检查物品是否配置了惩罚规则
     * <p>
     * 如果物品配置了碎裂/掉级/销毁任一惩罚规则，返回 true
     * </p>
     *
     * @param targetData    目标强化数据
     * @param originalLevel 原始等级
     * @return 如果物品配置了惩罚规则返回 true
     */
    private static boolean hasItemPenaltyConfig(@NotNull UpgradeData targetData, int originalLevel) {
        // 检查碎裂配置
        if (targetData.isInBreakRange(originalLevel) && targetData.getBreakChance() > 0) {
            return true;
        }
        // 检查掉级配置
        if (targetData.isInDowngradeRange(originalLevel) && targetData.getDowngradeChance() > 0) {
            return true;
        }
        // 检查销毁配置
        if (targetData.destroysOnFail()) {
            return true;
        }
        return false;
    }

    /**
     * 应用全局惩罚梯度配置
     *
     * @param player               玩家
     * @param targetMMO            目标物品
     * @param targetData           目标强化数据
     * @param targetItemStack      目标 ItemStack
     * @param originalLevel        原始等级
     * @param protectionMultiplier 保护系数
     * @param globalConfig         全局惩罚配置
     * @param itemName             物品名称
     * @return 惩罚应用结果
     */
    @NotNull
    private static PenaltyApplicationResult applyGlobalPenalty(@NotNull Player player,
                                                                @NotNull MMOItem targetMMO,
                                                                @NotNull UpgradeData targetData,
                                                                @Nullable ItemStack targetItemStack,
                                                                int originalLevel,
                                                                double protectionMultiplier,
                                                                @NotNull GlobalPenaltyConfig globalConfig,
                                                                @NotNull String itemName) {
        // 获取当前等级对应的惩罚梯度
        GlobalPenaltyConfig.PenaltyTier tier = globalConfig.getTierForLevel(originalLevel);
        GlobalPenaltyConfig.PenaltyType type = tier.getType();

        // 无惩罚
        if (type == GlobalPenaltyConfig.PenaltyType.NONE) {
            return PenaltyApplicationResult.of(PenaltyResult.NONE);
        }

        // 碎裂/销毁类型
        if (type == GlobalPenaltyConfig.PenaltyType.BREAK || type == GlobalPenaltyConfig.PenaltyType.DESTROY) {
            double actualChance = tier.getChance() * protectionMultiplier;
            if (RANDOM.nextDouble() < actualChance) {
                // 触发碎裂，检查保护
                if (tryConsumeProtection(player, targetData.getBreakProtectKey())) {
                    Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                    return PenaltyApplicationResult.protectedIntercept(PenaltyResult.BREAK);
                }
                // 执行碎裂
                if (targetItemStack != null) {
                    targetItemStack.setAmount(0);
                }
                Message.UPGRADE_FAIL_BREAK.format(ChatColor.RED, "#item#", itemName).send(player);
                player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 0.5f);
                return PenaltyApplicationResult.of(PenaltyResult.BREAK);
            }
            // 碎裂判定未触发，返回无惩罚
            return PenaltyApplicationResult.of(PenaltyResult.NONE);
        }

        // 降级类型
        if (type == GlobalPenaltyConfig.PenaltyType.DOWNGRADE) {
            // 先判定碎裂（如果配置了 break-chance）
            if (tier.getBreakChance() > 0) {
                double actualBreakChance = tier.getBreakChance() * protectionMultiplier;
                if (RANDOM.nextDouble() < actualBreakChance) {
                    // 触发碎裂，检查保护
                    if (tryConsumeProtection(player, targetData.getBreakProtectKey())) {
                        Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                        player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                        return PenaltyApplicationResult.protectedIntercept(PenaltyResult.BREAK);
                    }
                    // 执行碎裂
                    if (targetItemStack != null) {
                        targetItemStack.setAmount(0);
                    }
                    Message.UPGRADE_FAIL_BREAK.format(ChatColor.RED, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 0.5f);
                    return PenaltyApplicationResult.of(PenaltyResult.BREAK);
                }
            }

            // 判定降级
            double actualDowngradeChance = tier.getChance() * protectionMultiplier;
            if (RANDOM.nextDouble() < actualDowngradeChance) {
                // 触发降级，检查保护
                if (tryConsumeProtection(player, targetData.getDowngradeProtectKey())) {
                    Message.UPGRADE_FAIL_PROTECTED.format(ChatColor.GREEN, "#item#", itemName).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_PLAYER_LEVELUP, 1, 1.5f);
                    return PenaltyApplicationResult.protectedIntercept(PenaltyResult.DOWNGRADE);
                }

                // 执行降级
                int downgradeAmount = tier.getAmount();
                int newLevel = Math.max(targetData.getMin(), originalLevel - downgradeAmount);
                int actualDowngrade = originalLevel - newLevel;

                if (actualDowngrade > 0) {
                    UpgradeTemplate template = targetData.getTemplate();
                    if (template != null) {
                        template.upgradeTo(targetMMO, newLevel);
                        // 如果有 ItemStack，更新它
                        if (targetItemStack != null) {
                            NBTItem result = targetMMO.newBuilder().buildNBT();
                            targetItemStack.setItemMeta(result.toItem().getItemMeta());
                        }
                    }
                    Message.UPGRADE_FAIL_DOWNGRADE.format(ChatColor.RED, "#item#", itemName,
                            "#amount#", String.valueOf(actualDowngrade)).send(player);
                    player.playSound(player.getLocation(), Sounds.ENTITY_ITEM_BREAK, 1, 1.5f);
                    return PenaltyApplicationResult.of(PenaltyResult.DOWNGRADE);
                }
            }
        }

        // 默认无惩罚
        return PenaltyApplicationResult.of(PenaltyResult.NONE);
    }

    /**
     * 记录强化日志
     *
     * @param player             玩家
     * @param targetMMO          目标物品
     * @param originalLevel      原始等级
     * @param newLevel           新等级
     * @param success            是否成功
     * @param penaltyType        惩罚类型（失败时）
     * @param stonesUsed         使用的强化石数量
     * @param economyCost        经济消耗
     * @param guaranteeTriggered 是否触发保底
     */
    private static void logUpgradeResult(@NotNull Player player,
                                          @NotNull MMOItem targetMMO,
                                          int originalLevel,
                                          int newLevel,
                                          boolean success,
                                          @Nullable String penaltyType,
                                          int stonesUsed,
                                          double economyCost,
                                          boolean guaranteeTriggered) {
        UpgradeLogManager logManager = MMOItems.plugin.getUpgrades().getLogManager();
        if (logManager == null || !logManager.isEnabled()) {
            return;
        }

        String itemName = resolveItemName(targetMMO, targetMMO.getType() != null ? targetMMO.getType().getName() : "物品");

        String itemType = targetMMO.getType() != null ? targetMMO.getType().getId() : "UNKNOWN";
        String itemId = targetMMO.getId() != null ? targetMMO.getId() : "UNKNOWN";

        UpgradeLogEntry entry = new UpgradeLogEntry.Builder()
                .player(player.getUniqueId(), player.getName())
                .item(itemType, itemId, itemName)
                .levels(originalLevel, newLevel)
                .success(success)
                .penalty(penaltyType)
                .stonesUsed(stonesUsed)
                .economyCost(economyCost)
                .guaranteeTriggered(guaranteeTriggered)
                .build();

        logManager.log(entry);
    }

    /**
     * 解析物品展示名，优先使用自定义名称，其次使用类型名称，最后使用指定默认值。
     *
     * @param targetMMO 目标物品
     * @param defaultName 默认名称（类型缺失时使用）
     * @return 展示名称
     */
    @NotNull
    private static String resolveItemName(@NotNull MMOItem targetMMO, @NotNull String defaultName) {
        if (targetMMO.hasData(ItemStats.NAME)) {
            return targetMMO.getData(ItemStats.NAME).toString();
        }
        if (targetMMO.getType() != null) {
            return targetMMO.getType().getName();
        }
        return defaultName;
    }

    /**
     * 管理器获取聚合，减少多次 getter 链调用。
     */
    private static class UpgradeManagerFacade {
        private final DailyLimitManager dailyLimitManager;
        private final UpgradeEconomyHandler economyHandler;
        private final GuaranteeManager guaranteeManager;
        private final UpgradeChanceBonusCalculator chanceBonusCalculator;
        private final GlobalPenaltyConfig globalPenaltyConfig;

        private UpgradeManagerFacade(DailyLimitManager dailyLimitManager,
                                     UpgradeEconomyHandler economyHandler,
                                     GuaranteeManager guaranteeManager,
                                     UpgradeChanceBonusCalculator chanceBonusCalculator,
                                     GlobalPenaltyConfig globalPenaltyConfig) {
            this.dailyLimitManager = dailyLimitManager;
            this.economyHandler = economyHandler;
            this.guaranteeManager = guaranteeManager;
            this.chanceBonusCalculator = chanceBonusCalculator;
            this.globalPenaltyConfig = globalPenaltyConfig;
        }

        @NotNull
        static UpgradeManagerFacade from(@NotNull net.Indyuce.mmoitems.manager.UpgradeManager manager) {
            return new UpgradeManagerFacade(
                    manager.getDailyLimitManager(),
                    manager.getEconomyHandler(),
                    manager.getGuaranteeManager(),
                    manager.getChanceBonusCalculator(),
                    manager.getGlobalPenaltyConfig()
            );
        }
    }
}
