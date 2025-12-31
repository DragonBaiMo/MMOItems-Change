package net.Indyuce.mmoitems.api.upgrade;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.manager.Reloadable;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 强化运行期配置缓存
 * <p>
 * 针对每次强化都会命中的配置（广播、自动绑定），在内存中做一次性解析，
 * 避免热路径重复访问 {@link FileConfiguration} 与构造临时集合。
 * </p>
 */
public class UpgradeRuntimeSettings implements Reloadable {
    private boolean broadcastEnabled;
    private Set<Integer> broadcastLevels = Collections.emptySet();
    private String broadcastTemplate;
    private String broadcastTemplateColored;

    private boolean autoBindEnabled;
    private int autoBindLevel;
    private boolean autoBindShowMessage;

    public UpgradeRuntimeSettings() {
        reload();
    }

    @Override
    public void reload() {
        FileConfiguration config = MMOItems.plugin.getConfig();

        // 广播配置
        broadcastEnabled = config.getBoolean("item-upgrading.broadcast.enabled", false);
        broadcastTemplate = config.getString("item-upgrading.broadcast.message",
                "&6[强化通报] &a{player} 的 &e{item}&a 强化到 &c+{level}&a 级！");
        broadcastTemplateColored = ChatColor.translateAlternateColorCodes('&', broadcastTemplate);
        broadcastLevels = parseBroadcastLevels(config.getList("item-upgrading.broadcast.levels"));

        // 自动绑定配置
        autoBindEnabled = config.getBoolean("item-upgrading.auto-bind-on-upgrade.enabled", false);
        autoBindLevel = clampLevel(config.getInt("item-upgrading.auto-bind-on-upgrade.level", 1));
        autoBindShowMessage = config.getBoolean("item-upgrading.auto-bind-on-upgrade.show-message", true);
    }

    public boolean shouldBroadcast(int level) {
        return broadcastEnabled && broadcastLevels.contains(level);
    }

    @NotNull
    public String formatBroadcast(@NotNull String playerName, @NotNull String itemName, int level) {
        String message = broadcastTemplate
                .replace("{player}", playerName)
                .replace("{item}", itemName)
                .replace("{level}", String.valueOf(level));
        return message;
    }

    @NotNull
    public String formatBroadcastColored(@NotNull String playerName, @NotNull String itemName, int level) {
        String message = broadcastTemplateColored
                .replace("{player}", playerName)
                .replace("{item}", itemName)
                .replace("{level}", String.valueOf(level));
        return message;
    }

    public boolean isAutoBindEnabled() {
        return autoBindEnabled;
    }

    public int getAutoBindLevel() {
        return autoBindLevel;
    }

    public boolean isAutoBindShowMessage() {
        return autoBindShowMessage;
    }

    private int clampLevel(int level) {
        return Math.max(1, Math.min(7, level));
    }

    @NotNull
    private Set<Integer> parseBroadcastLevels(List<?> levels) {
        if (levels == null || levels.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> parsed = new HashSet<>();
        for (Object obj : levels) {
            if (obj instanceof Number) {
                parsed.add(((Number) obj).intValue());
            }
        }
        return parsed;
    }
}
