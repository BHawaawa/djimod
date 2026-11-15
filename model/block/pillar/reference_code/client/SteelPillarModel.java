package com.example.avatadrone.client;

import com.example.avatadrone.block.SteelPillarBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class SteelPillarModel extends GeoModel<SteelPillarBlockEntity> {
    @Override public Identifier getModelResource(SteelPillarBlockEntity a) { return new Identifier("avatadrone", "geo/block/steel_pillar.geo.json"); }
    @Override public Identifier getTextureResource(SteelPillarBlockEntity a) { return new Identifier("avatadrone", "textures/block/steel_pillar.png"); }
    @Override public Identifier getAnimationResource(SteelPillarBlockEntity a) { return new Identifier("avatadrone", "animations/block/steel_pillar.animation.json"); }
}
