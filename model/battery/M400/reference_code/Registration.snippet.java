// ===== 注册片段：放进 ModInitializer（例如 AvataDrone.java）=====
import com.example.avatadrone.item.M400BatteryItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public static final Item M400_BATTERY = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "m400_battery"),
        new M400BatteryItem(new Item.Settings().maxCount(1)));

// onInitialize() 中：
ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(M400_BATTERY));

// 客户端不需要额外注册：GeoItem 通过 createRenderer/RenderProvider 自行提供渲染器。

// ===== 资源 =====
// assets/<modid>/geo/m400_battery.geo.json
// assets/<modid>/animations/m400_battery.animation.json
// assets/<modid>/textures/item/m400_battery.png
// assets/<modid>/textures/item/m400_battery_glowmask.png      (电量灯发光，AutoGlowingGeoLayer)
// assets/<modid>/models/item/m400_battery.json               ({"parent":"builtin/entity", "display":{...}})
// en_us: "item.avatadrone.m400_battery": "DJI TB100 Intelligent Flight Battery (Matrice 400)"
// zh_cn: "item.avatadrone.m400_battery": "大疆 TB100 智能飞行电池（经纬 M400）"
