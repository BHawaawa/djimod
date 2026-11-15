package com.example.avatadrone.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Compare the former text layout path with cached layout inside the same live client. */
public final class UiPerformanceChecks {
    private static final String[] LABELS = {"普通挡", "可以起飞", "云台俯仰", "地图加载中", "RC 图传距离",
            "Mini 4 Pro", "99", "23'00\"", "4K 60", "+0.3", "2.0m", "500m", "已锁定辅助视角：前"};
    private static volatile float sink;
    private static final Identifier[][] FONTS = new Identifier[2][McDjiGfx.FONT_SIZES.length];
    static {
        for (int bold = 0; bold < 2; bold++) for (int i = 0; i < McDjiGfx.FONT_SIZES.length; i++)
            FONTS[bold][i] = new Identifier("djimod", (bold == 1 ? "dji_b_" : "dji_r_") + McDjiGfx.FONT_SIZES[i]);
    }
    private static float reference(MinecraftClient c, McDjiGfx gfx, String value, float size, boolean bold) {
        float px = Math.min(Math.max(size * gfx.scale() * McDjiGfx.TEXT_BOOST, 10), 60);
        int provider = 0;
        for (int i = 1; i < McDjiGfx.FONT_SIZES.length; i++)
            if (Math.abs(McDjiGfx.FONT_SIZES[i] - px) < Math.abs(McDjiGfx.FONT_SIZES[provider] - px)) provider = i;
        Text styled = Text.literal(value).setStyle(Style.EMPTY.withFont(FONTS[bold ? 1 : 0][provider]));
        Text plain = Text.literal(value);
        float styledWidth = c.textRenderer.getWidth(styled), plainWidth = c.textRenderer.getWidth(plain);
        boolean fallback = plainWidth > 0 && styledWidth < plainWidth * .75f;
        return c.textRenderer.getWidth(fallback ? plain : styled)
                * (fallback ? px / 9 : px / McDjiGfx.FONT_SIZES[provider]) / gfx.scale();
    }
    private static long measure(MinecraftClient c, McDjiGfx gfx, boolean cached, int count) {
        float total = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            String value = LABELS[i % LABELS.length];
            float size = 18 + (i % 3) * 2;
            total += cached ? gfx.textWidth(value, size, (i & 1) == 0)
                    : reference(c, gfx, value, size, (i & 1) == 0);
        }
        long elapsed = System.nanoTime() - start;
        sink = total;
        return elapsed;
    }
    public static void run(MinecraftClient client) throws Exception {
        checkRasterClipping();
        checkPanelVisibility();
        McDjiGfx gfx = new McDjiGfx(new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()));
        try {
            for (int i = 0; i < 156; i++) {
                String value = LABELS[i % LABELS.length];
                float size = 18 + (i % 3) * 2;
                float expected = reference(client, gfx, value, size, (i & 1) == 0);
                if (Math.abs(expected - gfx.textWidth(value, size, (i & 1) == 0)) > .001f)
                    throw new AssertionError("Cached layout width changed: " + value);
            }
            measure(client, gfx, false, 3000); measure(client, gfx, true, 3000);
            long[] before = new long[5], after = new long[5];
            for (int i = 0; i < 5; i++) {
                before[i] = measure(client, gfx, false, 10000);
                after[i] = measure(client, gfx, true, 10000);
            }
            Arrays.sort(before); Arrays.sort(after);
            String result = String.format(Locale.ROOT,
                    "UI_PERFORMANCE_QA PASS equal widths; 10000 layouts median baseline=%.3fms cached=%.3fms ratio=%.2fx%n",
                    before[2] / 1e6, after[2] / 1e6, (double)before[2] / after[2]);
            Files.writeString(Path.of("ui-performance-result.txt"), result);
            System.out.print(result);
            var cacheField = McDjiGfx.class.getDeclaredField("TEXT_CACHE"); cacheField.setAccessible(true);
            var cache = (java.util.Map<?, ?>)cacheField.get(null);
            for (int i = 0; i < 2100; i++) gfx.textWidth("QA " + i, 18, false);
            if (cache.size() > 2048) throw new AssertionError("HUD cache is not bounded");
            McDjiGfx.clearCaches();
            if (!cache.isEmpty()) throw new AssertionError("Resource reload must clear HUD metrics");
            if (Math.abs(reference(client, gfx, LABELS[0], 18, false) - gfx.textWidth(LABELS[0], 18, false)) > .001f)
                throw new AssertionError("HUD width changed after cache reset");
            System.out.println("UI_CACHE_QA PASS bounded entries and resource reset");
        } finally { gfx.end(); }
    }

    private static void checkPanelVisibility() throws Exception {
        String[] names = {"panel", "mapTab", "mapType", "mapExpanded", "assistFloating"};
        java.lang.reflect.Field[] fields = new java.lang.reflect.Field[names.length];
        Object[] saved = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = Rc2FlightUi.class.getDeclaredField(names[i]); fields[i].setAccessible(true);
            saved[i] = fields[i].get(null);
        }
        try {
            fields[0].setInt(null, 0); fields[2].setInt(null, 2);
            fields[3].setBoolean(null, true); fields[4].setBoolean(null, false);
            if (!Rc2FlightUi.assistVisible() || Rc2FlightUi.coversMainFeed()) throw new AssertionError("Main HUD feed");
            fields[0].setInt(null, 1);
            if (!Rc2FlightUi.assistVisible()) throw new AssertionError("Partial mode panel must keep assist live");
            fields[0].setInt(null, 4);
            if (Rc2FlightUi.assistVisible()) throw new AssertionError("Settings covers assist");
            fields[0].setInt(null, 5); fields[1].setInt(null, 0);
            if (!Rc2FlightUi.coversMainFeed() || Rc2FlightUi.assistVisible()) throw new AssertionError("Fullscreen map");
            fields[1].setInt(null, 2);
            if (Rc2FlightUi.coversMainFeed() || !Rc2FlightUi.assistVisible()) throw new AssertionError("Fullscreen assist");
            fields[0].setInt(null, 0); fields[3].setBoolean(null, false);
            if (Rc2FlightUi.assistVisible()) throw new AssertionError("Collapsed assist");
            fields[4].setBoolean(null, true);
            if (!Rc2FlightUi.assistVisible()) throw new AssertionError("Floating assist");
            System.out.println("UI_VISIBILITY_QA PASS map, settings, partial panels, collapsed and floating assist");
        } finally {
            for (int i = 0; i < fields.length; i++) fields[i].set(null, saved[i]);
        }
    }

    private static void checkRasterClipping() throws Exception {
        String[] names = {"g", "s", "rasterLeft", "rasterTop", "rasterRight", "rasterBottom"};
        java.lang.reflect.Field[] fields = new java.lang.reflect.Field[names.length];
        Object[] saved = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = Rc2FlightUi.class.getDeclaredField(names[i]); fields[i].setAccessible(true);
            saved[i] = fields[i].get(null);
        }
        int[] pixels = new int[64 * 64];
        DjiGfx recorder = (DjiGfx)java.lang.reflect.Proxy.newProxyInstance(DjiGfx.class.getClassLoader(),
                new Class<?>[]{DjiGfx.class}, (proxy, method, args) -> {
                    if (method.getName().equals("batch")) ((Runnable)args[0]).run();
                    if (method.getName().equals("fillPx")) {
                        for (int y = Math.max(0, (int)args[1]); y < Math.min(64, (int)args[3]); y++)
                            for (int x = Math.max(0, (int)args[0]); x < Math.min(64, (int)args[2]); x++)
                                pixels[y * 64 + x] = (int)args[4];
                    }
                    return method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == float.class ? 1f : null;
                });
        var ring = Rc2FlightUi.class.getDeclaredMethod("ring", float.class, float.class, float.class, float.class, int.class);
        ring.setAccessible(true);
        try {
            fields[0].set(null, recorder); fields[1].setFloat(null, 1f);
            for (float[] spec : new float[][]{{32,32,25}, {-10,32,40}, {70,-10,80}, {32,32,100}}) {
                fields[2].setDouble(null, -512); fields[3].setDouble(null, -512);
                fields[4].setDouble(null, 512); fields[5].setDouble(null, 512);
                Arrays.fill(pixels, 0);
                ring.invoke(null, spec[0], spec[1], spec[2], 3f, 0x806FB9FF);
                int[] expected = pixels.clone();
                fields[2].setDouble(null, 0); fields[3].setDouble(null, 0);
                fields[4].setDouble(null, 64); fields[5].setDouble(null, 64);
                Arrays.fill(pixels, 0);
                ring.invoke(null, spec[0], spec[1], spec[2], 3f, 0x806FB9FF);
                if (!Arrays.equals(expected, pixels)) throw new AssertionError("Clipping changed visible ring pixels");
            }
            long start = System.nanoTime();
            ring.invoke(null, 32f, 32f, 10000f, 3f, 0x806FB9FF);
            if (System.nanoTime() - start > 1_000_000_000L) throw new AssertionError("Offscreen RC ring exceeded raster budget");
            System.out.println("UI_RASTER_QA PASS clipped ring pixels unchanged; 10000px ring bounded to viewport");
        } finally {
            for (int i = 0; i < fields.length; i++) fields[i].set(null, saved[i]);
        }
    }
}
