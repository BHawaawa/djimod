package com.example.avatadrone.client;

import com.example.avatadrone.item.RC2Item;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** 手持/背包/展示框中的姿态由 assets/<modid>/models/item/rc2.json 的 display 决定。 */
public class RC2ItemRenderer extends GeoItemRenderer<RC2Item> {
    public RC2ItemRenderer() {
        super(new RC2ItemModel());
    }
}
