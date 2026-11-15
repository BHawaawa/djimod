package com.example.avatadrone.client;

import com.example.avatadrone.client.DjiGfx;
import com.example.avatadrone.client.Rc2View;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class McDjiGfx
implements DjiGfx {
    static final int[] FONT_SIZES;
    private static final Identifier[] REGULAR;
    private static final Identifier[] BOLD;
    private static final int[] ATLAS_CELLS;
    private static final Identifier[] ATLAS;
    private static final Map<Identifier, AbstractTexture> filtered;
    private static final Map<TextKey, TextMetrics> TEXT_CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TextKey, TextMetrics> eldest) {
            return size() > 2048;
        }
    };
    static void clearCaches() {
        TEXT_CACHE.clear();
        filtered.clear();
    }
    private final DrawContext ctx;
    private final TextRenderer tr;
    private final float s;
    private static final float MIN_TEXT_PX = 10.0f;
    static float TEXT_BOOST;
    private static final float MAX_TEXT_PX = 60.0f;

    private static float fixedPx(float f) {
        return Math.min(Math.max(f * TEXT_BOOST, MIN_TEXT_PX), MAX_TEXT_PX);
    }

    McDjiGfx(DrawContext drawContext) {
        this.ctx = drawContext;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        this.tr = minecraftClient.textRenderer;
        Matrix4f matrix4f = drawContext.getMatrices().peek().getPositionMatrix();
        float f = (float)Math.hypot(matrix4f.m00(), matrix4f.m01());
        this.s = Math.max(0.05f, f * (float)minecraftClient.getWindow().getScaleFactor());
        drawContext.getMatrices().push();
        drawContext.getMatrices().scale(1.0f / this.s, 1.0f / this.s, 1.0f);
    }

    void end() {
        this.ctx.draw();
        this.ctx.getMatrices().pop();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public float scale() {
        return this.s;
    }

    @Override
    public void fillPx(int n, int n2, int n3, int n4, int n5) {
        if (n3 > n && n4 > n2) {
            this.ctx.fill(n, n2, n3, n4, n5);
        }
    }

    @Override
    public void gradientPx(int n, int n2, int n3, int n4, int n5, int n6) {
        if (n3 > n && n4 > n2) {
            this.ctx.fillGradient(n, n2, n3, n4, n5, n6);
        }
    }

    @Override
    public void batch(Runnable runnable) {
        this.ctx.draw(runnable);
    }

    @Override
    public void icon(int n, float f, float f2, float f3, float f4, int n2) {
        if ((n2 >>> 24 & 0xFF) == 0) {
            return;
        }
        float f5 = Math.max(f3, f4) * this.s;
        int n3 = f5 <= 34.0f ? 0 : (f5 <= 68.0f ? 1 : 2);
        int n4 = ATLAS_CELLS[n3];
        Identifier identifier = ATLAS[n3];
        McDjiGfx.bilinear(identifier);
        this.ctx.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float)(n2 >> 16 & 0xFF) / 255.0f, (float)(n2 >> 8 & 0xFF) / 255.0f, (float)(n2 & 0xFF) / 255.0f, (float)(n2 >>> 24 & 0xFF) / 255.0f);
        int n5 = Math.round(f * this.s);
        int n6 = Math.round(f2 * this.s);
        int n7 = Math.round((f + f3) * this.s);
        int n8 = Math.round((f2 + f4) * this.s);
        this.ctx.drawTexture(identifier, n5, n6, n7 - n5, n8 - n6, n % 8 * n4, n / 8 * n4, n4, n4, n4 * 8, n4 * 8);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void bilinear(Identifier identifier) {
        AbstractTexture abstractTexture = MinecraftClient.getInstance().getTextureManager().getTexture(identifier);
        if (filtered.get(identifier) != abstractTexture) {
            abstractTexture.setFilter(true, false);
            filtered.put(identifier, abstractTexture);
        }
    }

    private static int provider(float f) {
        int n = 0;
        for (int i = 1; i < FONT_SIZES.length; ++i) {
            if (!(Math.abs((float)FONT_SIZES[i] - f) < Math.abs((float)FONT_SIZES[n] - f))) continue;
            n = i;
        }
        return n;
    }

    private Text styled(String string, boolean bl, int n) {
        return Text.literal(string).setStyle(Style.EMPTY.withFont(bl ? BOLD[n] : REGULAR[n]));
    }

    private TextMetrics metrics(String string, boolean bold, int provider) {
        TextKey key = new TextKey(string, bold, provider);
        TextMetrics hit = TEXT_CACHE.get(key);
        if (hit != null) return hit;
        Text styled = this.styled(string, bold, provider);
        Text plain = Text.literal(string);
        int styledWidth = this.tr.getWidth(styled);
        int plainWidth = this.tr.getWidth(plain);
        TextMetrics result = styledWidth < plainWidth * 0.75f && plainWidth > 0
                ? new TextMetrics(plain, plainWidth, true)
                : new TextMetrics(styled, styledWidth, false);
        TEXT_CACHE.put(key, result);
        return result;
    }

    @Override
    public float text(String string, float f, float f2, float f3, int n, boolean bl, boolean bl2) {
        if (string == null || string.isEmpty()) {
            return 0.0f;
        }
        float f4 = McDjiGfx.fixedPx(f3 * this.s);
        int n2 = McDjiGfx.provider(f4);
        TextMetrics metrics = this.metrics(string, bl, n2);
        float f7 = metrics.plain ? f4 / 9.0f : f4 / (float)FONT_SIZES[n2];
        this.ctx.getMatrices().push();
        this.ctx.getMatrices().translate(Math.round(f * this.s), Math.round(f2 * this.s), 0.0f);
        this.ctx.getMatrices().scale(f7, f7, 1.0f);
        this.ctx.drawText(this.tr, metrics.text, 0, 0, n, bl2);
        this.ctx.getMatrices().pop();
        return (float)metrics.width * f7 / this.s;
    }

    @Override
    public float textWidth(String string, float f, boolean bl) {
        if (string == null || string.isEmpty()) {
            return 0.0f;
        }
        float f2 = McDjiGfx.fixedPx(f * this.s);
        int n = McDjiGfx.provider(f2);
        TextMetrics metrics = this.metrics(string, bl, n);
        float f3 = metrics.plain ? f2 / 9.0f : f2 / (float)FONT_SIZES[n];
        return (float)metrics.width * f3 / this.s;
    }

    @Override
    public void pushClip(float f, float f2, float f3, float f4) {
        this.ctx.draw();
        Matrix4f matrix4f = this.ctx.getMatrices().peek().getPositionMatrix();
        Vector3f vector3f = matrix4f.transformPosition(new Vector3f(f * this.s, f2 * this.s, 0.0f));
        Vector3f vector3f2 = matrix4f.transformPosition(new Vector3f((f + f3) * this.s, (f2 + f4) * this.s, 0.0f));
        this.ctx.enableScissor((int)Math.floor(Math.min(vector3f.x, vector3f2.x)), (int)Math.floor(Math.min(vector3f.y, vector3f2.y)), (int)Math.ceil(Math.max(vector3f.x, vector3f2.x)), (int)Math.ceil(Math.max(vector3f.y, vector3f2.y)));
    }

    @Override
    public void popClip() {
        this.ctx.draw();
        this.ctx.disableScissor();
    }

    public boolean mapTiles(float x, float y, float w, float h, double cx, double cz, double scale) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaeroworldmap")) return false;
        try {
            return XaeroFlightMap.draw(ctx, Math.round(x * s), Math.round(y * s), Math.round(w * s), Math.round(h * s),
                    cx, cz, scale * s);
        } catch (Throwable ex) {
            return false;
        } finally {
            ctx.draw();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    @Override
    public boolean feed(float f, float f2, float f3, float f4) {
        boolean bl = Rc2View.drawFeedInto(this.ctx, Math.round(f * this.s), Math.round(f2 * this.s), Math.round(f3 * this.s), Math.round(f4 * this.s));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return bl;
    }

    @Override
    public boolean assist(float f, float f2, float f3, float f4) {
        boolean bl = Rc2View.drawAssistInto(this.ctx, Math.round(f * this.s), Math.round(f2 * this.s), Math.round(f3 * this.s), Math.round(f4 * this.s));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return bl;
    }

    static {
        int n;
        TEXT_BOOST = 1.0f;
        FONT_SIZES = new int[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 26, 28, 30, 32, 35, 38, 41, 44, 48, 52, 56, 60, 65, 70};
        REGULAR = new Identifier[FONT_SIZES.length];
        BOLD = new Identifier[FONT_SIZES.length];
        ATLAS_CELLS = new int[]{32, 64, 128};
        ATLAS = new Identifier[ATLAS_CELLS.length];
        filtered = new HashMap<Identifier, AbstractTexture>();
        for (n = 0; n < FONT_SIZES.length; ++n) {
            McDjiGfx.REGULAR[n] = new Identifier("djimod", "dji_r_" + FONT_SIZES[n]);
            McDjiGfx.BOLD[n] = new Identifier("djimod", "dji_b_" + FONT_SIZES[n]);
        }
        for (n = 0; n < ATLAS_CELLS.length; ++n) {
            McDjiGfx.ATLAS[n] = new Identifier("djimod", "textures/gui/dji_fly_icons_" + ATLAS_CELLS[n] + ".png");
        }
    }

    private record TextKey(String value, boolean bold, int provider) {}
    private record TextMetrics(Text text, int width, boolean plain) {}
}

