# 外部调研请求

## 调研主题
MMOInventory API 2.0 槽位操作接口，用于实现左键点击装卸饰品功能

## 上下文背景
我正在修改 MMOItems 插件以兼容 MMOInventory。当前问题是：MMOInventory 的饰品栏无法通过鼠标拖拽放入物品，用户希望能在普通背包中左键点击 MMOItems 物品直接装备/卸下到 MMOInventory 的饰品槽。

已知信息：
- MMOInventory API 版本：2.0
- Maven 坐标：`net.Indyuce:Inventory-API:2.0`
- 已下载并解压 jar，发现关键类在 `net.Indyuce.inventory` 包下
- MMOItems 现有集成代码通过 `InventoryUpdateEvent` 监听槽位变化

## 相关伪代码
```pseudo
// 目标：在 MMOItems 中添加左键点击装卸饰品功能
@EventHandler
function onInventoryClick(InventoryClickEvent event):
    player = event.getWhoClicked()
    clickedItem = event.getCurrentItem()

    if event.getClick() != LEFT_CLICK:
        return

    // 检查是否为 MMOItems 的 ACCESSORY 类型
    nbtItem = NBTItem.get(clickedItem)
    type = Type.get(nbtItem)
    if type == null || type.getModifierSource() != ACCESSORY:
        return

    // ← 不确定：如何获取 MMOInventory 玩家数据？
    mmoInvPlayerData = MMOInventory.plugin.getDataManager().get(player)  // ← 需确认

    // ← 不确定：如何遍历所有自定义饰品槽？
    for inventory in MMOInventory.plugin.getInventoryManager().getAll():
        for slot in inventory.getSlots():  // ← 需确认方法名
            if slot.getType().isCustom():

                // ← 关键不确定：如何判断物品是否可以放入此槽位？
                if slot.canHold(clickedItem):  // ← 方法名未知，可能是 accepts()？

                    // ← 不确定：如何获取槽位当前物品？
                    currentItem = mmoInvPlayerData.get(inventory).getItem(slot)

                    if currentItem == null || currentItem.isAir():
                        // ← 不确定：如何设置槽位物品？
                        mmoInvPlayerData.get(inventory).setItem(slot, clickedItem)
                        event.setCurrentItem(null)
                        return
```

## 技术约束
- 技术栈：Java 11+, Bukkit/Spigot API 1.21.4
- 依赖：MMOInventory API 2.0, MythicLib 1.7.1
- 运行环境：Minecraft 服务端插件
- 已有限制：不能修改 MMOInventory 源码，只能通过其公开 API 操作

## 待澄清问题

### 1. CustomSlot 类
- 是否有方法判断物品能否放入槽位？（如 `canHold(ItemStack)`、`accepts(ItemStack)`）
- 槽位限制（SlotRestriction）如何工作？是否暴露了检查方法？
- `getType()` 返回的 SlotType 有哪些枚举值？

### 2. PlayerData / InventoryData 类
- `PlayerData.get(Inventory)` 返回什么类型？
- 如何正确设置槽位物品？`setItem(CustomSlot, ItemStack)` 是否存在？
- 设置物品后是否会自动触发 `InventoryUpdateEvent`？

### 3. 整体流程
- 通过代码将物品放入 MMOInventory 槽位的标准流程是什么？
- 是否有官方推荐的 API 用法示例？

## 期望输出
1. 上述类的关键公共方法签名列表
2. 判断物品是否可放入槽位的正确方法
3. 将物品装备到槽位的完整代码示例
4. 如果 API 不支持此操作，请说明原因和替代方案

---

## 调研 Prompt（直接复制给外部 AI）

我正在修改 Minecraft 插件 MMOItems 以兼容 MMOInventory 插件，遇到以下技术问题：

【背景】
MMOInventory 的饰品栏无法通过鼠标拖拽放入物品，我需要在 MMOItems 中实现"左键点击背包中的饰品类物品，自动装备到 MMOInventory 的饰品槽"功能。

【技术环境】
- MMOInventory API 版本：2.0（Maven: net.Indyuce:Inventory-API:2.0）
- Java 11+, Bukkit/Spigot 1.21.4
- 不能修改 MMOInventory 源码

【已知的类结构】
从 jar 解压发现以下关键类：
- `net.Indyuce.inventory.inventory.slot.CustomSlot`
- `net.Indyuce.inventory.inventory.slot.SlotRestriction`
- `net.Indyuce.inventory.inventory.slot.SlotType`
- `net.Indyuce.inventory.player.PlayerData`
- `net.Indyuce.inventory.player.InventoryData`
- `net.Indyuce.inventory.player.CustomInventoryData`
- `net.Indyuce.inventory.api.event.InventoryUpdateEvent`

【具体问题】
1. CustomSlot 是否有方法判断物品能否放入槽位？（如 canHold、accepts 等）
2. 如何通过 PlayerData/InventoryData 获取和设置槽位物品？
3. 将物品通过代码装备到 MMOInventory 槽位的完整流程是什么？

【期望回答】
请提供：
1. 上述类的关键公共方法签名
2. 判断物品是否可放入槽位的正确 API 调用方式
3. 完整的代码示例：如何将 ItemStack 装备到 MMOInventory 的自定义槽位
4. 如果 API 不支持，请说明原因和可能的替代方案
