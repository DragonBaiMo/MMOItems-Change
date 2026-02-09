package net.Indyuce.mmoitems.inventory;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.handler.StatHandler;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.ModifierSupplier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import io.lumine.mythic.lib.player.modifier.SimpleModifierSupplier;
import io.lumine.mythic.lib.player.particle.ParticleEffect;
import io.lumine.mythic.lib.player.permission.PermissionModifier;
import io.lumine.mythic.lib.player.potion.PermanentPotionEffect;
import io.lumine.mythic.lib.player.skill.PassiveSkill;
import io.lumine.mythic.lib.util.annotation.BackwardsCompatibility;
import io.lumine.mythic.lib.util.lang3.Validate;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ItemSet;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.RefreshInventoryEvent;
import net.Indyuce.mmoitems.api.event.inventory.ItemEquipEvent;
import net.Indyuce.mmoitems.api.event.inventory.ItemUnequipEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import net.Indyuce.mmoitems.stat.type.WeaponBaseStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.UUID;

/**
 * Bukkit-MMOItems interface class.
 * <p>
 * Makes sure MMOItems is up-to-date with the most recent version
 * of the player's inventory, in a performant way. Then, applies
 * modifiers like permanent effects, permissions, set bonuses...
 * <p>
 * TODO refactor abilities/permissions/... and move them to individual stat classes
 * TODO after adding some interface like StatThatRegistersPlayerModifiers
 *
 * @author jules
 */
public class InventoryResolver {
    private final PlayerData playerData;
    private final List<InventoryWatcher> watchers = new ArrayList<>();

    /**
     * Item registry
     */
    private final Set<EquippedItem> activeItems = new HashSet<>();

    // Item set logic
    private final Map<ItemSet, Integer> itemSetCount = new HashMap<>();
    private final ModifierSupplier setModifierSupplier = new SimpleModifierSupplier();

    // Two-Handed-ness
    private @Nullable Boolean encumbered;

    private static final String MODIFIER_KEY = "MMOItems";
    private static final String INVENTORY_TIMER_MODIFIER_KEY = "MMOItemsInventoryTimer";
    private static final String TIMER_INVENTORY_TRIGGER_NAME = "TIMER_INVENTORY";
    private static final String TIMER_INVENTORY_TRIGGER_LEGACY_NAME = "INVENTORY_TIMER";
    private static final int STORAGE_SLOT_COUNT = 36;

    private final int[] inventoryTimerSlotHashes = new int[STORAGE_SLOT_COUNT];
    private final Material[] inventoryTimerSlotTypes = new Material[STORAGE_SLOT_COUNT];
    private final int[] inventoryTimerSlotAmounts = new int[STORAGE_SLOT_COUNT];
    private final boolean[] inventoryTimerDirtySlots = new boolean[STORAGE_SLOT_COUNT];
    private final Map<Integer, List<UUID>> inventoryTimerSkillIds = new HashMap<>();
    private boolean inventoryTimerInitialized;
    private boolean initialized;
    private int lastHeldSlot = -1;
    private boolean inventoryTimerSyncQueued;

    public static boolean ENABLE_ORNAMENTS = false;

    public InventoryResolver(PlayerData playerData) {
        this.playerData = playerData;
        Arrays.fill(inventoryTimerSlotTypes, Material.AIR);

        // TODO reset all item modifiers on join? extra safety
    }

    public void initialize() {
        if (initialized) return;
        this.watchers.addAll(MMOItems.plugin.getInventory().getWatchers(this));
        syncTimerInventoryAbilities();
        initialized = true;
    }

    public void onClose() {

        // 禁用 watcher，避免会话结束后的异步回调
        watchers.clear();

        // 清理已应用的物品增益
        playerData.getMMOPlayerData().getStatMap().bufferUpdates(() -> {
            for (var equipped : activeItems) if (equipped.applied) unapplyModifiers(equipped);
        });
        activeItems.clear();

        // 清理套装增益
        itemSetCount.clear();
        resetItemSetModifiers();

        // 清理背包定时触发器注册
        clearTimerInventoryAbilities();

        // 允许下一次合法会话重新初始化
        initialized = false;
    }

    @NotNull
    public PlayerData getPlayerData() {
        return playerData;
    }

    //region Resolving Inventory

    public void watchVanillaSlot(@NotNull EquipmentSlot slot, Optional<ItemStack> newItem) {
        for (InventoryWatcher watcher : watchers)
            InventoryWatcher.callIfNotNull(watcher.watchVanillaSlot(slot, newItem), this::processUpdate);
    }

    public void watchInventory(int slotIndex, Optional<ItemStack> newItem) {
        for (InventoryWatcher watcher : watchers)
            InventoryWatcher.callIfNotNull(watcher.watchInventory(slotIndex, newItem), this::processUpdate);
    }

    public <T extends InventoryWatcher> void watch(Class<T> instanceOf, Function<T, ItemUpdate> action) {
        for (InventoryWatcher watcher : watchers)
            if (instanceOf.isInstance(watcher))
                InventoryWatcher.callIfNotNull(action.apply(instanceOf.cast(watcher)), this::processUpdate);
    }

    public void processUpdate(@NotNull ItemUpdate recorded) {

        // Register changes
        if (recorded.getOld() != null) unregisterItem(recorded.getOld());
        if (recorded.getNew() != null) registerItem(recorded.getNew());

        // Reset emcumbered status
        if (recorded.getEquipmentSlot().isHand()) encumbered = null;
    }

    @NotNull
    public Set<EquippedItem> getEquipped() {
        return activeItems;
    }

    public void resolveInventory() {
        final LoginRefreshSession session = LoginRefreshSession.get(playerData);
        if (session != null) {
            session.beginCycle();
            for (InventoryWatcher watcher : watchers) {
                final LoginRefreshSession.Phase phase = LoginRefreshSession.Phase.resolve(watcher);
                final Consumer<ItemUpdate> consumer = session.wrapConsumer(phase);
                watcher.watchAll(consumer);
                session.markPhaseReady(phase);
            }
            session.tryCommit(this);
            syncTimerInventoryAbilities();
            return;
        }

        playerData.getMMOPlayerData().getStatMap().bufferUpdates(() -> {
            for (InventoryWatcher watcher : watchers) watcher.watchAll(this::processUpdate);
        });
        syncTimerInventoryAbilities();
    }

    int unapplyAllItemModifiers() {
        int removed = 0;

        for (EquippedItem equippedItem : activeItems) {
            if (!equippedItem.getModifierCache().isEmpty()) {
                for (PlayerModifier modifier : new ArrayList<>(equippedItem.getModifierCache())) {
                    modifier.unregister(playerData.getMMOPlayerData());
                    removed++;
                }
                equippedItem.getModifierCache().clear();
            }
            equippedItem.applied = false;
        }

        if (!setModifierSupplier.getModifierCache().isEmpty()) {
            for (PlayerModifier modifier : new ArrayList<>(setModifierSupplier.getModifierCache())) {
                modifier.unregister(playerData.getMMOPlayerData());
                removed++;
            }
            setModifierSupplier.getModifierCache().clear();
        }

        itemSetCount.clear();
        return removed;
    }

    int countActiveModifiers() {
        int total = setModifierSupplier.getModifierCache().size();
        for (EquippedItem equippedItem : activeItems) {
            total += equippedItem.getModifierCache().size();
        }
        return total;
    }

    private void registerItem(@NotNull EquippedItem equippedItem) {
        Validate.isTrue(activeItems.add(equippedItem), "Item already registered");
        Bukkit.getPluginManager().callEvent(new ItemEquipEvent(playerData, equippedItem));
        callBackwardsCompatibleEvent();
        resolveModifiers(equippedItem);
    }

    private void unregisterItem(@NotNull EquippedItem unequippedItem) {
        Validate.isTrue(activeItems.remove(unequippedItem), "Item not found");
        Bukkit.getPluginManager().callEvent(new ItemUnequipEvent(playerData, unequippedItem));
        callBackwardsCompatibleEvent();
        if (unequippedItem.applied) unapplyModifiers(unequippedItem);
    }

    @BackwardsCompatibility(version = "6.10.1")
    @SuppressWarnings("deprecation")
    private void callBackwardsCompatibleEvent() {
        Bukkit.getPluginManager().callEvent(new RefreshInventoryEvent(this));
    }

    //endregion

    //region Resolving Modifiers

    public void resolveModifiers() {
        for (EquippedItem equippedItem : activeItems) {
            equippedItem.flushCache();
            resolveModifiers(equippedItem);
        }
    }

    private void resolveModifiers(@NotNull EquippedItem equippedItem) {
        boolean valid = equippedItem.isPlacementLegal() && equippedItem.isUsable(playerData.getRPG());
        if (valid && !equippedItem.applied) applyModifiers(equippedItem);
        else if (!valid && equippedItem.applied) unapplyModifiers(equippedItem);
    }

    public void clearItemModifiers(@NotNull EquippedItem equippedItem) {
        if (equippedItem.applied) unapplyModifiers(equippedItem);
    }

    private void applyModifiers(@NotNull EquippedItem equippedItem) {
        Validate.isTrue(!equippedItem.applied, "Item modifiers already applied");
        equippedItem.applied = true;

        final VolatileMMOItem item = equippedItem.reader();

        ///////////////////////////////////////
        // Abilities
        ///////////////////////////////////////
        if (item.hasData(ItemStats.ABILITIES))
            registerAbilities(equippedItem, ((AbilityListData) item.getData(ItemStats.ABILITIES)).getAbilities());

        ///////////////////////////////////////
        // Permanent potion effects
        ///////////////////////////////////////
        if (item.hasData(ItemStats.PERM_EFFECTS))
            registerPotionEffects(equippedItem, ((PotionEffectListData) item.getData(ItemStats.PERM_EFFECTS)).getEffects().stream().map(PotionEffectData::toEffect).collect(Collectors.toList()));

        ///////////////////////////////////////
        // Item particles
        ///////////////////////////////////////
        if (item.hasData(ItemStats.ITEM_PARTICLES)) {
            ParticleData particleData = (ParticleData) item.getData(ItemStats.ITEM_PARTICLES);
            registerParticleEffect(equippedItem, particleData);
        }

        ///////////////////////////////////////
        // Permissions (not using Vault)
        ///////////////////////////////////////
        if (MMOItems.plugin.getLanguage().itemGrantedPermissions && item.hasData(ItemStats.GRANTED_PERMISSIONS))
            registerPermissions(equippedItem, ((StringListData) item.getData(ItemStats.GRANTED_PERMISSIONS)).getList());

        ///////////////////////////////////////
        // Item Set
        ///////////////////////////////////////
        if (equippedItem.getSet() != null) {
            itemSetCount.merge(equippedItem.getSet(), 1, Integer::sum);
            resolveItemSet();
        }

        ///////////////////////////////////////
        // Numeric Stats
        ///////////////////////////////////////
        for (ItemStat<?, ?> stat : MMOItems.plugin.getStats().getNumericStats()) {

            try {
                // TODO MI7 do a full stat lookup and lookup stat by nbtpath
                double statValue = equippedItem.getItem().getStat(stat.getId());
                if (statValue == 0) continue;

                StatInstance statInstance = playerData.getMMOPlayerData().getStatMap().getInstance(stat.getId());
                final ModifierSource modifierSource = equippedItem.getModifierSource();

                // Apply hand weapon stat offset
                if (modifierSource.isWeapon() && stat instanceof WeaponBaseStat)
                    statValue = fixWeaponBase(statInstance, stat, statValue);

                StatModifier modifier = new StatModifier(MODIFIER_KEY, stat.getId(), statValue, ModifierType.FLAT, equippedItem.getEquipmentSlot(), modifierSource);
                statInstance.registerModifier(modifier);
                equippedItem.getModifierCache().add(modifier);

            } catch (ArrayIndexOutOfBoundsException ex) {
                // 记录越界异常并跳过该属性，避免影响整体解析
                MMOItems.plugin.getLogger().log(Level.SEVERE,
                        "应用数值属性时出现越界异常，已跳过该属性。stat=" + stat.getId()
                                + ", slot=" + equippedItem.getEquipmentSlot()
                                + ", source=" + equippedItem.getModifierSource()
                                + ", item=" + equippedItem,
                        ex);
            } catch (Throwable t) {
                // 兜底保护，防止单个属性异常中断流程
                MMOItems.plugin.getLogger().log(Level.SEVERE,
                        "应用数值属性时出现异常，已跳过该属性。stat=" + stat.getId()
                                + ", item=" + equippedItem,
                        t);
            }
        }
    }

    private double fixWeaponBase(StatInstance statInstance, ItemStat<?, ?> stat, double statValue) {
        @NotNull Optional<StatHandler> opt = MythicLib.plugin.getStats().getHandler(stat.getId());
        return opt.map(statHandler -> statValue - statHandler.getBaseValue(statInstance)).orElse(statValue);
    }

    private void registerPotionEffects(ModifierSupplier supplier, Collection<PotionEffect> effects) {
        for (PotionEffect bukkit : effects) {
            // TODO Support for slot and source
            PermanentPotionEffect modifier = new PermanentPotionEffect(MODIFIER_KEY, bukkit.getType(), bukkit.getAmplifier());
            modifier.register(playerData.getMMOPlayerData());
            supplier.getModifierCache().add(modifier);
        }
    }

    private void registerParticleEffect(ModifierSupplier supplier, ParticleData particleData) {
        // TODO Support for slot and source
        ParticleEffect modifier = particleData.toModifier(MODIFIER_KEY);
        modifier.register(playerData.getMMOPlayerData());
        supplier.getModifierCache().add(modifier);
    }

    private void registerPermissions(ModifierSupplier supplier, Collection<String> permissions) {
        for (String permission : permissions) {
            PermissionModifier modifier = new PermissionModifier(MODIFIER_KEY, permission, supplier.getEquipmentSlot(), supplier.getModifierSource());
            modifier.register(playerData.getMMOPlayerData());
            supplier.getModifierCache().add(modifier);
        }
    }

    private void registerAbilities(ModifierSupplier supplier, Collection<AbilityData> abilities) {
        for (AbilityData abilityData : abilities) {
            if (isTimerInventoryTrigger(abilityData.getTrigger().name()) && supplier.getEquipmentSlot() != EquipmentSlot.INVENTORY)
                continue;

            PassiveSkill modifier = new PassiveSkill(MODIFIER_KEY, abilityData, supplier.getEquipmentSlot(), supplier.getModifierSource());
            modifier.register(playerData.getMMOPlayerData());
            supplier.getModifierCache().add(modifier);
        }
    }

    private void unapplyModifiers(@NotNull EquippedItem equippedItem) {
        Validate.isTrue(equippedItem.applied, "Item modifiers not applied");
        equippedItem.applied = false;

        // Unload ALL modifiers
        equippedItem.getModifierCache().forEach(mod -> mod.unregister(playerData.getMMOPlayerData()));
        equippedItem.getModifierCache().clear(); // Clear cache!!!

        ///////////////////////////////////////
        // Item Set
        ///////////////////////////////////////
        if (equippedItem.getSet() != null) {
            itemSetCount.merge(equippedItem.getSet(), 0, (oldValue, value) -> oldValue - 1);
            resolveItemSet();
        }
    }

    private void resetItemSetModifiers() {
        setModifierSupplier.getModifierCache().forEach(mod -> mod.unregister(playerData.getMMOPlayerData()));
        setModifierSupplier.getModifierCache().clear();
    }

    // TODO make it not fully reset everything like a retard everytime
    private void resolveItemSet() {

        // 清理套装增益
        resetItemSetModifiers();

        // Reset and compute item set bonuses
        ItemSet.SetBonuses setBonuses = null;
        for (Map.Entry<ItemSet, Integer> equippedSetBonus : itemSetCount.entrySet()) {
            if (setBonuses == null)
                setBonuses = equippedSetBonus.getKey().getBonuses(equippedSetBonus.getValue()); // Set
            else setBonuses.merge(equippedSetBonus.getKey().getBonuses(equippedSetBonus.getValue()));  // Merge bonuses
        }

        // Apply set bonuses
        if (setBonuses != null) {
            registerAbilities(setModifierSupplier, setBonuses.getAbilities());
            registerPotionEffects(setModifierSupplier, setBonuses.getPotionEffects());
            for (ParticleData particle : setBonuses.getParticles())
                registerParticleEffect(setModifierSupplier, particle);
            registerPermissions(setModifierSupplier, setBonuses.getPermissions());
            setBonuses.getStats().forEach((stat, statValue) -> {
                StatModifier modifier = new StatModifier(MODIFIER_KEY, stat.getId(), statValue, ModifierType.FLAT, EquipmentSlot.OTHER, ModifierSource.OTHER);
                modifier.register(playerData.getMMOPlayerData());
                setModifierSupplier.getModifierCache().add(modifier);
            });
        }
    }

    public boolean isEncumbered() {
        if (encumbered != null) return encumbered;

        // Get the mainhand and offhand items.
        final NBTItem main = MythicLib.plugin.getVersion().getWrapper().getNBTItem(playerData.getPlayer().getInventory().getItemInMainHand());
        final NBTItem off = MythicLib.plugin.getVersion().getWrapper().getNBTItem(playerData.getPlayer().getInventory().getItemInOffHand());

        // Is either hand two-handed?
        final boolean mainhand_twohanded = main.getBoolean(ItemStats.TWO_HANDED.getNBTPath());
        final boolean offhand_twohanded = off.getBoolean(ItemStats.TWO_HANDED.getNBTPath());

        // Is either hand encumbering: Not NULL, not AIR, and not Handworn
        final boolean mainhand_encumbering = (main.getItem() != null && main.getItem().getType() != Material.AIR && !main.getBoolean(ItemStats.HANDWORN.getNBTPath()));
        final boolean offhand_encumbering = (off.getItem() != null && off.getItem().getType() != Material.AIR && !off.getBoolean(ItemStats.HANDWORN.getNBTPath()));

        // Will it encumber?
        return encumbered = ((mainhand_twohanded && offhand_encumbering) || (mainhand_encumbering && offhand_twohanded));
    }

    /**
     * 全量同步背包中的 TIMER_INVENTORY 技能注册状态。
     * 该方法带有槽位 hash 快速判定，仅在槽位变化时才更新注册。
     */
    public void syncTimerInventoryAbilities() {
        final int heldSlot = playerData.getPlayer().getInventory().getHeldItemSlot();
        if (heldSlot != lastHeldSlot) {
            if (lastHeldSlot >= 0 && lastHeldSlot < STORAGE_SLOT_COUNT)
                inventoryTimerDirtySlots[lastHeldSlot] = true;
            if (heldSlot >= 0 && heldSlot < STORAGE_SLOT_COUNT)
                inventoryTimerDirtySlots[heldSlot] = true;
            lastHeldSlot = heldSlot;
        }

        for (int slot = 0; slot < STORAGE_SLOT_COUNT; slot++)
            syncTimerInventorySlot(slot, Optional.empty());

        inventoryTimerInitialized = true;
    }

    /**
     * Queues a single inventory timer synchronization on next tick.
     * Multiple calls in the same tick are coalesced into one run.
     */
    public void requestTimerInventorySync() {
        if (inventoryTimerSyncQueued)
            return;

        inventoryTimerSyncQueued = true;
        Bukkit.getScheduler().runTask(MMOItems.plugin, () -> {
            inventoryTimerSyncQueued = false;
            if (!initialized || !playerData.getMMOPlayerData().isPlaying())
                return;

            syncTimerInventoryAbilities();
        });
    }

    /**
     * 增量同步指定背包槽位的 TIMER_INVENTORY 技能注册状态。
     *
     * @param slotIndex 槽位索引（0~35）
     * @param newItem   若提供则使用该物品，否则读取玩家当前背包槽位物品
     */
    public void syncTimerInventorySlot(int slotIndex, @NotNull Optional<ItemStack> newItem) {
        if (slotIndex < 0 || slotIndex >= STORAGE_SLOT_COUNT)
            return;

        final ItemStack stack = newItem.orElse(playerData.getPlayer().getInventory().getItem(slotIndex));
        final boolean air = UtilityMethods.isAir(stack);
        final int hash = air ? 0 : stack.hashCode();
        final Material type = air ? Material.AIR : stack.getType();
        final int amount = air ? 0 : stack.getAmount();

        // TIMER_INVENTORY 仅在背包中生效，主手当前选中槽位不算背包生效位
        if (slotIndex == playerData.getPlayer().getInventory().getHeldItemSlot()) {
            inventoryTimerSlotHashes[slotIndex] = hash;
            inventoryTimerSlotTypes[slotIndex] = type;
            inventoryTimerSlotAmounts[slotIndex] = amount;
            inventoryTimerDirtySlots[slotIndex] = false;
            clearTimerInventorySlot(slotIndex);
            return;
        }

        if (inventoryTimerInitialized && !inventoryTimerDirtySlots[slotIndex]
                && inventoryTimerSlotHashes[slotIndex] == hash
                && inventoryTimerSlotTypes[slotIndex] == type
                && inventoryTimerSlotAmounts[slotIndex] == amount)
            return;

        inventoryTimerSlotHashes[slotIndex] = hash;
        inventoryTimerSlotTypes[slotIndex] = type;
        inventoryTimerSlotAmounts[slotIndex] = amount;
        inventoryTimerDirtySlots[slotIndex] = false;
        clearTimerInventorySlot(slotIndex);

        if (UtilityMethods.isAir(stack))
            return;

        final NBTItem nbt = MythicLib.plugin.getVersion().getWrapper().getNBTItem(stack);
        final @Nullable String rawAbilities = nbt.getString(ItemStats.ABILITIES.getNBTPath());
        if (!isTimerInventoryRawAbilities(rawAbilities))
            return;

        final @Nullable Type itemType = Type.get(nbt);
        if (ENABLE_ORNAMENTS && itemType != null && itemType.getModifierSource() == ModifierSource.ORNAMENT)
            return;

        if (!playerData.getRPG().canUse(nbt, false, false))
            return;

        final VolatileMMOItem item = new VolatileMMOItem(nbt);
        if (!item.hasData(ItemStats.ABILITIES))
            return;

        final AbilityListData abilityList = (AbilityListData) item.getData(ItemStats.ABILITIES);
        if (abilityList == null || abilityList.getAbilities().isEmpty())
            return;

        final List<UUID> registered = new ArrayList<>();
        for (AbilityData abilityData : abilityList.getAbilities()) {
            if (!isTimerInventoryTrigger(abilityData.getTrigger().name()))
                continue;

            final PassiveSkill modifier = new PassiveSkill(INVENTORY_TIMER_MODIFIER_KEY, abilityData, EquipmentSlot.INVENTORY, ModifierSource.OTHER);
            modifier.register(playerData.getMMOPlayerData());
            registered.add(modifier.getUniqueId());
        }

        if (!registered.isEmpty())
            inventoryTimerSkillIds.put(slotIndex, registered);
    }

    public void clearTimerInventoryAbilities() {
        for (List<UUID> modifiers : inventoryTimerSkillIds.values())
            modifiers.forEach(uuid -> playerData.getMMOPlayerData().getPassiveSkillMap().removeModifier(uuid));

        inventoryTimerSkillIds.clear();
        Arrays.fill(inventoryTimerSlotHashes, 0);
        Arrays.fill(inventoryTimerSlotTypes, Material.AIR);
        Arrays.fill(inventoryTimerSlotAmounts, 0);
        Arrays.fill(inventoryTimerDirtySlots, false);
        inventoryTimerInitialized = false;
        lastHeldSlot = -1;
        inventoryTimerSyncQueued = false;
    }

    private void clearTimerInventorySlot(int slotIndex) {
        final List<UUID> modifiers = inventoryTimerSkillIds.remove(slotIndex);
        if (modifiers != null)
            modifiers.forEach(uuid -> playerData.getMMOPlayerData().getPassiveSkillMap().removeModifier(uuid));
    }

    private boolean isTimerInventoryTrigger(@NotNull String triggerName) {
        return TIMER_INVENTORY_TRIGGER_NAME.equals(triggerName) || TIMER_INVENTORY_TRIGGER_LEGACY_NAME.equals(triggerName);
    }

    private boolean isTimerInventoryRawAbilities(@Nullable String rawAbilities) {
        return rawAbilities != null && (rawAbilities.contains(TIMER_INVENTORY_TRIGGER_NAME) || rawAbilities.contains(TIMER_INVENTORY_TRIGGER_LEGACY_NAME));
    }

    //endregion
}
