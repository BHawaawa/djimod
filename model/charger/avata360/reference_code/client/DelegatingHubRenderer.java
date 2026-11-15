package com.example.avatadrone.client.charger;

import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.block.charger.ChargingHubType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** 共用 BlockEntityType 时按 ChargingHubType 转发到对应的 GeoBlockRenderer。 */
public class DelegatingHubRenderer implements BlockEntityRenderer<ChargingHubBlockEntity> {
    private final ChargingHubRenderer avata, mini;

    public DelegatingHubRenderer(ChargingHubRenderer avata, ChargingHubRenderer mini) {
        this.avata = avata;
        this.mini = mini;
    }

    @Override
    public void render(ChargingHubBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay) {
        (be.type() == ChargingHubType.AVATA360 ? avata : mini).render(be, tickDelta, matrices, vcp, light, overlay);
    }

    @Override
    public boolean rendersOutsideBoundingBox(ChargingHubBlockEntity be) {
        return true;   // 电池帽 / 插入动画会高出 1 格
    }
}
