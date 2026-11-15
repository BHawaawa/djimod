package com.example.avatadrone.client;

import com.example.avatadrone.item.PowerStationItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Inventory / hand rendering of the AURO 2000 ELITE (entity geometry, cable in its stowed rest pose). */
public final class PowerStationItemRenderer extends GeoItemRenderer<PowerStationItem> {
    public PowerStationItemRenderer() {
        super(new GeoModel<PowerStationItem>() {
            @Override public Identifier getModelResource(PowerStationItem item) { return PowerStationModel.MODEL; }
            @Override public Identifier getTextureResource(PowerStationItem item) { return PowerStationModel.TEXTURE; }
            @Override public Identifier getAnimationResource(PowerStationItem item) { return PowerStationModel.ANIMATION; }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
