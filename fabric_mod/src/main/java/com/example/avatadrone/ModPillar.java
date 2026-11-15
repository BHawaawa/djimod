package com.example.avatadrone;

import com.example.avatadrone.block.SteelPillarBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** 在 AvataDrone.onInitialize() 里调用 ModPillar.init(); */
public final class ModPillar {
    public static final Block STEEL_PILLAR = Registry.register(Registries.BLOCK,
            new Identifier("djimod", "steel_pillar"),
            new SteelPillarBlock(FabricBlockSettings.create().strength(5f, 6f)
                    .requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque()));

    public static final Item STEEL_PILLAR_ITEM = Registry.register(Registries.ITEM,
            new Identifier("djimod", "steel_pillar"), new BlockItem(STEEL_PILLAR, new Item.Settings()));

    public static void init() { }
}
