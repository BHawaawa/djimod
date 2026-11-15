package com.example.avatadrone.client;

import com.example.avatadrone.entity.PowerStationEntity;
import com.example.avatadrone.item.DeviceCharging;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders the station (GeckoLib, 1 model px = 4 mm, scale 0.125 -> 0.89 x 0.44 x 0.48 blocks), the plugged device
 * at the end of the cable, and a live LCD (charge %, bar, input / output watts) over the static screen texture.
 * All coordinates below are Blockbench px of model/auro2000elite (front = -Z, u = -X).
 */
public class PowerStationRenderer extends GeoEntityRenderer<PowerStationEntity> {
    public static final float SCALE = 0.125f;
    private static final float PX = SCALE / 16f;                       // blocks per model px
    /** device spot (tools/auro_cable.py DEVICE) and the extra scale applied on top of the item's GROUND transform. */
    private static final float DEV_X = -14f, DEV_Y = -1.5f, DEV_Z = -85.5f, DEV_SCALE = 0.3f;
    private static final float LCD_Z = -29.2f;
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public PowerStationRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PowerStationModel());
        withScale(SCALE);
        this.shadowRadius = 0.45f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** GeoEntityRenderer only uses body yaw of living entities; this is a plain entity, so use its own yaw. */
    @Override
    protected void applyRotations(PowerStationEntity entity, MatrixStack matrices, float ageInTicks, float rotationYaw, float partialTick) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - entity.getYaw(partialTick)));
    }

    @Override
    public void render(PowerStationEntity entity, float entityYaw, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        super.render(entity, entityYaw, partialTick, matrices, buffers, light);
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - entity.getYaw(partialTick)));
        // ---- plugged device at the end of the cable
        ItemStack device = entity.getDevice();
        if (!device.isEmpty()) {                      // also kept briefly while unplugging
            matrices.push();
            matrices.translate(DEV_X * PX, DEV_Y * PX, DEV_Z * PX);
            matrices.scale(DEV_SCALE, DEV_SCALE, DEV_SCALE);
            MinecraftClient.getInstance().getItemRenderer().renderItem(device, ModelTransformationMode.GROUND, light,
                    OverlayTexture.DEFAULT_UV, matrices, buffers, entity.getWorld(), entity.getId());
            matrices.pop();
        }
        // ---- live LCD (only when close enough to read it)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.squaredDistanceTo(entity) < 12 * 12) {
            matrices.scale(PX, PX, PX);
            drawLcd(entity, matrices, buffers);
        }
        matrices.pop();
    }

    private void drawLcd(PowerStationEntity e, MatrixStack matrices, VertexConsumerProvider buffers) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer bg = buffers.getBuffer(RenderLayer.getTextBackground());
        // black panel over the printed demo screen
        quad(bg, m, 25.0f, 43.75f, 45.2f, 53.85f, LCD_Z, 0xFF060709);
        int pct = Math.round(e.getChargeFraction() * 100);
        int lit = MathHelper.ceil(e.getChargeFraction() * 10 - 1e-3);
        boolean charging = e.getInputWatts() > 0;
        boolean blink = (e.age / 10) % 2 == 0;
        for (int k = 0; k < 10; k++) {
            float a0 = 25.9f + k * 1.15f;
            int col = k < lit ? 0xFFF6922A : 0xFF2A2B2E;
            if (charging && k == Math.min(lit, 9) && blink) col = 0xFFF6922A;
            quad(bg, m, a0, 45.45f, a0 + 0.85f, 45.95f, LCD_Z - 0.05f, col);
        }
        quad(bg, m, 38.65f, 44.4f, 38.75f, 53.4f, LCD_Z - 0.05f, 0xFF3C3E42);
        quad(bg, m, 39.3f, 48.95f, 44.8f, 49.05f, LCD_Z - 0.05f, 0xFF3C3E42);
        TextRenderer tr = mc().textRenderer;
        String big = Integer.toString(pct);
        text(matrices, buffers, tr, big, 26.0f, 52.6f, 4.4f / 7f, 0xFFEDEEF0, false);
        float bigW = tr.getWidth(big) * 4.4f / 7f;
        text(matrices, buffers, tr, "%", 26.2f + bigW, 48.2f + 1.1f, 1.1f / 7f, 0xFFEDEEF0, false);
        text(matrices, buffers, tr, "INPUT", 39.4f, 53.3f, 0.62f / 7f, 0xFFC8CACE, false);
        text(matrices, buffers, tr, "W", 39.4f, 52.3f, 0.6f / 7f, 0xFFC8CACE, false);
        text(matrices, buffers, tr, Integer.toString(e.getInputWatts()), 45.0f, 52.9f, 2.0f / 7f, charging ? 0xFF7CFF9A : 0xFFEDEEF0, true);
        text(matrices, buffers, tr, "OUTPUT", 39.4f, 48.6f, 0.62f / 7f, 0xFFC8CACE, false);
        text(matrices, buffers, tr, "W", 39.4f, 47.6f, 0.6f / 7f, 0xFFC8CACE, false);
        text(matrices, buffers, tr, Integer.toString(e.getOutputWatts()), 45.0f, 48.2f, 2.0f / 7f, 0xFFEDEEF0, true);
        ItemStack d = e.getDevice();
        if (e.getCableState() == PowerStationEntity.PLUGGED && !d.isEmpty())
            text(matrices, buffers, tr, "USB-C " + Math.round(DeviceCharging.getCharge(d) * 100) + "%", 39.4f, 45.2f,
                    0.55f / 7f, 0xFFF6922A, false);
    }

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    /** u = screen-right coordinate (= -X), y = model height of the text top; k = model px per font unit. */
    private static void text(MatrixStack matrices, VertexConsumerProvider buffers, TextRenderer tr, String s, float u, float y,
                             float k, int argb, boolean alignRight) {
        matrices.push();
        float uu = alignRight ? u - tr.getWidth(s) * k : u;
        matrices.translate(-uu, y, LCD_Z - 0.1f);
        matrices.scale(-k, -k, k);
        tr.draw(s, 0, 0, argb, false, matrices.peek().getPositionMatrix(), buffers, TextRenderer.TextLayerType.POLYGON_OFFSET,
                0, FULL_BRIGHT);
        matrices.pop();
    }

    /** flat rectangle facing the front (-Z), given in (u, y); both windings so face culling never hides it. */
    private static void quad(VertexConsumer vc, Matrix4f m, float u0, float y0, float u1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float x0 = -u0, x1 = -u1;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).light(FULL_BRIGHT).next();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).light(FULL_BRIGHT).next();
    }
}
