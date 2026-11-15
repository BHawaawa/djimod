package com.example.avatadrone;

import com.example.avatadrone.entity.AvataDroneEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 主入口：注册 DJI Avata 360 实体、属性与刷怪蛋。
 * Minecraft 1.20.1 / Fabric / Yarn 映射 / GeckoLib 4.x
 */
public class AvataDrone implements ModInitializer {
    public static final String MOD_ID = "avatadrone";

    // 模型宽 30 像素 * 渲染缩放 0.5 ≈ 0.94 格，高度约 0.2 格
    public static final EntityType<AvataDroneEntity> AVATA360 = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "avata360"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AvataDroneEntity::new)
                    .dimensions(EntityDimensions.fixed(0.95f, 0.3f))
                    .trackRangeBlocks(64)
                    .build());

    public static final Item AVATA360_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "avata360_spawn_egg"),
            new SpawnEggItem(AVATA360, 0x8E9092, 0x1E1F21, new Item.Settings()));

    /** Avata 360 智能飞行电池（简单物品；想要 3D 手持模型可换成 model/battery/avata360 里的 GeoItem） */
    public static final Item AVATA360_BATTERY = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "avata360_battery"),
            new Item(new Item.Settings().maxCount(1)));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(AVATA360, AvataDroneEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(AVATA360_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(AVATA360_BATTERY));
    }
}
