package net.Indyuce.mmoitems.comp.mmoinventory;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.comp.mmoinventory.stat.AccessorySet;
import org.bukkit.Bukkit;

public class MMOInventorySupport {
    public MMOInventorySupport() {
        MMOItems.plugin.getStats().register(new AccessorySet());

        // 注册左键点击装卸饰品监听器
        Bukkit.getPluginManager().registerEvents(new MMOInventoryClickEquipListener(), MMOItems.plugin);
    }
}
