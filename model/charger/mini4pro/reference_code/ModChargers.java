package com.example.avatadrone;

import com.example.avatadrone.block.charger.ChargingHubBlock;
import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.block.charger.ChargingHubType;
import com.example.avatadrone.item.ChargingHubBlockItem;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * 两个充电管家：avata360_charging_hub / mini4pro_charging_hub，共用一个 BlockEntityType。
 * 在 AvataDrone.onInitialize() 里调用 ModChargers.init();
 */
public final class ModChargers {
    public static final ChargingHubBlock AVATA360_HUB = block(ChargingHubType.AVATA360, MapColor.GRAY);
    public static final ChargingHubBlock MINI4PRO_HUB = block(ChargingHubType.MINI4PRO, MapColor.LIGHT_GRAY);

    public static final BlockEntityType<ChargingHubBlockEntity> CHARGING_HUB_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            new Identifier(AvataDrone.MOD_ID, "charging_hub"),
            FabricBlockEntityTypeBuilder.create(ChargingHubBlockEntity::new, AVATA360_HUB, MINI4PRO_HUB).build());

    private static ChargingHubBlock block(ChargingHubType type, MapColor color) {
        Identifier id = new Identifier(AvataDrone.MOD_ID, type.model);
        ChargingHubBlock b = Registry.register(Registries.BLOCK, id, new ChargingHubBlock(type,
                FabricBlockSettings.create().mapColor(color).strength(1.0f).sounds(BlockSoundGroup.METAL).nonOpaque()));
        Registry.register(Registries.ITEM, id, new ChargingHubBlockItem(b, new Item.Settings().maxCount(16)));
        return b;
    }

    public static void init() { }
}
