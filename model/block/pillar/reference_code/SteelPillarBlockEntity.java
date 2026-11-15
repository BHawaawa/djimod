package com.example.avatadrone.block;

import com.example.avatadrone.ModPillar;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SteelPillarBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SteelPillarBlockEntity(BlockPos pos, BlockState state) {
        super(ModPillar.STEEL_PILLAR_BE, pos, state);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { } // 静态模型
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
