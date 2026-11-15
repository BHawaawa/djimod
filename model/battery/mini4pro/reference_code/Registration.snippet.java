// ===== 注册片段：放进 ModInitializer（例如 AvataDrone.java）=====
import com.example.avatadrone.item.Mini4ProBatteryItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public static final Item MINI4PRO_BATTERY = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "mini4pro_battery"),
        new Mini4ProBatteryItem(new Item.Settings().maxCount(1)));

// onInitialize() 中：
ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(MINI4PRO_BATTERY));

// 客户端不需要额外注册：GeoItem 通过 createRenderer/RenderProvider 自行提供渲染器。

// ===== 资源 =====
// assets/<modid>/geo/mini4pro_battery.geo.json
// assets/<modid>/animations/mini4pro_battery.animation.json
// assets/<modid>/textures/item/mini4pro_battery.png
// assets/<modid>/models/item/mini4pro_battery.json            ({"parent":"builtin/entity", "display":{...}})
// en_us: "item.avatadrone.mini4pro_battery": "DJI Mini 4 Pro Intelligent Flight Battery"
// zh_cn: "item.avatadrone.mini4pro_battery": "大疆 Mini 4 Pro 智能飞行电池"
