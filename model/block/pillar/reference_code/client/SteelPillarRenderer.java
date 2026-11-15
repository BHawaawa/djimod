package com.example.avatadrone.client;

import com.example.avatadrone.block.SteelPillarBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SteelPillarRenderer extends GeoBlockRenderer<SteelPillarBlockEntity> {
    public SteelPillarRenderer() { super(new SteelPillarModel()); }
}
