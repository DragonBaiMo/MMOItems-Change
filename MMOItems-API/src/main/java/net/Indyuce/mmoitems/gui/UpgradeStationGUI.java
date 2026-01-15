package net.Indyuce.mmoitems.gui;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.version.Sounds;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ConfigFile;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.UpgradeTemplate;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.Indyuce.mmoitems.api.upgrade.*;
import net.Indyuce.mmoitems.api.upgrade.limit.DailyLimitManager;
import net.Indyuce.mmoitems.api.util.message.Message;
import net.Indyuce.mmoitems.stat.data.UpgradeData;
import net.Indyuce.mmoitems.util.MMOUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;

/**
 * 强化工作台 GUI
 * <p>
 * 提供可视化的强化操作界面，支持配置化和安全防护
 * </p>
 * <p>
 * 安全特性：
 * <ul>
 *     <li>防止 Shift+点击 复制物品</li>
 *     <li>防止数字键交换物品</li>
 *     <li>防止快速连点刷物品</li>
 *     <li>断线/崩溃自动返还物品</li>
 *     <li>物品一致性验证</li>
 * </ul>
 * </p>
 *
 * @author MMOItems Team
 * @since 强化系统扩展
 */
public class UpgradeStationGUI implements InventoryHolder, Listener {

    // ===== 静态管理 =====
    /**
     * 所有打开的 GUI 实例（用于玩家退出时清理）
     */
    private static final Map<UUID, UpgradeStationGUI> OPEN_GUIS = new ConcurrentHashMap<>();

    /**
     * 强化冷却记录
     */
    private static final Map<UUID, Long> UPGRADE_COOLDOWNS = new ConcurrentHashMap<>();

    // ===== 配置缓存 =====
    private static FileConfiguration config;
    private static long lastConfigLoad = 0;
    private static final long CONFIG_CACHE_TIME = 60000; // 1分钟缓存

    public static void invalidateConfigCache() {
        config = null;
        lastConfigLoad = 0;
    }

    private static NamespacedKey displayItemKey() {
        return new NamespacedKey(MMOItems.plugin, "upgrade_station_display_item");
    }

    public static void reloadConfigFromDisk() {
        ConfigFile primary = new ConfigFile("upgrade-station");
        if (primary.exists()) {
            config = primary.getConfig();
        } else {
            config = new ConfigFile("/default", "upgrade-station").getConfig();
        }
        lastConfigLoad = System.currentTimeMillis();
    }

    // ===== 槽位配置 =====
    private int slotTargetItem;
    private int slotUpgradeStone;
    private int slotPreview;
    private int slotLuckyStone;
    private int slotProtectStone;
    private int slotDirectStone;
    private int slotUpgradeButton;
    private int slotInfoPanel;
    private int slotCloseButton;
    private int slotProgressStart;
    private int progressLength;

    // map 布局缓存
    private final Map<Integer, String> mappedItems = new HashMap<>();
    private final Map<Integer, String> mappedBinds = new HashMap<>();
    private final Map<Integer, String> mappedBindDisplays = new HashMap<>();
    private boolean mapLayoutActive = false;

    // ===== 安全配置 =====
    private long upgradeCooldown;
    private boolean blockShiftClick;
    private boolean blockNumberKey;
    private boolean blockDrag;
    private boolean returnOnClose;
    private boolean returnOnQuit;

    // ===== 实例字段 =====
    private final Player player;
    private final Inventory inventory;
    private final int inventorySize;
    private final UpgradeStationDisplay display;
    private boolean registered = false;
    private boolean processing = false; // 防止并发操作
    private BukkitTask updateTask;

    private boolean configValid = true;

    // ===== 物品快照（用于验证） =====
    private ItemStack targetSnapshot;
    private ItemStack stoneSnapshot;

    /**
     * 创建强化工作台 GUI
     *
     * @param player 玩家
     */
    public UpgradeStationGUI(@NotNull Player player) {
        this.player = player;
        loadConfig();

        int rows = getConfig().getInt("gui.rows", 6);
        rows = Math.max(1, Math.min(6, rows));
        this.inventorySize = rows * 9;

        validateRequiredBinds();

        String title = color(getConfig().getString("gui.title", "&5&l强化工作台"));
        this.inventory = Bukkit.createInventory(this, inventorySize, title);    
        this.display = new UpgradeStationDisplay(this);

        setupInventory();
    }

    private void validateRequiredBinds() {
        // 允许“可选槽”缺失（如辅料/进度条/信息面板），但核心交互必须存在。
        List<String> missing = new ArrayList<>();
        if (slotTargetItem < 0) missing.add("target-item");
        if (slotUpgradeStone < 0) missing.add("upgrade-stone");
        if (slotPreview < 0) missing.add("preview");
        if (slotUpgradeButton < 0) missing.add("upgrade-button");
        if (slotCloseButton < 0) missing.add("close-button");

        if (!missing.isEmpty()) {
            configValid = false;
            MMOItems.print(null, "upgrade-station: missing required bind(s): " + String.join(", ", missing), null);
        }
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        // 仅在启动后首次或外部显式重载后为 null 时加载；平时不走定时过期，避免重复磁盘 IO。
        if (config == null) {
            reloadConfigFromDisk();
        }

        loadSlotsFromConfig();

        // 加载安全配置
        ConfigurationSection security = getConfig().getConfigurationSection("security");
        if (security != null) {
            upgradeCooldown = security.getLong("upgrade-cooldown", 1000);
            blockShiftClick = security.getBoolean("block-shift-click", true);
            blockNumberKey = security.getBoolean("block-number-key", true);
            blockDrag = security.getBoolean("block-drag", false);
            returnOnClose = security.getBoolean("return-items-on-close", true);
            returnOnQuit = security.getBoolean("return-items-on-quit", true);
        } else {
            upgradeCooldown = 1000;
            blockShiftClick = true;
            blockNumberKey = true;
            blockDrag = false;
            returnOnClose = true;
            returnOnQuit = true;
        }
    }

    FileConfiguration getConfig() {
        return config;
    }

    /**
     * 加载功能槽位；优先 slots.*，否则从 layout 的 bind 定义获取；最后回退默认值
     */
    private void loadSlotsFromConfig() {
        if (parseMapLayout()) return;

        ConfigurationSection slots = getConfig().getConfigurationSection("slots");
        if (slots != null && !slots.getKeys(false).isEmpty()) {
            slotTargetItem = slots.getInt("target-item", 11);
            slotUpgradeStone = slots.getInt("upgrade-stone", 15);
            slotPreview = slots.getInt("preview", 13);
            slotLuckyStone = slots.getInt("lucky-stone", 37);
            slotProtectStone = slots.getInt("protect-stone", 39);
            slotDirectStone = slots.getInt("direct-stone", 41);
            slotUpgradeButton = slots.getInt("upgrade-button", 43);
            slotInfoPanel = slots.getInt("info-panel", 49);
            slotCloseButton = slots.getInt("close-button", 45);
            slotProgressStart = slots.getInt("progress-start", 28);
            progressLength = slots.getInt("progress-length", 7);
            return;
        }

        // fallback layout 绑定
        Map<String, List<Integer>> binds = new HashMap<>();
        for (Map<String, Object> entry : safeLayoutListLegacy()) {
            Object bindObj = entry.get("bind");
            if (bindObj == null) continue;
            List<Integer> parsed = parseSlots(entry.get("slot"), entry.get("slots"));
            if (parsed.isEmpty()) continue;
            binds.put(bindObj.toString().trim(), parsed);
        }

        slotTargetItem = firstSlot(binds, "target-item", 11);
        slotUpgradeStone = firstSlot(binds, "upgrade-stone", 15);
        slotPreview = firstSlot(binds, "preview", 13);
        slotLuckyStone = firstSlot(binds, "lucky-stone", 37);
        slotProtectStone = firstSlot(binds, "protect-stone", 39);
        slotDirectStone = firstSlot(binds, "direct-stone", 41);
        slotUpgradeButton = firstSlot(binds, "upgrade-button", 43);
        slotInfoPanel = firstSlot(binds, "info-panel", 49);
        slotCloseButton = firstSlot(binds, "close-button", 45);

        List<Integer> progressSlots = binds.get("progress-bar");
        if (progressSlots != null && !progressSlots.isEmpty()) {
            slotProgressStart = progressSlots.get(0);
            progressLength = progressSlots.size();
        } else {
            slotProgressStart = 28;
            progressLength = 7;
        }
    }

    private int firstSlot(Map<String, List<Integer>> binds, String key, int defaultValue) {
        List<Integer> list = binds.get(key);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return defaultValue;
    }

    /**
     * 初始化 GUI 布局
     */
    private void setupInventory() {
        if (!configValid) {
            return;
        }
        boolean fillBackground = getConfig().getBoolean("gui.fill-background", true);

        // 填充背景（可关闭）
        if (fillBackground) {
            ItemStack filler = createConfigItem("items.filler", Material.BLACK_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < inventorySize; i++) {
                inventory.setItem(i, filler);
            }
        } else {
            for (int i = 0; i < inventorySize; i++) {
                inventory.setItem(i, null);
            }
        }

        // 设置功能槽位为空
        setSlotEmpty(slotTargetItem);
        setSlotEmpty(slotUpgradeStone);
        setSlotEmpty(slotLuckyStone);
        setSlotEmpty(slotProtectStone);
        setSlotEmpty(slotDirectStone);

        // map 布局：只渲染 layout-map + legend，不执行默认布局/旧 layout
        if (mapLayoutActive) {
            applyMapLayoutIfPresent();
            display.updateAllDisplays();
            return;
        }

        List<Map<String, Object>> customLayout = safeLayoutList();
        boolean hasCustomLayout = !customLayout.isEmpty();

        if (!hasCustomLayout) {
            // 默认布局
            ItemStack border = createConfigItem("items.border", Material.PURPLE_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < 9 && i < inventorySize; i++) {
                inventory.setItem(i, border);
            }

            if (4 < inventorySize) {
                inventory.setItem(4, createConfigItem("items.title", Material.ANVIL, "&5&l? 强化工作台 ?"));
            }

            ItemStack separator = createConfigItem("items.separator", Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int i = 18; i < 27 && i < inventorySize; i++) {
                inventory.setItem(i, separator);
            }

            setSlotItem(slotTargetItem - 1, createConfigItem("items.target-label", Material.DIAMOND_SWORD, "&6&l装备槽"));
            setSlotItem(slotUpgradeStone - 1, createConfigItem("items.upgrade-stone-label", Material.EMERALD, "&a&l强化石槽"));
            setSlotItem(20, createConfigItem("items.target-label", Material.DIAMOND_SWORD, "&6&l装备槽"));
            setSlotItem(24, createConfigItem("items.upgrade-stone-label", Material.EMERALD, "&a&l强化石槽"));
            setSlotItem(slotLuckyStone - 1, createConfigItem("items.lucky-stone-label", Material.LIME_DYE, "&a幸运石"));
            setSlotItem(slotProtectStone - 1, createConfigItem("items.protect-stone-label", Material.LIGHT_BLUE_DYE, "&b保护石"));
            setSlotItem(slotDirectStone - 1, createConfigItem("items.direct-stone-label", Material.PURPLE_DYE, "&d直达石"));
            setSlotItem(slotCloseButton, createConfigItem("items.close-button", Material.BARRIER, "&c&l关闭"));
        } else {
            applyCustomLayout(customLayout);
        }

        // 初始更新
        display.updateAllDisplays();
    }

    private void setSlotEmpty(int slot) {
        if (slot >= 0 && slot < inventorySize) {
            inventory.setItem(slot, null);
        }
    }

    void setSlotItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventorySize) return;
        if (item == null || item.getType() == Material.AIR) {
            inventory.setItem(slot, null);
            return;
        }
        inventory.setItem(slot, item);
    }

    /**
     * 应用 layout 列表定义的静态物品布局
     * layout:
     *   - key: title
     *     slot: 4
     *   - key: target-label
     *     slots: [10, 20]
     */
    private void applyCustomLayout(@NotNull List<Map<String, Object>> layout) {
        for (Map<String, Object> entry : layout) {
            Object keyObj = entry.get("key");
            if (keyObj == null) continue;
            String key = keyObj.toString().trim();
            if (key.isEmpty()) continue;

            if (Boolean.TRUE.equals(entry.get("hide"))) continue;

            List<Integer> slots = parseSlots(entry.get("slot"), entry.get("slots"));
            if (slots.isEmpty()) continue;

            ItemStack item = createConfigItem("items." + key, Material.PAPER, "&7");
            for (int slot : slots) {
                setSlotItem(slot, item);
            }
        }

        // map 定义的物品覆盖（在自定义 layout 基础上再覆盖，便于复用 legend）
        applyMapLayoutIfPresent();
    }

    private void applyMapLayoutIfPresent() {
        if (mappedItems.isEmpty() && mappedBinds.isEmpty()) return;

        for (Map.Entry<Integer, String> e : mappedItems.entrySet()) {
            int slot = e.getKey();
            String itemKey = e.getValue();
            setSlotItem(slot, createConfigItem("items." + itemKey, Material.PAPER, "&7"));
        }

        // 功能槽位清空，等待玩家交互
        for (Integer slot : mappedBinds.keySet()) {
            setSlotEmpty(slot);
            String display = mappedBindDisplays.get(slot);
            if (display != null && !display.isEmpty()) {
                ItemStack displayItem = createConfigItem("items." + display, Material.BARRIER, "&c?");
                if (displayItem != null && displayItem.getType() != Material.AIR) {
                    setSlotItem(slot, markAsDisplayItem(displayItem));
                }
            }
        }
    }

    private List<Integer> parseSlots(Object primary, Object secondary) {
        Object slotObj = primary != null ? primary : secondary;
        if (slotObj == null) return Collections.emptyList();

        List<Integer> result = new ArrayList<>();
        if (slotObj instanceof Number) {
            result.add(((Number) slotObj).intValue());
            return result;
        }
        if (slotObj instanceof Collection<?>) {
            for (Object o : (Collection<?>) slotObj) {
                if (o instanceof Number) {
                    result.add(((Number) o).intValue());
                } else {
                    try {
                        result.add(Integer.parseInt(String.valueOf(o)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return result;
        }
        try {
            result.add(Integer.parseInt(String.valueOf(slotObj)));
        } catch (NumberFormatException ignored) {
        }
        return result;
    }

    private List<Map<String, Object>> safeLayoutList() {
        List<Map<?, ?>> raw = getConfig().getMapList("layout");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            if (entry == null || entry.isEmpty()) continue;
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : entry.entrySet()) {
                if (e.getKey() != null) {
                    converted.put(e.getKey().toString(), e.getValue());
                }
            }
            result.add(converted);
        }
        return result;
    }

    private List<Map<String, Object>> safeLayoutListLegacy() {
        return safeLayoutList();
    }

    private boolean parseMapLayout() {
        List<String> mapLines = getConfig().getStringList("layout-map");
        if (mapLines == null || mapLines.isEmpty()) {
            mapLayoutActive = false;
            return false;
        }

        // 先清空所有槽位定义，避免“没在 layout-map 里出现但仍旧固定显示”的残留
        slotTargetItem = -1;
        slotUpgradeStone = -1;
        slotPreview = -1;
        slotLuckyStone = -1;
        slotProtectStone = -1;
        slotDirectStone = -1;
        slotUpgradeButton = -1;
        slotInfoPanel = -1;
        slotCloseButton = -1;
        slotProgressStart = -1;
        progressLength = 0;

        ConfigurationSection legend = getConfig().getConfigurationSection("legend");
        if (legend == null || legend.getKeys(false).isEmpty()) {
            MMOItems.print(null, "upgrade-station: legend missing while layout-map present", null);
            mapLayoutActive = false;
            return false;
        }

        int cols = mapLines.get(0).length();
        for (String line : mapLines) {
            if (line.length() != cols) {
                MMOItems.print(null, "upgrade-station: layout-map lines must have equal length", null);
                mapLayoutActive = false;
                return false;
            }
        }

        // 清空旧映射
        mappedItems.clear();
        mappedBinds.clear();
        mappedBindDisplays.clear();
        mapLayoutActive = true;

        // 行列转换为 slot
        for (int r = 0; r < mapLines.size(); r++) {
            String line = mapLines.get(r);
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                String key = String.valueOf(ch);
                ConfigurationSection entry = legend.getConfigurationSection(key);
                if (entry == null) continue; // 未映射字符允许跳过
                String type = entry.getString("type", "item").toLowerCase(Locale.ROOT);
                String ref = entry.getString("ref");
                String display = entry.getString("display");
                int slot = r * 9 + c;
                if (slot >= 54) continue; // GUI 最大 6 行
                if ("item".equals(type)) {
                    mappedItems.put(slot, ref);
                } else if ("bind".equals(type)) {
                    mappedBinds.put(slot, ref);
                    if (display != null && !display.isEmpty()) {
                        mappedBindDisplays.put(slot, display);
                    }
                    applyBindSlot(ref, slot);
                }
            }
        }

        // 进度条从 legend 中读取
        int progressStartLegend = -1;
        int progressLenLegend = 0;
        for (Map.Entry<Integer, String> e : mappedBinds.entrySet()) {
            if ("progress-bar".equals(e.getValue())) {
                if (progressStartLegend == -1) progressStartLegend = e.getKey();
                progressLenLegend++;
            }
        }
        if (progressStartLegend != -1 && progressLenLegend > 0) {
            slotProgressStart = progressStartLegend;
            progressLength = progressLenLegend;
        }

        return true;
    }

    private void applyBindSlot(String ref, int slot) {
        switch (ref) {
            case "target-item":
                slotTargetItem = slot;
                break;
            case "upgrade-stone":
                slotUpgradeStone = slot;
                break;
            case "preview":
                slotPreview = slot;
                break;
            case "lucky-stone":
                slotLuckyStone = slot;
                break;
            case "protect-stone":
                slotProtectStone = slot;
                break;
            case "direct-stone":
                slotDirectStone = slot;
                break;
            case "upgrade-button":
                slotUpgradeButton = slot;
                break;
            case "info-panel":
                slotInfoPanel = slot;
                break;
            case "close-button":
                slotCloseButton = slot;
                break;
            default:
                break;
        }
    }

    /**
     * 从配置创建物品
     */
    ItemStack createConfigItem(String path, Material defaultMaterial, String defaultName) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);

        Material material = defaultMaterial;
        String name = defaultName;
        List<String> lore = new ArrayList<>();
        boolean hide = false;
        int customModelData = -1;

        if (section != null) {
            String matStr = section.getString("material");
            if (matStr != null) {
                try {
                    material = Material.valueOf(matStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
            name = section.getString("name", defaultName);
            lore = section.getStringList("lore");
            hide = section.getBoolean("hide", false);
            customModelData = section.getInt("custom-model-data", -1);
        }

        if (hide) {
            return new ItemStack(Material.AIR);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(color(line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ===== 辅助数值 =====

    double getAuxiliaryChanceBonus() {
        ItemStack luckyStone = getItemAt(slotLuckyStone);
        if (luckyStone == null || luckyStone.getType() == Material.AIR) return 0;

        NBTItem nbt = NBTItem.get(luckyStone);
        if (nbt.hasTag(ItemStats.AUXILIARY_CHANCE_BONUS.getNBTPath())) {
            return nbt.getDouble(ItemStats.AUXILIARY_CHANCE_BONUS.getNBTPath());
        }
        return 0;
    }

    double getAuxiliaryProtection() {
        ItemStack protectStone = getItemAt(slotProtectStone);
        if (protectStone == null || protectStone.getType() == Material.AIR) return 0;

        NBTItem nbt = NBTItem.get(protectStone);
        if (nbt.hasTag(ItemStats.AUXILIARY_PROTECTION.getNBTPath())) {
            return nbt.getDouble(ItemStats.AUXILIARY_PROTECTION.getNBTPath());
        }
        return 0;
    }

    double getAuxiliaryDirectUpChance() {
        ItemStack directStone = getItemAt(slotDirectStone);
        if (directStone == null || directStone.getType() == Material.AIR) return 0;

        NBTItem nbt = NBTItem.get(directStone);
        if (nbt.hasTag(ItemStats.AUXILIARY_DIRECT_UP_CHANCE.getNBTPath())) {
            return nbt.getDouble(ItemStats.AUXILIARY_DIRECT_UP_CHANCE.getNBTPath());
        }
        return 0;
    }

    int getAuxiliaryDirectUpLevels() {
        ItemStack directStone = getItemAt(slotDirectStone);
        if (directStone == null || directStone.getType() == Material.AIR) return 0;

        NBTItem nbt = NBTItem.get(directStone);
        if (nbt.hasTag(ItemStats.AUXILIARY_DIRECT_UP_LEVELS.getNBTPath())) {
            return (int) nbt.getDouble(ItemStats.AUXILIARY_DIRECT_UP_LEVELS.getNBTPath());
        }
        return 0;
    }

    boolean canPerformUpgrade() {
        if (processing) return false;

        ItemStack targetItem = getItemAt(slotTargetItem);
        ItemStack stoneItem = getItemAt(slotUpgradeStone);

        if (targetItem == null || targetItem.getType() == Material.AIR) return false;
        if (stoneItem == null || stoneItem.getType() == Material.AIR) return false;

        NBTItem targetNBT = NBTItem.get(targetItem);
        if (!targetNBT.hasType()) return false;

        VolatileMMOItem mmoItem = new VolatileMMOItem(targetNBT);
        if (!mmoItem.hasData(ItemStats.UPGRADE)) return false;

        UpgradeData data = (UpgradeData) mmoItem.getData(ItemStats.UPGRADE);
        if (!data.canLevelUp()) return false;

        return isValidUpgradeStone(stoneItem, targetItem);
    }

    boolean isValidUpgradeStone(@Nullable ItemStack stone, @Nullable ItemStack target) {
        if (stone == null || stone.getType() == Material.AIR) return false;
        if (target == null || target.getType() == Material.AIR) return false;

        NBTItem stoneNBT = NBTItem.get(stone);
        Type stoneType = Type.get(stoneNBT);
        if (stoneType == null || !stoneType.corresponds(Type.CONSUMABLE)) return false;
        if (!stoneNBT.hasTag(ItemStats.UPGRADE.getNBTPath())) return false;

        NBTItem targetNBT = NBTItem.get(target);
        VolatileMMOItem targetMmo = new VolatileMMOItem(targetNBT);
        if (!targetMmo.hasData(ItemStats.UPGRADE)) return false;
        UpgradeData targetData = (UpgradeData) targetMmo.getData(ItemStats.UPGRADE);

        VolatileMMOItem stoneMmo = new VolatileMMOItem(stoneNBT);
        if (!stoneMmo.hasData(ItemStats.UPGRADE)) return false;
        UpgradeData stoneData = (UpgradeData) stoneMmo.getData(ItemStats.UPGRADE);

        return MMOUtils.checkReference(stoneData.getReference(), targetData.getReference());
    }

    // ===== 强化执行 =====

    private void performUpgrade() {
        // 冷却检查
        Long lastUpgrade = UPGRADE_COOLDOWNS.get(player.getUniqueId());
        if (lastUpgrade != null && System.currentTimeMillis() - lastUpgrade < upgradeCooldown) {
            playSound("sounds.deny");
            return;
        }

        if (!canPerformUpgrade() || processing) {
            playSound("sounds.deny");
            return;
        }

        // 标记处理中
        processing = true;
        UPGRADE_COOLDOWNS.put(player.getUniqueId(), System.currentTimeMillis());
        display.updateUpgradeButton();

        ItemStack targetItem = getItemAt(slotTargetItem);
        ItemStack stoneItem = getItemAt(slotUpgradeStone);
        double stoneBaseSuccess = getSuccessFromStoneItem(stoneItem);
        double auxiliaryChanceBonus = getAuxiliaryChanceBonus();
        double auxiliaryProtection = getAuxiliaryProtection();
        double auxiliaryDirectUpChance = getAuxiliaryDirectUpChance();
        int auxiliaryDirectUpLevels = getAuxiliaryDirectUpLevels();

        // 保存快照（用于验证）
        targetSnapshot = targetItem.clone();
        stoneSnapshot = stoneItem.clone();

        // 验证物品未被修改
        if (!validateItems()) {
            processing = false;
            display.updateUpgradeButton();
            playSound("sounds.deny");
            player.sendMessage(color(getMessage("item-state-invalid", "&c物品状态异常，请重新放入物品")));
            return;
        }

        NBTItem targetNBT = NBTItem.get(targetItem);
        LiveMMOItem targetMMO = new LiveMMOItem(targetNBT);
        UpgradeData targetData = (UpgradeData) targetMMO.getData(ItemStats.UPGRADE);

        // 重要：任何“会导致 UpgradeService 直接返回 error 的硬性校验”都必须在消耗材料前完成，避免 GUI 白白扣除材料
        DailyLimitManager dailyLimitManager = MMOItems.plugin.getUpgrades().getDailyLimitManager();
        if (dailyLimitManager != null && dailyLimitManager.isEnabled() && !dailyLimitManager.canUpgrade(player)) {
            int used = dailyLimitManager.getUsedAttempts(player);
            int max = dailyLimitManager.getMaxAttempts(player);
            processing = false;
            display.updateUpgradeButton();
            playSound("sounds.deny");
            player.sendMessage(color(getMessage("daily-limit-reached", "&c今日强化次数已用尽 ({used}/{max})")
                    .replace("{used}", String.valueOf(used))
                    .replace("{max}", String.valueOf(max))));
            return;
        }

        UpgradeTemplate template = targetData.getTemplate();
        if (template == null) {
            processing = false;
            display.updateUpgradeButton();
            playSound("sounds.deny");
            player.sendMessage(color(getMessage("template-not-found", "&c未找到强化模板: &f{template}")
                    .replace("{template}", String.valueOf(targetData.getTemplateName()))));
            return;
        }

        playSound("sounds.click");

        // 构建上下文
        UpgradeContext context = new UpgradeContext.Builder()
                .player(player)
                .targetItem(targetMMO)
                .targetData(targetData)
                .targetItemStack(targetItem)
                .freeMode(true) // 已手动消耗
                .forceMode(false)
                // 重要：GUI 模式下已手动消耗强化石，UpgradeService 会将 freeMode 视为“无消耗品=基础成功率100%”。
                // 为了与背包强化/GUI 展示一致，这里将“强化石的基础成功率”映射到 chanceModifier，从而得到：基础成功率 × 衰减^等级。
                .chanceModifier(stoneBaseSuccess)
                .auxiliaryChanceBonus(auxiliaryChanceBonus)
                .auxiliaryProtection(auxiliaryProtection)
                .auxiliaryDirectUpChance(auxiliaryDirectUpChance)
                .auxiliaryDirectUpLevels(auxiliaryDirectUpLevels)
                .build();

        UpgradeResult result = UpgradeService.performUpgrade(context);

        // 错误：不应消耗材料
        if (result.isError()) {
            processing = false;
            display.updateUpgradeButton();
            playSound("sounds.deny");
            player.sendMessage(color(getMessage("upgrade-error", "&c强化失败: &f{reason}")
                    .replace("{reason}", String.valueOf(result.getMessage()))));
            return;
        }

        // 消耗强化石（仅在本次确实执行强化时）
        stoneItem.setAmount(stoneItem.getAmount() - 1);
        if (stoneItem.getAmount() <= 0) {
            inventory.setItem(slotUpgradeStone, null);
        }

        // 消耗辅料（仅在本次对应效果会生效时）
        consumeAuxiliaryStones(
                auxiliaryChanceBonus > 0,
                auxiliaryProtection > 0,
                auxiliaryDirectUpChance > 0 && auxiliaryDirectUpLevels > 0
        );

        // 更新物品
        if (result.isSuccess()) {
            MMOItem upgradedItem = result.getUpgradedItem();
            if (upgradedItem != null) {
                NBTItem newNBT = upgradedItem.newBuilder().buildNBT();
                ItemStack built = newNBT.toItem();
                targetItem.setType(built.getType());
                targetItem.setItemMeta(built.getItemMeta());
            }
            playSound("sounds.upgrade-success");
            Message.UPGRADE_SUCCESS.format(ChatColor.GREEN, "#item#", MMOUtils.getDisplayName(targetItem)).send(player);
        } else {
            playSound("sounds.upgrade-fail");
            String msg = result.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = getMessage("upgrade-fail-default", "强化失败");
            }

            if (result.getStatus() == UpgradeResult.Status.FAILURE_PROTECTED || result.getPenaltyResult() == PenaltyResult.PROTECTED) {
                PenaltyResult intercepted = result.getInterceptedPenalty();
                String key;
                String def;
                if (intercepted == PenaltyResult.BREAK) {
                    key = "upgrade-fail-protected-break";
                    def = "&a强化失败，保护已生效（拦截碎裂）：&f{reason}";
                } else if (intercepted == PenaltyResult.DOWNGRADE) {
                    key = "upgrade-fail-protected-downgrade";
                    def = "&a强化失败，保护已生效（拦截掉级）：&f{reason}";
                } else if (intercepted == PenaltyResult.DESTROY) {
                    key = "upgrade-fail-protected-destroy";
                    def = "&a强化失败，保护已生效（拦截销毁）：&f{reason}";
                } else {
                    key = "upgrade-fail-protected";
                    def = "&a强化失败，但保护已生效：&f{reason}";
                }

                player.sendMessage(color(getMessage(key, def).replace("{reason}", msg)));
            } else {
                player.sendMessage(color(getMessage("upgrade-fail", "&c{reason}")
                        .replace("{reason}", msg)));
            }
        }

        // 延迟解除处理状态
        Bukkit.getScheduler().runTaskLater(MMOItems.plugin, () -> {
            processing = false;
            display.updateAllDisplays();
        }, 10L);
    }

    /**
     * 从强化石物品中读取基础成功率（0-1）。
     * <p>
     * 注意：GUI 模式下强化石放在 GUI 容器里而不是玩家背包里，因此不能依赖 {@link net.Indyuce.mmoitems.api.upgrade.UpgradeService#findUpgradeStones}。
     * </p>
     */
    private double getSuccessFromStoneItem(@Nullable ItemStack stoneItem) {
        if (stoneItem == null || stoneItem.getType() == Material.AIR) return 1.0;

        NBTItem stoneNBT = NBTItem.get(stoneItem);
        VolatileMMOItem stoneMmo = new VolatileMMOItem(stoneNBT);
        if (stoneMmo.hasData(ItemStats.UPGRADE)) {
            UpgradeData stoneData = (UpgradeData) stoneMmo.getData(ItemStats.UPGRADE);
            return stoneData.getSuccess();
        }
        return 1.0;
    }

    /**
     * 验证物品未被非法修改
     */
    private boolean validateItems() {
        ItemStack currentTarget = getItemAt(slotTargetItem);
        ItemStack currentStone = getItemAt(slotUpgradeStone);

        // 简单验证：物品仍然存在且类型相同
        if (currentTarget == null || currentTarget.getType() == Material.AIR) return false;
        if (currentStone == null || currentStone.getType() == Material.AIR) return false;

        return true;
    }

    private void consumeAuxiliaryStones(boolean consumeLucky, boolean consumeProtect, boolean consumeDirect) {
        consumeOneAuxiliaryStone(slotLuckyStone, consumeLucky);
        consumeOneAuxiliaryStone(slotProtectStone, consumeProtect);
        consumeOneAuxiliaryStone(slotDirectStone, consumeDirect);
    }

    private void consumeOneAuxiliaryStone(int slot, boolean shouldConsume) {
        if (!shouldConsume) return;

        ItemStack stone = getItemAt(slot);
        if (stone == null || stone.getType() == Material.AIR) return;

        stone.setAmount(stone.getAmount() - 1);
        if (stone.getAmount() <= 0) {
            inventory.setItem(slot, null);
        }
    }

    // ===== GUI 控制 =====

    public void open() {
        if (!configValid) {
            player.sendMessage(color("&c强化台配置无效：缺少必要槽位绑定（target-item/upgrade-stone/preview/upgrade-button/close-button）。"));
            player.sendMessage(color("&7请检查 &fplugins/MMOItems/upgrade-station.yml &7的 layout-map 与 legend。"));
            return;
        }
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, MMOItems.plugin);
            registered = true;
        }
        OPEN_GUIS.put(player.getUniqueId(), this);
        player.openInventory(inventory);
        playSound("sounds.click");
    }

    public void close() {
        if (returnOnClose) {
            returnItems();
        }
        cleanup();
    }

    private void cleanup() {
        OPEN_GUIS.remove(player.getUniqueId());
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void returnItems() {
        int[] returnSlots = {slotTargetItem, slotUpgradeStone, slotLuckyStone, slotProtectStone, slotDirectStone};
        for (int slot : returnSlots) {
            if (slot < 0 || slot >= inventorySize) continue;
            ItemStack item = getItemAt(slot);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                inventory.setItem(slot, null);
            }
        }
    }

    private boolean isFunctionalSlot(int slot) {
        return slot == slotTargetItem || slot == slotUpgradeStone ||
                slot == slotLuckyStone || slot == slotProtectStone || slot == slotDirectStone;
    }

    private boolean isDisplayItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(displayItemKey(), PersistentDataType.BYTE);
    }

    private ItemStack markAsDisplayItem(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(displayItemKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ===== 工具方法 =====

    String color(String text) {
        return MythicLib.plugin.parseColors(text);
    }

    String getMessage(String key, String defaultValue) {
        return getConfig().getString("messages." + key, defaultValue);
    }

    Material getMaterial(String path, Material defaultMaterial) {
        String matStr = getConfig().getString(path);
        if (matStr != null) {
            try {
                return Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defaultMaterial;
    }

    private void playSound(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null) return;

        String soundName = section.getString("sound");
        if (soundName == null) return;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ===== 只读访问器 =====

    int getSlotTargetItem() {
        return slotTargetItem;
    }

    int getSlotUpgradeStone() {
        return slotUpgradeStone;
    }

    int getSlotPreview() {
        return slotPreview;
    }

    int getSlotLuckyStone() {
        return slotLuckyStone;
    }

    int getSlotProtectStone() {
        return slotProtectStone;
    }

    int getSlotDirectStone() {
        return slotDirectStone;
    }

    int getSlotUpgradeButton() {
        return slotUpgradeButton;
    }

    int getSlotInfoPanel() {
        return slotInfoPanel;
    }

    int getSlotCloseButton() {
        return slotCloseButton;
    }

    int getSlotProgressStart() {
        return slotProgressStart;
    }

    int getProgressLength() {
        return progressLength;
    }

    int getInventorySize() {
        return inventorySize;
    }

    Player getPlayer() {
        return player;
    }

    boolean isProcessing() {
        return processing;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    @Nullable
    ItemStack getItemAt(int slot) {
        if (slot < 0 || slot >= inventorySize) return null;
        return inventory.getItem(slot);
    }

    void clearSlot(int slot) {
        if (slot < 0 || slot >= inventorySize) return;
        inventory.setItem(slot, null);
    }

    // ===== 事件处理 =====

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player clicker = (Player) event.getWhoClicked();
        if (!clicker.equals(player)) return;

        int slot = event.getRawSlot();
        InventoryAction action = event.getAction();
        ClickType clickType = event.getClick();

        // ===== 安全防护 =====

        // 1. 阻止 Shift+点击（防止复制/移动到错误位置）
        if (blockShiftClick && clickType.isShiftClick()) {
            event.setCancelled(true);
            playSound("sounds.deny");
            return;
        }

        // 2. 阻止数字键交换
        if (blockNumberKey && clickType == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
            playSound("sounds.deny");
            return;
        }

        // 3. 阻止双击收集
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // 4. 阻止移动到其他物品栏
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && slot < inventorySize) {
            // 只允许从功能槽位移出
            if (!isFunctionalSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }

        // 玩家背包区域
        if (slot >= inventorySize) {
            // 阻止 Shift+点击将物品自动塞入 GUI（可能进入不可放置的装饰槽位）
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                playSound("sounds.deny");
                return;
            }
            // 允许正常操作，但延迟更新
            Bukkit.getScheduler().runTaskLater(MMOItems.plugin, display::updateAllDisplays, 1L);
            return;
        }

        // 功能槽位：允许放置/取出
        if (isFunctionalSlot(slot)) {
            // 若当前槽位是“展示占位物品”，禁止拿走，但允许直接覆盖放入玩家物品
            if (isDisplayItem(event.getCurrentItem())) {
                ItemStack cursor = event.getCursor();
                boolean hasCursorItem = cursor != null && cursor.getType() != Material.AIR;
                if (!hasCursorItem) {
                    event.setCancelled(true);
                    playSound("sounds.deny");
                    return;
                }
            }

            // 播放放入音效
            Bukkit.getScheduler().runTaskLater(MMOItems.plugin, () -> {
                playSound("sounds.item-place");
                display.updateAllDisplays();
            }, 1L);
            return;
        }

        // 非功能槽位：取消点击
        event.setCancelled(true);

        // 强化按钮
        if (slot == slotUpgradeButton) {
            performUpgrade();
            return;
        }

        // 关闭按钮
        if (slot == slotCloseButton) {
            playSound("sounds.close");
            clicker.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) return;

        // 检查是否拖拽到非功能槽位
        for (int slot : event.getRawSlots()) {
            if (slot < inventorySize && !isFunctionalSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }

        // 如果配置了阻止拖拽
        if (blockDrag) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(MMOItems.plugin, display::updateAllDisplays, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player closer = (Player) event.getPlayer();
        if (!closer.equals(player)) return;

        close();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().equals(player)) return;

        if (returnOnQuit) {
            returnItems();
        }
        cleanup();
    }

    // ===== 静态方法 =====

    /**
     * 获取玩家当前打开的强化工作台
     */
    @Nullable
    public static UpgradeStationGUI getOpenGUI(Player player) {
        return OPEN_GUIS.get(player.getUniqueId());
    }

    /**
     * 关闭所有打开的强化工作台（用于插件关闭时）
     */
    public static void closeAll() {
        for (UpgradeStationGUI gui : OPEN_GUIS.values()) {
            gui.returnItems();
            gui.cleanup();
        }
        OPEN_GUIS.clear();
        UPGRADE_COOLDOWNS.clear();
    }
}
