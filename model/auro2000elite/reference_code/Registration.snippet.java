// ---- common (ModInitializer class, e.g. AvataDrone.java) ----
public static final EntityType<com.example.avatadrone.entity.PowerStationEntity> AURO2000 = Registry.register(
        Registries.ENTITY_TYPE, new Identifier(MOD_ID, "auro2000elite"),
        FabricEntityTypeBuilder.<com.example.avatadrone.entity.PowerStationEntity>create(SpawnGroup.MISC,
                        com.example.avatadrone.entity.PowerStationEntity::new)
                .dimensions(EntityDimensions.fixed(0.9f, 0.48f)).trackRangeBlocks(64).build());
public static final Item AURO2000_ITEM = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "auro2000elite"),
        new com.example.avatadrone.item.PowerStationItem(new Item.Settings().maxCount(1)));
// item group: entries.add(AURO2000_ITEM);

// ---- client (ClientModInitializer) ----
EntityRendererRegistry.register(AvataDrone.AURO2000, com.example.avatadrone.client.PowerStationRenderer::new);

// ---- assets ----
// geo/auro2000elite.geo.json, animations/auro2000elite.animation.json,
// textures/entity/auro2000elite.png + auro2000elite_glowmask.png, models/item/auro2000elite.json
// lang keys: entity/item.avatadrone.auro2000elite, message.avatadrone.auro.*, tooltip.avatadrone.auro.* (see fabric_mod lang files)
