// ===== 注册片段：放进 ModInitializer（AvataDrone） =====
public static final EntityType<M400Entity> M400 = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(MOD_ID, "m400"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, M400Entity::new)
                .dimensions(EntityDimensions.fixed(1.6f, 0.8f))
                .trackRangeBlocks(96)
                .build());

public static final Item M400_SPAWN_EGG = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "m400_spawn_egg"),
        new SpawnEggItem(M400, 0x4E4F53, 0xF07020, new Item.Settings()));

// onInitialize():
FabricDefaultAttributeRegistry.register(M400, M400Entity.createAttributes());

// ===== ClientModInitializer =====
EntityRendererRegistry.register(AvataDrone.M400, M400Renderer::new);

// ===== 资源 =====
// assets/<modid>/geo/m400.geo.json
// assets/<modid>/animations/m400.animation.json
// assets/<modid>/textures/entity/m400.png
// assets/<modid>/textures/entity/m400_glowmask.png   (AutoGlowingGeoLayer 用)
// assets/<modid>/models/item/m400_spawn_egg.json : {"parent": "minecraft:item/template_spawn_egg"}
// en_us: "entity.avatadrone.m400": "DJI Matrice 400", "item.avatadrone.m400_spawn_egg": "DJI Matrice 400 Spawn Egg"
// zh_cn: "entity.avatadrone.m400": "大疆经纬 M400", "item.avatadrone.m400_spawn_egg": "大疆经纬 M400 刷怪蛋"

// ===== v1.1：TB100 电池仓 =====
// M400Entity 引用 AvataDrone.M400_BATTERY（M400BatteryItem，见 model/battery/M400/reference_code）：
// public static final Item M400_BATTERY = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "m400_battery"),
//         new M400BatteryItem(new Item.Settings().maxCount(1)));
// 交互：手持 TB100 电池右键无人机 = 插入；潜行 + 空手右键 = 取出。
// 无电池时无人机受重力落地、飞行 AI 不启动；死亡时若有电池会掉落电池物品。
