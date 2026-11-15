// ===== 注册片段：放进 ModInitializer =====
public static final EntityType<Mini4ProEntity> MINI4PRO = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(MOD_ID, "mini4pro"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, Mini4ProEntity::new)
                .dimensions(EntityDimensions.fixed(0.9f, 0.45f))
                .trackRangeBlocks(64)
                .build());

public static final Item MINI4PRO_SPAWN_EGG = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "mini4pro_spawn_egg"),
        new SpawnEggItem(MINI4PRO, 0xC8CACC, 0x1C1C1E, new Item.Settings()));

// onInitialize():
FabricDefaultAttributeRegistry.register(MINI4PRO, Mini4ProEntity.createAttributes());
// 物品栏：entries.add(MINI4PRO_SPAWN_EGG);

// ===== ClientModInitializer =====
EntityRendererRegistry.register(AvataDrone.MINI4PRO, Mini4ProRenderer::new);

// ===== 资源 =====
// assets/<modid>/models/item/mini4pro_spawn_egg.json : {"parent": "minecraft:item/template_spawn_egg"}
// en_us: "entity.avatadrone.mini4pro": "DJI Mini 4 Pro", "item.avatadrone.mini4pro_spawn_egg": "DJI Mini 4 Pro Spawn Egg"
// zh_cn: "entity.avatadrone.mini4pro": "大疆 Mini 4 Pro", "item.avatadrone.mini4pro_spawn_egg": "大疆 Mini 4 Pro 刷怪蛋"

// ===== v2：电池仓 =====
// Mini4ProEntity 引用 AvataDrone.MINI4PRO_BATTERY（Mini4ProBatteryItem，见 model/battery/mini4pro/reference_code）：
// public static final Item MINI4PRO_BATTERY = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mini4pro_battery"),
//         new Mini4ProBatteryItem(new Item.Settings().maxCount(1)));
// 交互：手持电池右键无人机 = 插入；潜行 + 空手右键 = 取出；空手右键 = 云台切换。
// 无电池时无人机受重力落地、飞行 AI 不启动；死亡时若有电池会掉落电池物品。
