package com.example.avatadrone.block;

import com.example.avatadrone.ModPillar;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class SteelPillarBlock extends BlockWithEntity {
    public SteelPillarBlock(Settings settings) { super(settings); }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED; // 交给 GeckoLib 渲染
    }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SteelPillarBlockEntity(pos, state);
    }
}
