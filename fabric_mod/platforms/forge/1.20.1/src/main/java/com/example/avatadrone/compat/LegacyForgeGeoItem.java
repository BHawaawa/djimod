package com.example.avatadrone.compat;

import java.util.function.Consumer;
import java.util.function.Supplier;
import software.bernie.geckolib.animatable.GeoItem;

/** Adapts the shared lazy renderer factory to Forge's Item.initializeClient hook. */
public interface LegacyForgeGeoItem extends GeoItem {
    void createRenderer(Consumer<Object> consumer);
    Supplier<Object> getRenderProvider();
    static Supplier<Object> makeRenderer(LegacyForgeGeoItem item) {
        return new Supplier<>() {
            private Object renderer;
            @Override public Object get() {
                if (renderer == null) item.createRenderer(value -> renderer = value);
                return renderer;
            }
        };
    }
}
