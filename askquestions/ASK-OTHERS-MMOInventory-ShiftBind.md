## 调研主题
我正在排查 MMOItems 与 MMOInventory 的联动问题：玩家通过 Shift+Click 将 MMOItems 的 ACCESSORY 放入 MMOInventory 饰品槽时，MMOItems 的 `auto-bind-on-use`（一次性灵魂绑定）无法被消耗/落盘；但通过“鼠标拖拽/普通点击放入饰品槽”则绑定正常。

## 上下文背景
- 项目：MMOItems（自改版）对接 MMOInventory（Inventory-API 2.0）。
- 现象：
  - 普通放入（在 MMOInventory 饰品槽界面里用鼠标放入）→ 绑定成功。
  - Shift+Click（从玩家背包 Shift 快捷放入 MMOInventory 饰品槽）→ 绑定不触发/不生效。
- 关键推断：Shift+Click 路径可能不触发 `net.Indyuce.inventory.api.event.InventoryUpdateEvent`，导致原本挂在 `InventoryUpdateEvent` 上的绑定逻辑没有入口。
- 目标：无论使用哪种方式装备饰品，只要最终成功装备到 MMOInventory 饰品槽，都要消耗 auto-bind-on-use，并且不引入“修饰器反复刷新/两个物品来回切换”的旧 Bug。

## 已知相关信息
1) Inventory-API 2.0 的 `net.Indyuce.inventory.api.event.ItemEquipEvent` 类存在，且包含字段：
   - `newItem` / `previousItem` / `inventory` / `slot` / `action`，并实现 `org.bukkit.event.Cancellable`。
   - 从 class 文件字符串可见方法：`getItem()`, `getPreviousItem()`, `getInventory()`, `getSlot()`, `getAction()`, `isCancelled()`, `setCancelled(boolean)`。
2) MMOItems 的自动绑定工具：`net.Indyuce.mmoitems.util.AutoBindUtil.applyAutoBindIfNeeded(PlayerData, ItemStack)`
   - 条件：物品是 MMOItems 且 `MMOITEMS_AUTO_BIND_ON_USE` 为 true 且尚未 SOULBOUND。
   - 成功后会写入 SOULBOUND，并将 AUTO_BIND_ON_USE 置为 false，构建新 ItemStack。
   - 注意：对主手/副手提供明确写回槽位逻辑，但对“自定义饰品槽（MMOInventory）”没有直接写回能力，需要调用 MMOInventory 的 setItem 或其它 API。

## 相关伪代码

```pseudo
// 当前我们想实现：不管 Shift/拖拽/普通点击，只要装备成功，就消耗 auto-bind-on-use

on MMOInventory ItemEquipEvent (MONITOR, ignoreCancelled=true):
    if event.newItem is air: return
    if not isMMOItemsAccessory(event.newItem): return

    // 触发绑定
    AutoBindUtil.applyAutoBindIfNeeded(MMOItemsPlayerData(event.player), event.newItem)

    // 关键：如何将绑定后的 ItemStack 写回到 MMOInventory 的真实存储？
    // - event.newItem 可能是临时引用？
    // - 是否有 event.setNewItem 或 API 来回写？
    // - 是否应调用 event.getPlayerData().get(event.inventory).setItem(event.slot, item)?
    //   (但我们在 MMOItems 项目中拿不到 MMOInventory 的 PlayerData 类型，除非通过 MMOInventory.plugin.getDataManager().get(player))
```

## 技术约束
- 服务端：Paper/Spigot（Bukkit 事件模型）。
- MMOInventory：Inventory-API 2.0（net.Indyuce:Inventory-API:2.0）。
- 不允许引入旧 Bug：饰品在两个物品之间来回切换 / 修饰器重复注册刷新。
- 需要兼容 Shift+Click 装备路径。

## 待澄清问题
1) MMOInventory（Inventory-API 2.0）在 Shift+Click 装备饰品时，是否必然触发 `ItemEquipEvent`？是否不触发 `InventoryUpdateEvent`？
2) 在 `ItemEquipEvent` 中，`getItem()`/`getNewItem()` 返回的 `ItemStack` 是否是“真实存储引用”还是“事件快照”？
   - 直接修改其 ItemMeta/NBT 是否会写回 MMOInventory 存储？
3) Inventory-API 2.0 是否提供“写回新物品”的标准做法？例如：
   - event.setNewItem(item) 之类（如果存在）
   - event.getInventory().getPlayerData().setItem(event.getSlot(), item)
   - 或者必须走 `PlayerData.getCustom(inv).setItem(slot, item)`
4) 最低风险实现方案是什么：
   - 监听 `ItemEquipEvent`，在 MONITOR 阶段绑定并写回；
   - 还是在 LOWEST 阶段先绑定，并在 HIGHEST 阶段强制写回（类似“回写 NBT”机制）？
   - 如何避免导致装备事件再次触发、形成递归或重复刷新？
5) 如果 Shift+Click 不触发 `ItemEquipEvent`，那它到底触发哪类事件 / API 回调？应该挂在哪里才能稳定拿到“最终装备成功”的信号？

## 期望输出
请给出基于 Inventory-API 2.0 的权威结论与建议：
- Shift+Click 装备饰品的事件触发链（至少说明会不会触发 InventoryUpdateEvent / ItemEquipEvent）。
- 在 ItemEquipEvent 中将“绑定后 ItemStack”写回 MMOInventory 存储的正确方式（最好给出 Java 代码片段）。
- 为避免重复刷新/递归的去重策略建议（例如用 reentrancy guard、比较 item hash、或只在 action=EQUIP/SWAP 时处理）。

---

### 给外部 AI 的简短 Prompt
你熟悉 MMOInventory / Inventory-API 2.0 的事件模型吗？请说明玩家 Shift+Click 将物品装备到 MMOInventory 饰品槽时会触发哪些事件（InventoryUpdateEvent / ItemEquipEvent），以及在 ItemEquipEvent 中如何把修改后的 ItemStack 写回 MMOInventory 的真实存储（避免递归/重复刷新）。需要 Java 代码示例。
