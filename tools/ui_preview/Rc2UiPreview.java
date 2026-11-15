import com.example.avatadrone.client.DjiGfx;
import com.example.avatadrone.client.Rc2FlightUi;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayDeque;

/**
 * Offline preview of the RC 2 flight interface: renders Rc2FlightUi through a Java2D DjiGfx
 * (same TTF fonts and icon atlases as the game) with fake telemetry, one PNG per scenario.
 * Usage: java -cp <mc classpath>:<mod classes>:. Rc2UiPreview <assets dir> <out dir> [scale]
 */
public class Rc2UiPreview {
    static String assets;

    static final class Gfx implements DjiGfx {
        final BufferedImage img;
        final Graphics2D g2;
        final float s;
        final BufferedImage[] atlas = new BufferedImage[3];
        final Font regular, bold;
        final ArrayDeque<Shape> clips = new ArrayDeque<>();

        Gfx(int w, int h, float s, BufferedImage background) throws Exception {
            this.s = s;
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(background, 0, 0, w, h, null);
            int[] cells = {32, 64, 128};
            for (int i = 0; i < 3; i++)
                atlas[i] = ImageIO.read(new File(assets, "textures/gui/dji_fly_icons_" + cells[i] + ".png"));
            regular = Font.createFont(Font.TRUETYPE_FONT, new File(assets, "font/dji_sans_regular.ttf"));
            bold = Font.createFont(Font.TRUETYPE_FONT, new File(assets, "font/dji_sans_bold.ttf"));
        }

        public float scale() { return s; }

        public void fillPx(int x0, int y0, int x1, int y1, int argb) {
            if (x1 <= x0 || y1 <= y0) return;
            g2.setColor(new Color(argb, true));
            g2.fillRect(x0, y0, x1 - x0, y1 - y0);
        }

        public void gradientPx(int x0, int y0, int x1, int y1, int top, int bottom) {
            g2.setPaint(new GradientPaint(0, y0, new Color(top, true), 0, y1, new Color(bottom, true)));
            g2.fillRect(x0, y0, x1 - x0, y1 - y0);
        }

        public void batch(Runnable r) { r.run(); }

        public void icon(int index, float x, float y, float w, float h, int argb) {
            float px = Math.max(w, h) * s;
            int level = px <= 34 ? 0 : px <= 68 ? 1 : 2;
            int cell = new int[]{32, 64, 128}[level];
            BufferedImage src = atlas[level].getSubimage((index % 8) * cell, (index / 8) * cell, cell, cell);
            BufferedImage tinted = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
            float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f,
                    gg = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
            for (int yy = 0; yy < cell; yy++)
                for (int xx = 0; xx < cell; xx++) {
                    int p = src.getRGB(xx, yy);
                    int pa = (int) (((p >>> 24) & 255) * a), pr = (int) (((p >> 16) & 255) * r),
                            pg = (int) (((p >> 8) & 255) * gg), pb = (int) ((p & 255) * b);
                    tinted.setRGB(xx, yy, (pa << 24) | (pr << 16) | (pg << 8) | pb);
                }
            int X0 = Math.round(x * s), Y0 = Math.round(y * s), X1 = Math.round((x + w) * s), Y1 = Math.round((y + h) * s);
            g2.drawImage(tinted, X0, Y0, X1 - X0, Y1 - Y0, null);
        }

        Font font(float em, boolean b) { return (b ? bold : regular).deriveFont(em * s); }

        public float text(String str, float x, float y, float em, int argb, boolean b, boolean shadow) {
            if (str == null || str.isEmpty()) return 0;
            Font f = font(em, b);
            g2.setFont(f);
            float bx = Math.round(x * s), by = Math.round(y * s) + 0.88f * em * s;
            if (shadow) {
                int c = argb;
                int sh = (c & 0xFF000000) | ((((c >> 16) & 255) / 4) << 16) | ((((c >> 8) & 255) / 4) << 8) | ((c & 255) / 4);
                g2.setColor(new Color(sh, true));
                g2.drawString(str, bx + 1, by + 1);
            }
            g2.setColor(new Color(argb, true));
            g2.drawString(str, bx, by);
            return textWidth(str, em, b);
        }

        public float textWidth(String str, float em, boolean b) {
            if (str == null || str.isEmpty()) return 0;
            return (float) font(em, b).getStringBounds(str, g2.getFontRenderContext()).getWidth() / s;
        }

        public void pushClip(float x, float y, float w, float h) {
            clips.push(g2.getClip() == null ? new Rectangle(0, 0, img.getWidth(), img.getHeight()) : g2.getClip());
            g2.clipRect(Math.round(x * s), Math.round(y * s), Math.round(w * s), Math.round(h * s));
        }

        public void popClip() { g2.setClip(clips.pop()); }

        public boolean mapTiles(float x, float y, float w, float h, double cx, double cz, double scale) {
            // stand-in terrain like the reference light map
            g2.setColor(new Color(0xEBEEF3));
            g2.fillRect(Math.round(x * s), Math.round(y * s), Math.round(w * s), Math.round(h * s));
            g2.setColor(new Color(0xDCE0E6));
            for (int i = -2; i < 12; i++) {
                int px = Math.round((x + i * 60 + 10) * s);
                g2.drawLine(px, Math.round(y * s), px + Math.round(80 * s), Math.round((y + h) * s));
            }
            return true;
        }

        public boolean assist(float x, float y, float w, float h) { return feed(x, y, w, h); }

        public boolean feed(float x, float y, float w, float h) {
            g2.drawImage(background, Math.round(x * s), Math.round(y * s), Math.round(w * s), Math.round(h * s), null);
            return true;
        }
    }

    static BufferedImage background;

    static final class FakeBridge implements Rc2FlightUi.Bridge {
        Rc2FlightUi.Telemetry src = new Rc2FlightUi.Telemetry();
        public void fill(Rc2FlightUi.Telemetry t) {
            for (Field f : Rc2FlightUi.Telemetry.class.getFields()) {
                try {
                    if (!java.lang.reflect.Modifier.isFinal(f.getModifiers())) f.set(t, f.get(src));
                } catch (Exception e) { throw new RuntimeException(e); }
            }
            System.arraycopy(src.obstacle, 0, t.obstacle, 0, 4);
        }
        public void exit() {}
        public void takeoffOrLand() {}
        public void returnHome() {}
        public void emergencyStop() {}
        public void setMode(int mode) {}
        public void setZoom(float zoom) {}
        public void setFpvGimbal(boolean fpv) {}
        public String modeDescription(int m) {
            return new String[]{"平稳 C · 上升 2 / 下降 1.5 / 水平 6 m/s", "普通 N · 上升 6 / 下降 6 / 水平 16 m/s",
                    "运动 S · 上升 10 / 下降 10 / 水平 18 m/s"}[m];
        }
    }

    static void set(String name, Object v) throws Exception {
        Field f = Rc2FlightUi.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, v);
    }

    static BufferedImage makeBackground(int w, int h) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x3a3a3c), 0, h * 0.6f, new Color(0x2c2c2e)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x262627));
        g.fillPolygon(new int[]{0, (int) (w * .2), (int) (w * .45), 0}, new int[]{(int) (h * .62), (int) (h * .58), (int) (h * .74), (int) (h * .78)}, 4);
        g.fillPolygon(new int[]{(int) (w * .55), (int) (w * .8), w, w}, new int[]{(int) (h * .76), (int) (h * .6), (int) (h * .63), (int) (h * .82)}, 4);
        g.setColor(new Color(0x343436));
        g.fillRect(0, (int) (h * .8), w, h);
        g.dispose();
        return b;
    }

    public static void main(String[] args) throws Exception {
        assets = args[0];
        File out = new File(args[1]);
        out.mkdirs();
        float s = args.length > 2 ? Float.parseFloat(args[2]) : 1f;
        int W = Math.round(1280 * s), H = Math.round(680 * s);
        background = makeBackground(W, H);
        FakeBridge fb = new FakeBridge();
        Rc2FlightUi.bridge = fb;
        Rc2FlightUi.Telemetry t = fb.src;
        // reference screen: on the ground, no satellites, prompt shown
        t.connected = true; t.telemetry = true; t.paired = true; t.mode = 1; t.battery = 99; t.remainingSec = 0;
        t.rcBars = 4; t.fourGBars = 4; t.satellites = 0; t.state = "可以起飞"; t.obstacleWarn = true;
        t.autoReturnPct = 25; t.forcedLandPct = 8; t.zoom = 1; t.yaw = 30;
        t.homeX = 10; t.homeZ = 20; t.pilotKnown = true; t.pilotX = 12; t.pilotZ = 22;
        render(out, "01-reference-ground", W, H, s);
        // cover screen: flying
        t.satellites = 26; t.obstacleWarn = false; t.armed = true; t.state = "飞行中"; t.remainingSec = 1800;
        t.altitude = 100; t.homeDistance = 800; t.vSpeed = 1.0; t.hSpeed = 1.0; t.battery = 99;
        set("cameraTab", 1);
        set("recording", true); set("recordStart", System.nanoTime() - 12_000_000_000L);
        render(out, "02-flying", W, H, s);
        set("recording", false);
        // panels
        t.armed = false; t.state = "可以起飞"; t.altitude = 0; t.homeDistance = 0; t.vSpeed = 0; t.hSpeed = 0; t.satellites = 25;
        set("panel", 7); render(out, "03-takeoff", W, H, s);
        t.armed = true; render(out, "04-land-rth", W, H, s);
        set("panel", 6); render(out, "05-focus", W, H, s);
        set("panel", 2); set("cameraTab", 0); render(out, "06-camera-photo", W, H, s);
        set("cameraTab", 1); render(out, "07-camera-video", W, H, s);
        set("panel", 0); set("waypointMode", true); render(out, "08-waypoint", W, H, s);
        set("waypointMode", false);
        set("panel", 4); render(out, "09-settings", W, H, s);
        set("panel", 0); set("mapExpanded", true); set("mapType", 0); render(out, "10-map-widget", W, H, s);
        set("mapType", 1); render(out, "11-attitude-widget", W, H, s);
        set("mapType", 2); render(out, "12-assist-widget", W, H, s);
        // mid-swipe from the map thumbnail towards the attitude ball
        set("mapType", 0); set("thumbPressed", true); set("thumbStartX", 200f);
        Rc2FlightUi.setMouse(110, 590, true); render(out, "12b-thumb-swipe", W, H, s);
        set("thumbPressed", false); Rc2FlightUi.setMouse(-1, -1, false);
        set("panel", 5); set("mapTab", 1); render(out, "12c-thumb-tap-attitude", W, H, s);
        set("panel", 0);
        set("mapExpanded", false); set("autoExposure", false); render(out, "13-pro", W, H, s);
        set("autoExposure", true);
        t.battery = 20; t.remainingSec = 488; t.rcBars = 2; t.fourGBars = 0; t.satellites = 10; t.altitude = 130;
        set("panel", 9); render(out, "14-warnings-4g", W, H, s);
        set("panel", 8); render(out, "15-battery", W, H, s);
        set("panel", 0);
        t.connected = false; t.telemetry = false; t.battery = -1; t.remainingSec = -1; t.satellites = -1; t.rcBars = 0;
        render(out, "16-no-telemetry", W, H, s);
    }

    static void render(File out, String name, int W, int H, float s) throws Exception {
        Gfx g = new Gfx(W, H, s, background);
        Rc2FlightUi.renderWith(g);
        g.g2.dispose();
        ImageIO.write(g.img, "png", new File(out, name + ".png"));
        System.out.println("wrote " + name);
    }
}
