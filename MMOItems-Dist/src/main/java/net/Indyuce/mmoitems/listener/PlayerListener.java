package net.Indyuce.mmoitems.listener;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.api.event.armorequip.ArmorEquipEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.damage.MeleeAttackMetadata;
import io.lumine.mythic.lib.damage.ProjectileAttackMetadata;
import io.lumine.mythic.lib.entity.ProjectileMetadata;
import io.lumine.mythic.lib.entity.ProjectileType;
import io.lumine.mythic.lib.skill.SimpleSkill;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.DeathItemsHandler;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.interaction.projectile.ArrowPotionEffectArrayItem;
import net.Indyuce.mmoitems.api.interaction.util.InteractItem;
import net.Indyuce.mmoitems.api.interaction.weapon.Weapon;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.api.util.DeathDowngrading;
import net.Indyuce.mmoitems.inventory.LoginRefreshSession;
import net.Indyuce.mmoitems.stat.data.PotionEffectData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private static final Map<UUID, BukkitTask> PENDING_LOGIN_REFRESH = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SKILL_ATTACK_GUARD = ThreadLocal.withInitial(() -> false);
    private static final int LOGIN_REFRESH_MAX_ROUNDS = 6;
    private static final int LOGIN_REFRESH_SUCCESS_TARGET = 2;
    private static final long LOGIN_REFRESH_DELAY = 1L;
    private static final long LOGIN_REFRESH_INTERVAL = 10L;

    /**
     * If the player dies, its time to roll the death-downgrade stat!
     */
    @SuppressWarnings("InstanceofIncompatibleInterface")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeathForUpgradeLoss(@NotNull PlayerDeathEvent event) {

        // Supports NPCs
        final PlayerData playerData = PlayerData.getOrNull(event.getEntity());
        if (playerData == null) return;

        // See description of DelayedDeathDowngrade child class for full explanation
        final Player player = event.getEntity();
        new DelayedDeathDowngrade(playerData, player).runTaskLater(MMOItems.plugin, 3L);
    }

    /**
     * Fixes <a href="https://gitlab.com/phoenix-dvpmt/mmocore/-/issues/545">MMOCore#545</a>
     */

    private void scheduleLoginRefresh(@NotNull PlayerData playerData, @NotNull String reason) {
        if (!playerData.isOnline()) {
            MMOItems.plugin.getLogger().log(Level.FINER,
                    "调试: 跳过为玩家 " + playerData.getPlayer().getName() + " 创建进服属性刷新任务，原因：玩家不在线，触发源：" + reason + "。");
            return;
        }

        final UUID uniqueId = playerData.getMMOPlayerData().getUniqueId();
        final String playerName = playerData.getPlayer().getName();

        final BukkitTask previous = PENDING_LOGIN_REFRESH.remove(uniqueId);
        if (previous != null) {
            previous.cancel();
            MMOItems.plugin.getLogger().log(Level.FINER,
                    "调试: 重置玩家 " + playerName + " 的进服属性刷新任务，触发源：" + reason + "。");
        }

        final BukkitTask task = new BukkitRunnable() {
            private int rounds = 0;
            private int success = 0;

            @Override
            public void run() {
                rounds++;

                if (!playerData.isOnline()) {
                    MMOItems.plugin.getLogger().log(Level.FINER,
                            "调试: 玩家 " + playerName + " 已离线，终止进服属性刷新任务。");
                    cancelAndCleanup();
                    return;
                }

                if (!playerData.getMMOPlayerData().hasFullySynchronized()) {
                    MMOItems.plugin.getLogger().log(Level.FINEST,
                            "调试: 玩家 " + playerName + " 的 MythicLib 数据尚未同步完成，第 " + rounds + " 次尝试跳过。");
                    if (rounds >= LOGIN_REFRESH_MAX_ROUNDS) {
                        MMOItems.plugin.getLogger().log(Level.FINER,
                                "调试: 玩家 " + playerName + " 的 MythicLib 数据长时间未就绪，放弃进服属性刷新任务。");
                        cancelAndCleanup();
                    }
                    return;
                }

                LoginRefreshSession.open(playerData, reason);
                playerData.resolveInventory();
                success++;
                MMOItems.plugin.getLogger().log(Level.FINE,
                        "调试: 玩家 " + playerName + " 进服属性刷新完成，第 " + success + " 次，触发源：" + reason
                                + "，当前登记装备数=" + playerData.getInventory().getEquipped().size() + "。");

                if (success >= LOGIN_REFRESH_SUCCESS_TARGET || rounds >= LOGIN_REFRESH_MAX_ROUNDS) {
                    cancelAndCleanup();
                }
            }

            private void cancelAndCleanup() {
                cancel();
                PENDING_LOGIN_REFRESH.remove(uniqueId);
                LoginRefreshSession.close(playerData, "进服属性刷新任务结束");
            }
        }.runTaskTimer(MMOItems.plugin, LOGIN_REFRESH_DELAY, LOGIN_REFRESH_INTERVAL);

        PENDING_LOGIN_REFRESH.put(uniqueId, task);
        MMOItems.plugin.getLogger().log(Level.FINER,
                "调试: 已安排玩家 " + playerName + " 的进服属性刷新任务，触发源：" + reason + "。");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final PlayerData playerData = PlayerData.getOrNull(event.getPlayer());
        if (playerData != null) {
            scheduleLoginRefresh(playerData, "玩家加入服务器");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final BukkitTask task = PENDING_LOGIN_REFRESH.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
            MMOItems.plugin.getLogger().log(Level.FINER,
                    "调试: 玩家 " + event.getPlayer().getName() + " 退出，已清理进服属性刷新任务。");
        }
        final PlayerData playerData = PlayerData.getOrNull(event.getPlayer());
        if (playerData != null) {
            LoginRefreshSession.close(playerData, "玩家退出服务器");
        }
    }

    /**
     * Prevent players from dropping items which are bound to them with a
     * Soulbound. Items are cached inside a map waiting for the player to
     * respawn. If he does not respawn the items are dropped on the ground, this
     * way there don't get lost
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void keepItemsOnDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;

        final Player player = event.getEntity();
        final DeathItemsHandler soulboundInfo = new DeathItemsHandler(player);

        final Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            final ItemStack item = iterator.next();
            final NBTItem nbt = NBTItem.get(item);

            if (nbt.getBoolean("MMOITEMS_DISABLE_DEATH_DROP") || (MMOItems.plugin.getLanguage().keepSoulboundOnDeath && MMOUtils.isSoulboundTo(nbt, player))) {
                iterator.remove();
                soulboundInfo.registerItem(item);
            }
        }

        soulboundInfo.registerIfNecessary();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        DeathItemsHandler.readAndRemove(event.getPlayer());
    }

    @EventHandler
    public void onArmorEquip(ArmorEquipEvent event) {
        if (event.getNewArmorPiece() == null)
            return;

        final PlayerData playerData = PlayerData.getOrNull(event.getPlayer());
        if (playerData != null) {
            net.Indyuce.mmoitems.util.AutoBindUtil.applyAutoBindIfNeeded(playerData, event.getNewArmorPiece());
        }
        if (playerData != null && !playerData.getRPG().canUse(NBTItem.get(event.getNewArmorPiece()), true))
            event.setCancelled(true);
    }

    /**
     * This handler listens to ALL trident shootings, including both
     * custom tridents from MMOItems AND vanilla tridents, since MMOItems
     * needs to apply on-hit effects like crits, elemental damage... even
     * if the player is using a vanilla trident.
     * <p>
     * Fixing commit 6cf6f741
     */
    @EventHandler(ignoreCancelled = true)
    public void registerTridents(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident) || !(event.getEntity().getShooter() instanceof Player))
            return;

        final InteractItem item = new InteractItem((Player) event.getEntity().getShooter(), Material.TRIDENT);
        if (!item.hasItem())
            return;

        final NBTItem nbtItem = MythicLib.plugin.getVersion().getWrapper().getNBTItem(item.getItem());
        final Type type = Type.get(nbtItem.getType());
        final PlayerData playerData = PlayerData.getOrNull((Player) event.getEntity().getShooter());
        if (playerData == null) return;

        if (type != null) {
            final Weapon weapon = new Weapon(playerData, nbtItem);
            if (!weapon.checkItemRequirements() || !weapon.checkAndApplyWeaponCosts()) {
                event.setCancelled(true);
                return;
            }

            final ProjectileMetadata proj = ProjectileMetadata.create(playerData.getMMOPlayerData(), EquipmentSlot.fromBukkit(item.getSlot()), ProjectileType.TRIDENT, event.getEntity());
            proj.setSourceItem(nbtItem);
            proj.setCustomDamage(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void registerArrowSpecialEffects(PlayerAttackEvent event) {
        if (!(event.getAttack() instanceof ProjectileAttackMetadata)) return;

        final ProjectileAttackMetadata projAttack = (ProjectileAttackMetadata) event.getAttack();
        final @Nullable ProjectileMetadata data = ProjectileMetadata.get(projAttack.getProjectile());
        if (data == null || data.getSourceItem() == null) return;

        // Apply MMOItems-specific effects
        applyPotionEffects(data, event.getEntity());
    }

    // Must run early enough so damage-modifying mechanics (like MythicLib 'damage')
    // can affect the current AttackMetadata before it is applied.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void triggerOnAttackForSkillDamage(PlayerAttackEvent event) {
        final var damageMeta = event.getAttack().getDamage();
        final boolean typedSkill = damageMeta.hasType(DamageType.SKILL);
        final boolean typedWeapon = damageMeta.hasType(DamageType.WEAPON);
        final boolean typedProjectile = damageMeta.hasType(DamageType.PROJECTILE);
        final boolean projectileAttack = event.getAttack() instanceof ProjectileAttackMetadata;

        // Requested: allow SKILL, WEAPON and projectile attacks.
        if (event.getAttack() instanceof MeleeAttackMetadata) {
            // Avoid double-triggering vanilla melee attacks (already handled by MMOItems).
            // Only allow melee attacks that are explicitly SKILL-typed.
            if (!typedSkill) {
                return;
            }
        } else {
            if (!typedSkill && !typedWeapon && !typedProjectile && !projectileAttack) {
                return;
            }
        }

        if (SKILL_ATTACK_GUARD.get()) return;

        final Player player = event.getPlayer();
        final PlayerData playerData = PlayerData.getOrNull(player);
        if (playerData == null) return;

        final ItemStack weaponUsed = player.getInventory().getItemInMainHand();
        final NBTItem nbtItem = MythicLib.plugin.getVersion().getWrapper().getNBTItem(weaponUsed);
        final Type itemType = Type.get(nbtItem);
        if (itemType == null || itemType == Type.BLOCK) return;

        final SkillHandler<?> onAttack = itemType.onAttack();
        if (onAttack == null) {
            return;
        }
        if (nbtItem.getBoolean("MMOITEMS_DISABLE_ATTACK_PASSIVE")) return;

        final Weapon weapon = new Weapon(playerData, nbtItem);
        if (!weapon.checkItemRequirements()) {
            return;
        }

        try {
            SKILL_ATTACK_GUARD.set(true);
            // SkillMetadata already contains the target entity; MythicLib exposes it
            // through reserved variables like <target>.
            new SimpleSkill(onAttack).cast(SkillMetadata.of(event.getAttacker(), event.getEntity(), event.getAttack(), event));
        } finally {
            SKILL_ATTACK_GUARD.set(false);
        }
    }

    /**
     * MythicMobs (and potentially other plugins) can deal damage through fake
     * Bukkit damage events. MythicLib intentionally ignores those events, which
     * prevents {@link PlayerAttackEvent} from being fired.
     * <p>
     * However, MythicLib still registers an {@link AttackMetadata} on the target
     * before applying damage (see MMODamageMechanic). We use that registry to
     * trigger MMOItems type on-attack for those damage sources.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void triggerOnAttackForFakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        if (!UtilityMethods.isFake(event)) return;
        if (event.getDamage() <= 0) return;

        final AttackMetadata attack = MythicLib.plugin.getDamage().getRegisteredAttackMetadata(event.getEntity());
        if (attack == null || !attack.isPlayer()) return;
        if (SKILL_ATTACK_GUARD.get()) return;

        if (!(attack.getAttacker() instanceof io.lumine.mythic.lib.api.stat.provider.PlayerStatProvider)) return;

        final var damageMeta = attack.getDamage();
        final boolean typedSkill = damageMeta.hasType(DamageType.SKILL);
        final boolean typedWeapon = damageMeta.hasType(DamageType.WEAPON);
        final boolean typedProjectile = damageMeta.hasType(DamageType.PROJECTILE);
        final boolean projectileAttack = attack instanceof ProjectileAttackMetadata;

        // Keep the same allowlist behaviour as the PlayerAttackEvent listener.
        if (attack instanceof MeleeAttackMetadata) {
            if (!typedSkill) return;
        } else {
            if (!typedSkill && !typedWeapon && !typedProjectile && !projectileAttack) return;
        }

        // MythicLib ignores fake damage events and therefore never calls PlayerAttackEvent.
        // We manually dispatch it so all MythicLib/MMOItems systems relying on it can react.
        final PlayerAttackEvent called = new PlayerAttackEvent(event, attack);
        org.bukkit.Bukkit.getPluginManager().callEvent(called);

        // Mirror MythicLib AttackEventListener behaviour: apply potentially modified damage back.
        if (called.isCancelled()) {
            event.setCancelled(true);
        } else {
            event.setDamage(called.getDamage().getDamage());
        }
    }

    private void applyPotionEffects(ProjectileMetadata proj, LivingEntity target) {
        if (proj.getSourceItem().hasTag("MMOITEMS_ARROW_POTION_EFFECTS"))
            for (ArrowPotionEffectArrayItem entry : MythicLib.plugin.getJson().parse(proj.getSourceItem().getString("MMOITEMS_ARROW_POTION_EFFECTS"), ArrowPotionEffectArrayItem[].class))
                target.addPotionEffect(new PotionEffectData(PotionEffectType.getByName(entry.type), entry.duration, entry.level).toEffect());
    }

    /**
     * Some plugins like to interfere with dropping items when the
     * player dies, or whatever of that sort.
     * <p>
     * MMOItems would hate to dupe items because of this, as such, we wait
     * 3 ticks for those plugins to reasonably complete their operations and
     * then downgrade the items the player still has equipped.
     * <p>
     * If a plugin removes items in this time, they will be completely excluded
     * and no dupes will be caused, and if a plugin adds items, they will be
     * included and downgraded. I think that's reasonable behaviour.
     *
     * @author Gunging
     */
    private static class DelayedDeathDowngrade extends BukkitRunnable {

        final PlayerData playerData;
        final Player player;

        DelayedDeathDowngrade(@NotNull PlayerData playerData, @NotNull Player player) {
            this.player = player;
            this.playerData = playerData;
        }

        @Override
        public void run() {
            // Check if player is still online before downgrading (absorbed from upstream)
            if (!playerData.isOnline()) return;

            // Downgrade player's inventory
            DeathDowngrading.playerDeathDowngrade(playerData, player);
        }
    }
}

