package com.example.avatadrone.compat;

import java.util.function.Consumer;
import java.util.function.Supplier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

/** Bridges the old lazy renderer contract to GeckoLib 4.6 without creating client objects on a server. */
public interface LegacyGeoItem extends GeoItem {
    Supplier<Object> getLegacyRenderProvider();
    void createRenderer(Consumer<Object> consumer);

    @Override default Object getRenderProvider() { return getLegacyRenderProvider().get(); }

    @Override default void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        createRenderer(renderer -> consumer.accept((GeoRenderProvider) renderer));
    }

    static Supplier<Object> makeRenderer(LegacyGeoItem item) {
        return new Supplier<>() {
            private Object renderer;
            @Override public Object get() {
                if (renderer == null) item.createRenderer(value -> renderer = value);
                return renderer;
            }
        };
    }
}
