package com.example.avatadrone;
import net.minecraft.sound.SoundEvent;

import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.network.DroneControlNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 主入口：注册 DJI Avata 360 实体、属性与刷怪蛋。
 * Minecraft 1.20.1 / Fabric / Yarn 映射 / GeckoLib 4.x
 */
public class AvataDrone implements ModInitializer {
    public static final String MOD_ID = "djimod";
    public static final SoundEvent VOICE_1 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.1"));
    public static final SoundEvent VOICE_2 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.2"));
    public static final SoundEvent VOICE_3 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.3"));
    public static final SoundEvent VOICE_4 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.4"));
    public static final SoundEvent VOICE_5 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.5"));
    public static final SoundEvent VOICE_6 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.6"));
    public static final SoundEvent VOICE_7 = SoundEvent.of((Identifier)new Identifier("djimod", "voice.7"));
    public static final SoundEvent VOICE_XINBAN = SoundEvent.of((Identifier)new Identifier("djimod", "voice.xinban"));
    public static final SoundEvent VOICE_JIUBAN = SoundEvent.of((Identifier)new Identifier("djimod", "voice.jiuban"));
    public static final SoundEvent VOICE_BIZHANG = SoundEvent.of((Identifier)new Identifier("djimod", "voice.bizhang"));
    public static final SoundEvent VOICE_WUBIZHANG = SoundEvent.of((Identifier)new Identifier("djimod", "voice.wubizhang"));
    public static final Item DJI_RC2 = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "dji_rc2"), new Rc2Item(new Item.Settings().maxCount(1)));

    public static final net.minecraft.block.Block FOUR_G_STATION = Registry.register(Registries.BLOCK,
            new Identifier(MOD_ID, "4g_signal_emitter"),
            new com.example.avatadrone.block.FourGBaseStationBlock(
                    net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings.create()
                            .strength(3f).sounds(net.minecraft.sound.BlockSoundGroup.METAL).nonOpaque()));
    public static final Item FOUR_G_STATION_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "4g_signal_emitter"), new net.minecraft.item.BlockItem(FOUR_G_STATION, new Item.Settings()));

    // 模型宽 30 像素 * 渲染缩放 0.5 ≈ 0.94 格，高度约 0.2 格
    public static final EntityType<AvataDroneEntity> AVATA360 = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "avata360"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AvataDroneEntity::new)
                    .dimensions(EntityDimensions.fixed(0.95f, 0.3f))
                    .trackRangeBlocks(10000)
                    .trackedUpdateRate(1)
                    .build());

    public static final Item AVATA360_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "avata360_spawn_egg"),
            new SpawnEggItem(AVATA360, 0x8E9092, 0x1E1F21, new Item.Settings()));

    /** Avata 360 智能飞行电池：手持右键无人机插入电池仓；潜行空手右键取出。 */
    public static final Item AVATA360_BATTERY = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "avata360_battery"), new com.example.avatadrone.item.DroneEquipmentItem("avata360_battery", false));

    public static final EntityType<com.example.avatadrone.entity.Mini4ProEntity> MINI4PRO = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(MOD_ID, "mini4pro"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, com.example.avatadrone.entity.Mini4ProEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.45f)).trackRangeBlocks(10000).trackedUpdateRate(1).build());
    public static final Item MINI4PRO_SPAWN_EGG = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "mini4pro_spawn_egg"),
            new SpawnEggItem(MINI4PRO, 0xC3C6C1, 0x343A3C, new Item.Settings()));

    public static final Item AVATA360_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "avata360"), new com.example.avatadrone.item.DroneEquipmentItem("avata360", true));
    public static final Item MINI4PRO_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "mini4pro"), new com.example.avatadrone.item.DroneEquipmentItem("mini4pro", true));
    public static final Item MINI4PRO_BATTERY = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "mini4pro_battery"), new com.example.avatadrone.item.DroneEquipmentItem("mini4pro_battery", false));

    public static final Item MINI4PRO_BATTERY_LONG = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "mini4pro_battery_long"), new com.example.avatadrone.item.DroneEquipmentItem("mini4pro_battery", false));

    public static final EntityType<com.example.avatadrone.entity.M400Entity> M400 = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(MOD_ID, "m400"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, com.example.avatadrone.entity.M400Entity::new)
                    .dimensions(EntityDimensions.fixed(1.6f, 0.8f)).trackRangeBlocks(10000).trackedUpdateRate(1).build());
    public static final Item M400_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "m400"), new com.example.avatadrone.item.DroneEquipmentItem("m400", true));
    public static final Item M400_BATTERY = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "m400_battery"), new com.example.avatadrone.item.DroneEquipmentItem("m400_battery", false));
    public static final Item M400_SPAWN_EGG = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "m400_spawn_egg"), new SpawnEggItem(M400, 0x51575C, 0xE57C2D, new Item.Settings()));

    /** DJI Power AURO 2000 ELITE 户外电源（可放置实体；Shift+右键取出/收回 140W 伸缩线，取出后连接充电管家或地面无人机）。 */
    public static final EntityType<com.example.avatadrone.entity.PowerStationEntity> AURO2000 = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(MOD_ID, "auro2000elite"),
            FabricEntityTypeBuilder.<com.example.avatadrone.entity.PowerStationEntity>create(SpawnGroup.MISC,
                            com.example.avatadrone.entity.PowerStationEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.48f)).trackRangeBlocks(64).build());
    public static final Item AURO2000_ITEM = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "auro2000elite"),
            new com.example.avatadrone.item.PowerStationItem(new Item.Settings().maxCount(1)));

    /** Redmi K90 手机（GeckoLib 物品）：Shift+右键开关机，右键亮/熄屏；AURO 2000 取线后手持手机右键电源充电。 */
    public static final Item REDMI_K90 = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "redmi_k90"),
            new com.example.avatadrone.item.PhoneItem(new Item.Settings().maxCount(1)));
    public static final Item NAS_MANAGER = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "nas_manager"),
            new com.example.avatadrone.item.NasManagerItem(new Item.Settings().maxCount(1)));

    /** 禁飞无人机牌子 — 贴墙放置，仿真实路牌样式。 */
    public static final net.minecraft.block.Block NODRONE_SIGN = Registry.register(Registries.BLOCK,
            new Identifier(MOD_ID, "nodrone_sign"),
            new com.example.avatadrone.block.NoDroneSignBlock(
                    net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings.create()
                            .strength(1.5f).sounds(net.minecraft.sound.BlockSoundGroup.METAL).nonOpaque()));
    public static final Item NODRONE_SIGN_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "nodrone_sign"),
            new net.minecraft.item.BlockItem(NODRONE_SIGN, new Item.Settings()));

    public static final ItemGroup DJI_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(MOD_ID, "dji"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.djimod.dji"))
                    .icon(() -> new ItemStack(DJI_RC2))
                    .entries((context, entries) -> {
                        entries.add(AVATA360_ITEM);
                        entries.add(AVATA360_BATTERY);
                        entries.add(MINI4PRO_ITEM);
                        entries.add(MINI4PRO_BATTERY);
                        entries.add(MINI4PRO_BATTERY_LONG);
                        entries.add(M400_ITEM);
                        entries.add(M400_BATTERY);
                        entries.add(AURO2000_ITEM);
                        entries.add(ModChargers.AVATA360_HUB);
                        entries.add(ModChargers.MINI4PRO_HUB);
                        entries.add(DJI_RC2);
                        entries.add(REDMI_K90);
                        entries.add(NAS_MANAGER);
                        entries.add(FOUR_G_STATION_ITEM);
                        entries.add(ModPillar.STEEL_PILLAR_ITEM);
                        entries.add(NODRONE_SIGN_ITEM);
                    })
                    .build());

    @Override
    public void onInitialize() {
        AvataDrone.registerVoice("voice.1", VOICE_1);
        AvataDrone.registerVoice("voice.2", VOICE_2);
        AvataDrone.registerVoice("voice.3", VOICE_3);
        AvataDrone.registerVoice("voice.4", VOICE_4);
        AvataDrone.registerVoice("voice.5", VOICE_5);
        AvataDrone.registerVoice("voice.6", VOICE_6);
        AvataDrone.registerVoice("voice.7", VOICE_7);
        AvataDrone.registerVoice("voice.xinban", VOICE_XINBAN);
        AvataDrone.registerVoice("voice.jiuban", VOICE_JIUBAN);
        AvataDrone.registerVoice("voice.bizhang", VOICE_BIZHANG);
        AvataDrone.registerVoice("voice.wubizhang", VOICE_WUBIZHANG);
        com.example.avatadrone.network.DroneSettingsNetworking.register();
        ModPillar.init();
        ModChargers.init();
        com.example.avatadrone.network.ChargingNetwork.register();
        com.example.avatadrone.item.PhoneApps.registerCommand();
        com.example.avatadrone.item.PhoneApps.register("nas");
        DroneControlNetworking.register();
        com.example.avatadrone.network.DroneChunkStreaming.register();
        com.example.avatadrone.network.DroneMaintenance.register();
        FabricDefaultAttributeRegistry.register(AVATA360, AvataDroneEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MINI4PRO, AvataDroneEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(M400, AvataDroneEntity.createAttributes());
    }
    private static void registerVoice(String name, SoundEvent event) {
        Registry.register(Registries.SOUND_EVENT, new Identifier(MOD_ID, name), event);
    }
}
