package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.PhoneApps;
import com.example.avatadrone.item.PhoneItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.List;

/**
 * GeckoLib renderer of the Redmi K90 + the live screen overlay (status bar, clock widget, configurable app grid,
 * dock, charging percentages).  The overlay is drawn in the space of the empty bone {@code screen_ui}
 * (captured while the model renders, drawn in renderFinal after GeckoLib has finished its buffers).
 *
 * Layout numbers = model/phone/redmi_k90/screen_layout.json ("ui"), in screen px measured from the screen's
 * top-left corner as seen from the front.  model X = SX - sx, Y = SY - sy (front faces -Z).
 */
public class PhoneItemRenderer extends GeoItemRenderer<PhoneItem> {
    private static final float SX = 18.55f, SY = 39.925f, SW = 37.1f, Z_UI = -2.033f;
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final int INK = 0xFF202024, WHITE = 0xFFFFFFFF, GREEN = 0xFF4FE07A;
    private Matrix4f ui;

    public PhoneItemRenderer() {
        super(new PhoneItemModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));          // screen = redmi_k90_glowmask.png -> full bright
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, PhoneItem animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (!isReRender && "screen_ui".equals(bone.getName())) {
            poseStack.push();
            RenderUtils.prepMatrixForBone(poseStack, bone);
            ui = new Matrix4f(poseStack.peek().getPositionMatrix());
            poseStack.pop();
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderFinal(MatrixStack poseStack, PhoneItem animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
                            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay,
                            float red, float green, float blue, float alpha) {
        ItemStack stack = getCurrentItemStack();
        Matrix4f m = ui;
        ui = null;
        if (stack == null || m == null || bufferSource == null) return;
        long now = PhoneItem.clientTime.getAsLong();
        switch (PhoneItem.screen(stack, now)) {
            case HOME -> drawHome(m, bufferSource, stack, now);
            case CHARGE_OFF -> text(m, bufferSource, pct(stack) + "%", SW / 2, 46f - 2.1f, 4.2f, WHITE, true, 0.02f);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ desktop
    private void drawHome(Matrix4f m, VertexConsumerProvider vc, ItemStack stack, long now) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long tod = mc.world == null ? 6000 : mc.world.getTimeOfDay();
        int hh = (int) ((tod / 1000 + 6) % 24), mm = (int) ((tod % 1000) * 60 / 1000);
        String time = String.format("%d:%02d", hh, mm);
        long day = tod / 24000 + 1;
        String date = "第" + day + "天  " + "星期" + "日一二三四五六".charAt((int) (day % 7));
        boolean charging = PhoneItem.isCharging(stack);
        float charge = (float) DeviceCharging.getCharge(stack);

        // status bar
        text(m, vc, time, 2.6f, 1.15f, 1.35f, INK, false, 0.02f);
        float bx1 = SW - 2.4f, by0 = 1.25f, bw = 2.4f, bh = 1.15f;
        VertexConsumer bg = vc.getBuffer(RenderLayer.getTextBackground());
        outline(bg, m, bx1 - bw, by0, bx1, by0 + bh, 0.13f, INK);
        rect(bg, m, bx1 - bw + 0.25f, by0 + 0.25f, bx1 - bw + 0.25f + (bw - 0.5f) * charge, by0 + bh - 0.25f, charging ? GREEN : INK, 0.01f);
        rect(bg, m, bx1 + 0.05f, by0 + 0.35f, bx1 + 0.25f, by0 + bh - 0.35f, INK, 0.01f);
        if (charging) bolt(bg, m, bx1 - bw / 2, by0 + bh / 2, 0.55f, WHITE);
        float sx = bx1 - bw - 3.8f;
        for (int k = 0; k < 4; k++) {
            float h = 0.35f + 0.22f * k, x1 = sx - (3 - k) * 0.42f;
            rect(bg, m, x1 - 0.28f, by0 + bh - h, x1, by0 + bh, INK, 0.01f);
        }
        text(m, vc, Integer.toString(pct(stack)), bx1 - bw - 0.45f - width(Integer.toString(pct(stack)), 1.05f), by0 + 0.06f, 1.05f, INK, false, 0.02f);
        // clock widget
        text(m, vc, time, SW / 2, 6.2f, 6.0f, INK, true, 0.02f);
        text(m, vc, date, SW / 2, 13.4f, 1.25f, INK, true, 0.02f);

        // app grid + dock
        List<String> grid = PhoneApps.grid(stack), dock = PhoneApps.dock(stack);
        for (int i = 0; i < Math.min(grid.size(), PhoneApps.GRID_SLOTS); i++) {
            int r = i / 4, c = i % 4;
            float cx = SW * (0.125f + 0.25f * c), cy = 19f + r * 10f + 3.2f;
            icon(m, vc, grid.get(i), cx, cy, 6.4f);
            Text label = Text.translatableWithFallback("app.djimod.phone." + grid.get(i), grid.get(i));
            float h = Math.min(1.05f, 8.6f / Math.max(1, mc.textRenderer.getWidth(label)) * 7f);
            textC(m, vc, label, cx, cy + 3.2f + 0.55f, h, INK);
        }
        for (int i = 0; i < Math.min(dock.size(), PhoneApps.DOCK_SLOTS); i++)
            icon(m, vc, dock.get(i), SW * (0.125f + 0.25f * i), 72.6f, 6.4f);
        bg = vc.getBuffer(RenderLayer.getTextBackground());
        rect(bg, m, SW / 2 - 0.95f, 67.1f, SW / 2 - 0.35f, 67.7f, INK, 0.01f);
        rect(bg, m, SW / 2 + 0.35f, 67.1f, SW / 2 + 0.95f, 67.7f, 0x5A202024, 0.01f);

        // HyperOS plug-in pill (bone chg_pop) carries the bolt, we print the percentage inside it
        long t = now - PhoneItem.plugTime(stack);
        if (PhoneItem.plugFx(stack, now) && t >= 7 && t <= 36)
            text(m, vc, pct(stack) + "%", SW / 2 + 1.2f, 75.4f - 0.8f, 1.6f, WHITE, true, 0.05f);
    }

    private static int pct(ItemStack s) { return Math.round((float) DeviceCharging.getCharge(s) * 100); }

    // ------------------------------------------------------------------ primitives (screen px -> bone space)
    private static float X(float sx) { return (SX - sx) / 16f; }
    private static float Y(float sy) { return (SY - sy) / 16f; }
    private static float Z(float dz) { return (Z_UI - dz) / 16f; }

    private static void rect(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, int argb, float dz) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float[][] p = {{X(x0), Y(y0)}, {X(x1), Y(y0)}, {X(x1), Y(y1)}, {X(x0), Y(y1)}};
        for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}})            // both windings: never culled
            for (int i : order) v.vertex(m, p[i][0], p[i][1], Z(dz)).color(r, g, b, a).light(FULL).next();
    }

    private static void outline(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float t, int argb) {
        rect(v, m, x0, y0, x1, y0 + t, argb, 0.01f); rect(v, m, x0, y1 - t, x1, y1, argb, 0.01f);
        rect(v, m, x0, y0, x0 + t, y1, argb, 0.01f); rect(v, m, x1 - t, y0, x1, y1, argb, 0.01f);
    }

    /** lightning bolt as two quads (degenerate 4th vertex = triangle-ish wedges). */
    private static void bolt(VertexConsumer v, Matrix4f m, float cx, float cy, float s, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        float[][][] quads = {{{0.18f, -1f}, {-0.62f, 0.12f}, {-0.05f, 0.12f}, {0.05f, -0.16f}},
                             {{-0.05f, 0.12f}, {-0.22f, 1f}, {0.62f, -0.16f}, {0.05f, -0.16f}}};
        for (float[][] q : quads)
            for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}})
                for (int i : order)
                    v.vertex(m, X(cx + q[i][0] * s * 0.6f), Y(cy + q[i][1] * s), Z(0.02f)).color(r, g, b, a).light(FULL).next();
    }

    private static void icon(Matrix4f m, VertexConsumerProvider vc, String id, float cx, float cy, float size) {
        VertexConsumer v = vc.getBuffer(RenderLayer.getText(new Identifier(AvataDrone.MOD_ID, "textures/item/phone/apps/" + id + ".png")));
        float h = size / 2;
        float[][] p = {{cx - h, cy - h, 0, 0}, {cx + h, cy - h, 1, 0}, {cx + h, cy + h, 1, 1}, {cx - h, cy + h, 0, 1}};
        for (int[] order : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}})
            for (int i : order)
                v.vertex(m, X(p[i][0]), Y(p[i][1]), Z(0.01f)).color(255, 255, 255, 255).texture(p[i][2], p[i][3]).light(FULL).next();
    }

    private static float width(String s, float h) { return MinecraftClient.getInstance().textRenderer.getWidth(s) * h / 7f; }

    /** text with its top at sy, height h (screen px of the 7-unit cap height); centered on sx if center. */
    private static void text(Matrix4f m, VertexConsumerProvider vc, String s, float sx, float sy, float h, int argb, boolean center, float dz) {
        draw(m, vc, Text.literal(s), sx, sy, h, argb, center, dz);
    }

    private static void textC(Matrix4f m, VertexConsumerProvider vc, Text s, float sx, float sy, float h, int argb) {
        draw(m, vc, s, sx, sy, h, argb, true, 0.02f);
    }

    private static void draw(Matrix4f m, VertexConsumerProvider vc, Text s, float sx, float sy, float h, int argb, boolean center, float dz) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float k = h / 7f / 16f;                                    // block units per font unit
        Matrix4f mt = new Matrix4f(m).translate(X(sx), Y(sy), Z(dz)).scale(-k, -k, -k);   // reads left->right from the front (-Z)
        float x = center ? -tr.getWidth(s) / 2f : 0f;
        tr.draw(s, x, 0, argb, false, mt, vc, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, FULL);
    }
}
