// ===== 注册片段：放进 ModInitializer（例如 AvataDrone.java）=====
import com.example.avatadrone.item.Avata360BatteryItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public static final Item AVATA360_BATTERY = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "avata360_battery"),
        new Avata360BatteryItem(new Item.Settings().maxCount(1)));

// onInitialize() 中：
ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(AVATA360_BATTERY));

// 客户端不需要额外注册：GeoItem 通过 createRenderer/RenderProvider 自行提供渲染器。

// ===== 资源 =====
// assets/<modid>/geo/avata360_battery.geo.json
// assets/<modid>/animations/avata360_battery.animation.json
// assets/<modid>/textures/item/avata360_battery.png
// assets/<modid>/textures/item/avata360_battery_glowmask.png
// assets/<modid>/models/item/avata360_battery.json            ({"parent":"builtin/entity", "display":{...}})
// en_us: "item.avatadrone.avata360_battery": "DJI Avata 360 Intelligent Flight Battery",
//        "item.avatadrone.battery.on": "Power: ON", "item.avatadrone.battery.off": "Power: OFF"
// zh_cn: "item.avatadrone.avata360_battery": "大疆 Avata 360 智能飞行电池",
//        "item.avatadrone.battery.on": "电源：开", "item.avatadrone.battery.off": "电源：关"
