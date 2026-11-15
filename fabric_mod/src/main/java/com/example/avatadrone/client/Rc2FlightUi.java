package com.example.avatadrone.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;

public final class Rc2FlightUi {
   public static final int W = 1280;
   public static final int H = 680;
   private static final int WHITE = -1;
   private static final int SUB = -6513506;
   private static final int DIM = -7433578;
   private static final int GREEN = -14884475;
   private static final int BATT_YELLOW = -548325;
   private static final int RED = -906460;
   private static final int CHIP_YELLOW = -23541;
   private static final int CHIP_RED = -57826;
   private static final int RC_ORANGE = -1335487;
   private static final int GNSS_YELLOW = -90355;
   private static final int DJI_YELLOW = -12218;
   private static final int ZOOM_X = -16895;
   private static final int REC_RED = -1827031;
   private static final int BAR_OFF = -10855844;
   private static final int PROMPT_BG = -348505542;
   private static final int TK_PANEL = -181850572;
   private static final int TK_BUTTON = -10789022;
   private static final int FOCUS_BAR = -230012340;
   private static final int FOCUS_SEP = -10724258;
   private static final int WP_HEAD = -226854530;
   private static final int WP_BODY = -230867649;
   private static final int WP_SEG = -10197916;
   private static final int WP_BORDER = -9868949;
   private static final int SET_BG = -14539741;
   private static final int SET_SIDE = -15198184;
   private static final int SET_SECTION = -14079444;
   private static final int SET_LINE = 352321535;
   private static final int SEG = -11710642;
   private static final int SEG_SEL = -460293;
   private static final int BLUE = -14312711;
   private static final int TOGGLE_OFF = -10526879;
   private static final int MENU_L = -181128140;
   private static final int MENU_R = -182115035;
   private static final int DISC = -435680499;
   private static final int POP = -179680692;
   private static final int SHADOW = 1493172224;
   private static final String NA = "N/A";
   private static final int I_HOME = 0;
   private static final int I_BACK = 1;
   private static final int I_WAYPOINT = 2;
   private static final int I_FOCUS = 3;
   private static final int I_TAKEOFF = 4;
   private static final int I_LAND = 5;
   private static final int I_RTH = 6;
   private static final int I_FILM = 7;
   private static final int I_PHOTO_FRAME = 8;
   private static final int I_PLAY = 9;
   private static final int I_MAP_APP = 10;
   private static final int I_ATT_MINI = 11;
   private static final int I_ASSIST_MINI = 12;
   private static final int I_ALT = 13;
   private static final int I_DIST = 14;
   private static final int I_OBST = 15;
   private static final int I_OBST_UD = 16;
   private static final int I_GNSS = 18;
   private static final int I_GNSS_OFF = 19;
   private static final int I_AUTO = 20;
   private static final int I_PRO = 21;
   private static final int I_EYE = 22;
   private static final int I_ORBIT = 23;
   private static final int I_LIST = 24;
   private static final int I_PLUS = 25;
   private static final int I_CHEV_UP = 26;
   private static final int I_DOTS = 27;
   private static final int I_AEB = 28;
   private static final int I_BURST = 29;
   private static final int I_TIMER = 30;
   private static final int I_CAMERA = 31;
   private static final int I_VIDEO = 32;
   private static final int I_MASTER = 33;
   private static final int I_APERTURE = 34;
   private static final int I_NIGHT = 35;
   private static final int I_SLOMO = 36;
   private static final int I_LOCATE = 37;
   private static final int I_CORNER = 38;
   private static final int I_EXPAND = 39;
   private static final int I_NAV = 40;
   private static final int I_CLOSE = 41;
   private static final int I_CHECK = 42;
   private static final int I_TRASH = 43;
   private static final int I_WARN = 44;
   private static final int I_CHEV_LEFT = 45;
   private static final int I_CHEV_RIGHT = 46;
   private static final int I_CHEV_DOWN = 47;
   private static final int I_HOME_MARK = 48;
   private static final int I_DROP = 49;
   private static final int I_PLANE = 50;
   private static final int I_TREES = 51;
   private static final int I_TARGET = 53;
   private static final int PANEL_NONE = 0;
   private static final int PANEL_MODE = 1;
   private static final int PANEL_CAMERA = 2;
   private static final int PANEL_PLAYBACK = 3;
   private static final int PANEL_SETTINGS = 4;
   private static final int PANEL_MAP = 5;
   private static final int PANEL_FOCUS = 6;
   private static final int PANEL_TAKEOFF = 7;
   private static final int PANEL_BATTERY = 8;
   private static final int PANEL_FOURG = 9;
   private static final int PANEL_WP_MORE = 10;
   private static final int PANEL_PARAMS = 11;
   private static int panel = 0;
   public static Rc2FlightUi.Bridge bridge;
   private static final Rc2FlightUi.Telemetry T = new Rc2FlightUi.Telemetry();
   private static int cameraTab = 1;
   private static int photoMode;
   private static int videoMode;
   private static int zoomIndex;
   private static boolean autoExposure = true;
   private static boolean recording;
   private static long recordStart;
   private static int photoCount;
   private static long flashUntil;
   private static int focusMode;
   private static int trackingMode;
   private static int resIndex;
   private static int fpsIndex = 2;
   private static int colorMode;
   private static float ev = 0.3F;
   private static int shutterIndex = 4;
   private static int isoIndex = 3;
   private static int wbIndex;
   private static final String[] RES = new String[]{"4K", "2.7K", "1080P"};
   private static final String[] FPS = new String[]{"24", "30", "60", "100"};
   private static final String[] COLOR = new String[]{"普通", "HLG", "D-Log M"};
   private static final String[] SHUTTER = new String[]{"1/2000", "1/500", "1/120", "1/60", "1/30", "1/15"};
   private static final String[] ISO = new String[]{"100", "200", "400", "720", "1600", "3200"};
   private static final String[] WB = new String[]{"自动", "3200K", "4400K", "5200K", "5600K", "6500K"};
   private static final String[] PHOTO_MODES = new String[]{"单拍", "AEB连拍", "连拍", "定时拍"};
   private static final int[] PHOTO_ICONS = new int[]{8, 28, 29, 30};
   private static final String[] VIDEO_MODES = new String[]{"普通", "夜景", "慢动作"};
   private static final int[] VIDEO_ICONS = new int[]{7, 35, 36};
   private static final String[] MODE_NAMES = new String[]{"平稳挡", "普通挡", "运动挡"};
   private static final String[] MODE_LETTERS = new String[]{"C", "N", "S"};
   private static final String[] MODE_EN = new String[]{"Cine", "Normal", "Sport"};
   private static int settingsTab;
   private static double rasterLeft;
   private static double rasterTop;
   private static double rasterRight;
   private static double rasterBottom;
   private static final boolean[][] toggles = new boolean[5][8];
   private static final int[][] segments = new int[5][8];
   private static final float[][] sliders = new float[5][8];
   private static boolean grid;
   private static boolean histogram;
   private static boolean overexposure;
   private static int mapType;
   private static boolean mapExpanded;
   private static final float ASSIST_BASE_W = 304.0F;
   private static final float ASSIST_BASE_H = 178.0F;
   private static final float ASSIST_LARGE_W = 360.0F;
   private static final float ASSIST_LARGE_H = 211.0F;
   private static float assistX = 30.0F;
   private static float assistY = 462.0F;
   private static boolean assistFloating;
   private static boolean assistLarge;
   private static int assistSlotType;
   private static boolean assistPickup;
   private static boolean assistPicked;
   private static long assistPickupAt;
   private static float assistPressMouseX;
   private static float assistPressMouseY;
   private static float assistPressBaseX;
   private static float assistPressBaseY;
   private static int mapTab;
   private static boolean showCoverage;
   private static boolean showRcCoverage = true;
   private static boolean waypointMode;
   private static boolean waypointCollapsed;
   private static int waypointTab;
   private static final List<double[]> waypoints = new ArrayList<>();
   private static int wpSpeed = 1;
   private static int wpLens;
   private static int wpFinish;
   private static int wpLost;
   private static int wpStart;
   private static final ArrayDeque<double[]> trail = new ArrayDeque<>();
   private static long lastTrailSample;
   private static final List<Long> gallery = new ArrayList<>();
   private static int gallerySelected = -1;
   private static String toastText;
   private static long toastUntil;
   private static String dismissedPrompt;
   private static String promptShownText;
   private static long promptShownAt;
   private static float fourGAnchor = 1010.0F;
   private static double mouseX = -1.0;
   private static double mouseY = -1.0;
   private static boolean mouseIn;
   private static boolean mouseDown;
   private static DjiGfx g;
   private static float s = 1.0F;
   private static long now;
   private static int layer;
   private static int topLayer;
   private static final List<Rc2FlightUi.Hit> hits = new ArrayList<>();
   private static String holdId;
   private static long holdStart;
   private static Runnable holdAction;
   private static int assistHoldDir = -1;
   private static long assistHoldStart;
   private static DoubleConsumer dragTarget;
   private static float dragX;
   private static float dragW;
   private static final String[] SETTING_TABS;
   private static final double[] SPAN;
   private static final float HUD_TEXT = 1.22F;
   private static final float PANEL_TEXT = 1.12F;
   private static final float LEGACY_TEXT_SCALE = 2.25F;
   private static final float TOP_EM = 30.0F;
   private static final float TOP_ICON = 32.0F;
   private static final float SAT_EM = 26.0F;
   private static final float CHIP_EM = 25.0F;
   private static final float BOTTOM_EM = 28.0F;
   private static final float BOTTOM_ICON = 28.0F;
   private static final float CTRL_EM = 26.0F;
   private static final float TOAST_EM = 21.0F;
   private static float topRightStart = 640.0F;
   private static final float THUMB_X = 30.0F;
   private static final float THUMB_W = 292.0F;
   private static final float THUMB_H = 164.0F;
   private static final float THUMB_Y = 468.0F;
   private static boolean thumbPressed;
   private static boolean thumbDrawing;
   private static float thumbStartX;
   private static float thumbStartY;
   private static float thumbAnim;

   private Rc2FlightUi() {
   }

   public static void onEntry() {
      panel = 0;
      gallerySelected = -1;
      dragTarget = null;
      holdId = null;
      assistPickup = false;
      assistPicked = false;
      dismissedPrompt = null;
      promptShownText = null;
      promptShownAt = 0L;
      if (bridge == null) {
         bridge = Rc2FlightBridge.INSTANCE;
      }

      bridge.fill(T);
      toast(T.keyHint, 5200L);
   }

   public static void onExit() {
      panel = 0;
      gallerySelected = -1;
      dragTarget = null;
      holdId = null;
      assistPickup = false;
      assistPicked = false;
      dismissedPrompt = null;
      promptShownText = null;
      promptShownAt = 0L;
      recording = false;
      flashUntil = 0L;
   }

   public static boolean closeTopPanel() {
      if (panel == 0) {
         if (assistFloating) {
            dockAssist();
            return true;
         } else if (mapExpanded) {
            mapExpanded = false;
            return true;
         } else {
            return false;
         }
      } else {
         if (panel == 3 && gallerySelected >= 0) {
            gallerySelected = -1;
         } else {
            panel = 0;
         }

         dragTarget = null;
         holdId = null;
         return true;
      }
   }

   public static void toast(String var0) {
      toast(var0, 2200L);
   }

   public static void toast(String var0, long var1) {
      if (var0 != null && !var0.isEmpty()) {
         toastText = var0;
         toastUntil = System.nanoTime() + var1 * 1000000L;
      }
   }

   public static void setMouse(double var0, double var2, boolean var4) {
      mouseX = var0;
      mouseY = var2;
      mouseIn = var4;
   }

   public static void render(DrawContext var0, float var1) {
      McDjiGfx var2 = new McDjiGfx(var0);

      try {
         renderWith(var2);
      } finally {
         var2.end();
      }
   }

   public static void renderWith(DjiGfx var0) {
      g = new Rc2FlightUi.Scaled(var0);
      s = var0.scale();
      rasterTop = 0.0;
      rasterLeft = 0.0;
      rasterRight = (double)(1280.0F * s);
      rasterBottom = (double)(680.0F * s);
      now = System.nanoTime();
      if (bridge == null) {
         bridge = Rc2FlightBridge.INSTANCE;
      }

      bridge.fill(T);
      hits.clear();
      topLayer = panel != 0 ? 1 : 0;
      layer = 0;
      if (recording && recordStart == 0L) {
         recordStart = now;
      }

      updateHold();
      updateAssistHold();
      updateDrag();
      updateAssistPickup();
      drawFeedOverlays();
      drawTracking();
      drawRadar();
      drawLeftRail();
      drawRightCluster();
      drawBottomLeft();
      drawBottomRight();
      drawTopLeft();
      drawTopRight();
      drawFloatingAssist();
      if (waypointMode) {
         drawWaypointPanel();
      }

      drawToast();
      if (flashUntil > now) {
         float var1 = clamp((float)(flashUntil - now) / 2.6E8F, 0.0F, 1.0F);
         rect(0.0F, 0.0F, 1280.0F, 680.0F, alpha(-1, var1 * 0.8F));
      }

      if (panel != 0) {
         layer = 1;
         drawPanel();
      }
   }

   private static void drawFeedOverlays() {
      if (grid) {
         for (int var0 = 1; var0 <= 2; var0++) {
            rect((float)(1280 * var0) / 3.0F, 0.0F, 1.2F, 680.0F, 1090519039);
            rect(0.0F, (float)(680 * var0) / 3.0F, 1280.0F, 1.2F, 1090519039);
         }
      }

      if (histogram) {
         float var5 = 150.0F;
         float var1 = 120.0F;
         rrect(var5, var1, 170.0F, 80.0F, 6.0F, -1728053248);

         for (int var2 = 0; var2 < 40; var2++) {
            float var3 = (float)var2 / 39.0F;
            float var4 = (float)(8.0 + 50.0 * Math.abs(Math.sin((double)var3 * 7.3 + 1.1)) * (1.0 - (double)var3 * 0.35));
            rect(var5 + 8.0F + (float)var2 * 3.9F, var1 + 72.0F - var4, 3.0F, var4, -855638017);
         }
      }

      if (overexposure) {
         for (float var6 = 130.0F; var6 < 530.0F; var6 += 22.0F) {
            for (float var7 = 900.0F; var7 < 1000.0F; var7 += 14.0F) {
               line(var7, var6, var7 + 10.0F, var6 - 10.0F, 2.0F, -1711318181);
            }
         }
      }
   }

   private static void drawTracking() {
      if (trackingMode > 0) {
         float var0 = 640.0F;
         float var1 = 340.0F;
         float var2 = 92.0F;
         float var3 = 68.0F;
         int var4 = -14884475;
         corner(var0 - var2, var1 - var3, 1, 1, var4);
         corner(var0 + var2, var1 - var3, -1, 1, var4);
         corner(var0 - var2, var1 + var3, 1, -1, var4);
         corner(var0 + var2, var1 + var3, -1, -1, var4);
         String var5 = new String[]{"", "跟随中", "锁定中", "环绕中"}[trackingMode];
         float var6 = g.textWidth(var5, 18.0F, false) + 28.0F;
         rrect(var0 - var6 / 2.0F, var1 - var3 - 42.0F, var6, 30.0F, 15.0F, -872415232);
         textC(var5, var0, var1 - var3 - 27.0F, 18.0F, var4, false, 1, false);
      }
   }

   private static void corner(float var0, float var1, int var2, int var3, int var4) {
      float var5 = 24.0F;
      float var6 = 3.0F;
      rect(var2 > 0 ? var0 : var0 - var5, var3 > 0 ? var1 : var1 - var6, var5, var6, var4);
      rect(var2 > 0 ? var0 : var0 - var6, var3 > 0 ? var1 : var1 - var5, var6, var5, var4);
   }

   private static void drawRadar() {
      if (panel == 0) {
         if (toggles[0][3] && T.connected && T.armed) {
            float var0 = 640.0F;
            float var1 = 350.0F;
            float var2 = 330.0F;
            float var3 = 145.0F;
            float var4 = 25.0F;

            for (int var5 = 0; var5 < 12; var5++) {
               float var6 = Rc2View.obstacleDirection(var5);
               if (var6 > 0.0F && var6 < var4) {
                  float var7 = clamp(var6 / var4, 0.0F, 1.0F);
                  int var8 = radarColor(var7);
                  float var9 = (float)(var5 * 30);
                  float var10 = var9 - 13.0F;
                  float var11 = 26.0F;
                  ellipseArc(var0, var1, var2 - 4.0F, var3 - 4.0F, var10, var11, 4.0F, alpha(var8, 0.45F));
                  ellipseArc(var0, var1, var2, var3, var10, var11, 5.0F, var8);
                  ellipseArc(var0, var1, var2 + 4.0F, var3 + 4.0F, var10, var11, 4.0F, alpha(var8, 0.25F));
                  double var12 = Math.toRadians((double)(var9 - 90.0F));
                  float var14 = var0 + (float)(Math.cos(var12) * (double)(var2 + 27.0F));
                  float var15 = var1 + (float)(Math.sin(var12) * (double)(var3 + 27.0F));
                  textC(String.format(Locale.ROOT, "%.1fm", var6), var14, var15, 13.5F, var8, true, 1, true);
               }
            }

            if (T.obstacleUp < 5.0F) {
               clearanceBar(560.0F, 178.0F, 160.0F, T.obstacleUp, 5.0F, true);
            }

            if (T.obstacleDown < 5.0F) {
               clearanceBar(560.0F, 604.0F, 160.0F, T.obstacleDown, 5.0F, false);
            }
         }
      }
   }

   private static int radarColor(float var0) {
      int[] var1 = new int[]{-50384, -27392, -13312, -6234112, -13318311, -16726082};
      float var2 = clamp(var0, 0.0F, 1.0F) * (float)(var1.length - 1);
      int var3 = (int)var2;
      return var3 >= var1.length - 1 ? var1[var1.length - 1] : lerpColor(var1[var3], var1[var3 + 1], var2 - (float)var3);
   }

   private static int lerpColor(int var0, int var1, float var2) {
      float var3 = clamp(var2, 0.0F, 1.0F);
      int var4 = (int)((float)(var0 >>> 24 & 0xFF) + (float)((var1 >>> 24 & 0xFF) - (var0 >>> 24 & 0xFF)) * var3);
      int var5 = (int)((float)(var0 >>> 16 & 0xFF) + (float)((var1 >>> 16 & 0xFF) - (var0 >>> 16 & 0xFF)) * var3);
      int var6 = (int)((float)(var0 >>> 8 & 0xFF) + (float)((var1 >>> 8 & 0xFF) - (var0 >>> 8 & 0xFF)) * var3);
      int var7 = (int)((float)(var0 & 0xFF) + (float)((var1 & 0xFF) - (var0 & 0xFF)) * var3);
      return var4 << 24 | var5 << 16 | var6 << 8 | var7;
   }

   private static void ellipseArc(float var0, float var1, float var2, float var3, float var4, float var5, float var6, int var7) {
      int var8 = Math.max(4, (int)(Math.abs(var5) / 6.0F));
      float var9 = 0.0F;
      float var10 = 0.0F;

      for (int var11 = 0; var11 <= var8; var11++) {
         double var12 = Math.toRadians((double)(var4 + var5 * (float)var11 / (float)var8) - 90.0);
         float var14 = var0 + (float)(Math.cos(var12) * (double)var2);
         float var15 = var1 + (float)(Math.sin(var12) * (double)var3);
         if (var11 > 0) {
            line(var9, var10, var14, var15, var6, var7);
         }

         var9 = var14;
         var10 = var15;
      }
   }

   private static void clearanceBar(float var0, float var1, float var2, float var3, float var4, boolean var5) {
      if (!Float.isNaN(var3) && !(var3 >= var4)) {
         float var6 = clamp(var3 / Math.max(1.0F, var4), 0.0F, 1.0F);
         int var7 = var6 < 0.25F ? -906460 : (var6 < 0.6F ? -23541 : -14884475);
         rect(var0, var1, var2, 3.0F, alpha(var7, 0.85F));
         rect(var0, var1 - 1.0F, var2, 1.0F, alpha(var7, 0.35F));
         String var8 = (var5 ? "上 " : "下 ") + String.format(Locale.ROOT, "%.1fm", var3);
         textC(var8, var0 + var2 / 2.0F, var5 ? var1 - 14.0F : var1 + 16.0F, 19.0F, var7, true, 1, true);
      }
   }

   private static Rc2FlightUi.Status stateChip() {
      if (Rc2View.isBraking()) {
         return new Rc2FlightUi.Status("飞行器刹车中", 1);
      } else if (T.accident) {
         return new Rc2FlightUi.Status("飞行器事故 · 图传断连", 2);
      } else if (!T.connected) {
         return new Rc2FlightUi.Status(T.paired ? "信号丢失" : "飞机未连接上遥控器", 2);
      } else if (T.rcBars == 0 && T.fourGBars == 0) {
         return new Rc2FlightUi.Status("信号丢失", 2);
      } else if (T.armed && T.battery >= 0 && T.battery <= T.forcedLandPct) {
         return new Rc2FlightUi.Status("电量极低请降落", 2);
      } else if (T.armed && T.battery >= 0 && T.battery <= T.autoReturnPct) {
         return new Rc2FlightUi.Status("电量低请返航", 2);
      } else if (T.returning) {
         return new Rc2FlightUi.Status("自动返航中 (" + Math.max(0, T.returnSec) + "s)", 0);
      } else if (T.state.startsWith("充电线")) {
         return new Rc2FlightUi.Status(T.state, 1);
      } else if (T.rcBars <= 1 && T.fourGBars == 0) {
         return new Rc2FlightUi.Status("信号弱，请调整天线", 1);
      } else if (T.satellites >= 0 && T.satellites < 11) {
         return new Rc2FlightUi.Status("卫星信号弱 · 请移动至开阔地带", 1);
      } else {
         return !T.armed && T.satellites >= 0 && T.satellites < 10
            ? new Rc2FlightUi.Status("谨慎起飞（无卫星定位）", 1)
            : new Rc2FlightUi.Status(T.armed ? "飞行中" : "可以起飞", 0);
      }
   }

   private static Rc2FlightUi.Status prompt() {
      if (VoiceAnnouncer.isAutoReturnActive()) {
         return new Rc2FlightUi.Status("低电量自动返航中 可按急停取消", 2);
      } else if (!T.connected && T.paired && !T.accident) {
         return new Rc2FlightUi.Status("信号丢失，等待飞机返回 RC / 4G 信号区", 2);
      } else if (T.connected && T.rcBars == 0 && T.fourGBars == 0) {
         return new Rc2FlightUi.Status("信号丢失，与飞机失去连接", 2);
      } else if (T.connected && T.altitude > 120.0) {
         return new Rc2FlightUi.Status("飞行高度超过 120m，存在违规风险，请确保您已获得当前空域授权", 2);
      } else if (T.armed && T.battery >= 0 && T.battery <= T.autoReturnPct) {
         return new Rc2FlightUi.Status("电量不足，飞行器将自动返航，可短按遥控器急停按键取消", 2);
      } else if (T.obstacleWarn) {
         return new Rc2FlightUi.Status("检测到障碍物，请注意飞行安全", 1);
      } else if (T.connected && T.rcBars == 2 && T.fourGBars == 0) {
         return new Rc2FlightUi.Status("飞机与遥控器之间可能存在遮挡，请将遥控器朝向飞机或提升飞机高度", 1);
      } else {
         return T.armed && T.avoidanceOff ? new Rc2FlightUi.Status("运动挡下避障功能已关闭，请注意飞行安全", 1) : null;
      }
   }

   private static void drawTopLeft() {
      if (!hidesTopBar()) {
         boolean var0 = hoverCircle(62.0F, 67.0F, 24.0F);
         if (var0) {
            disc(62.0F, 67.0F, 24.0F, 872415231);
         }

         iconS(0, 62.0F, 67.0F, 32.0F, -1);
         hitCircle(62.0F, 67.0F, 26.0F, () -> bridge.exit());
         String var1 = T.telemetry ? MODE_NAMES[clampI(T.mode, 0, 2)] : "N/A";
         float var2 = textS(var1, 112.0F, 52.0F, 30.0F, T.telemetry ? -1 : -7433578, true);
         if (hover(110.0F, 42.0F, var2 + 20.0F, 50.0F)) {
            rrect(110.0F, 44.0F, var2 + 20.0F, 46.0F, 6.0F, 654311423);
         }

         hit(110.0F, 42.0F, var2 + 20.0F, 50.0F, () -> open(1));
         Rc2FlightUi.Status var3 = stateChip();
         float var4 = 112.0F + var2 + 26.0F;
         float var5;
         if (var3.kind() == 0) {
            var5 = textS(var3.text(), var4, 52.0F, 30.0F, -1, true);
         } else {
            float var6 = 25.0F;
            float var7 = topRightStart - 20.0F - var4 - 40.0F;
            float var8 = g.textWidth(var3.text(), var6, false);
            if (var8 > var7 && var7 > 40.0F) {
               var6 = Math.max(16.0F, var6 * var7 / var8);
            }

            var5 = g.textWidth(var3.text(), var6, false) + 40.0F;
            rrect(var4, 45.0F, var5, 44.0F, 5.0F, var3.kind() == 2 ? -57826 : -23541);
            g.text(var3.text(), var4 + 20.0F, 67.0F - var6 / 2.0F, var6, -1, false, false);
         }

         Rc2FlightUi.Status var14 = prompt();
         if (var14 == null) {
            dismissedPrompt = null;
            promptShownText = null;
         } else {
            if (!var14.text().equals(promptShownText)) {
               promptShownText = var14.text();
               promptShownAt = now;
               dismissedPrompt = null;
            }

            if (!var14.text().equals(dismissedPrompt) && (VoiceAnnouncer.isAutoReturnActive() || now - promptShownAt < 1500000000L)) {
               List<String> var15 = wrap(var14.text(), 20.0F, 380.0F);
               float var16 = 0.0F;

               for (String var10 : var15) {
                  var16 = Math.max(var16, g.textWidth(var10, 20.0F, false));
               }

               float var17 = Math.max(var5, var16 + 46.0F);
               float var18 = (float)(24 + var15.size() * 30);
               float var11 = 100.0F;
               var4 = 110.0F;
               rrect(var4 + 3.0F, var11 + 4.0F, var17, var18, 8.0F, 1073741824);
               rrect(var4, var11, var17, var18, 8.0F, -434825440);
               rrectStroke(var4, var11, var17, var18, 8.0F, 1.0F, 872415231);
               rrect(var4 + 6.0F, var11 + 6.0F, 5.0F, var18 - 12.0F, 2.5F, var14.kind() == 2 ? -3931904 : -23541);

               for (int var12 = 0; var12 < var15.size(); var12++) {
                  g.text((String)var15.get(var12), var4 + 22.0F, var11 + 12.0F + (float)(var12 * 30), 20.0F, -1644826, false, false);
               }

               hit(110.0F, var11, var17, var18, () -> dismissedPrompt = var14.text());
            }
         }
      }
   }

   private static void drawTopRight() {
      if (!hidesTopBar()) {
         float var0 = 67.0F;
         String var1 = flightTime(T.remainingSec);
         int var2 = T.connected ? T.satellites : -1;
         String var3 = var2 < 0 ? "N/A" : String.valueOf(var2);
         float var4 = 24.0F;
         float var5 = g.textWidth(var1, 27.0F, true);
         float var6 = g.textWidth(var3, 24.0F, true);
         float var7 = 1240.0F;
         float var8 = 12.0F;
         float var9 = var4 * 2.0F + 10.0F + var5;
         float var10 = 70.0F;
         float var11 = 70.0F;
         float var12 = 60.0F;
         float var13 = 36.0F + var6;
         float var14 = var7 - 26.0F - var8 - var13 - var8 - var12 - var8 - var11 - var8 - var10 - var8 - var9;
         topRightStart = var14;
         int var15 = T.battery;
         float var16 = var14 + var4;
         disc(var16, var0, var4, -1290791912);
         ring(var16, var0, var4, 3.5F, -12894140);
         if (var15 >= 0) {
            var15 = clampI(var15, 0, 100);
            int var17 = batteryColor(var15);
            arc(var16, var0, var4, 3.5F, 0.0F, (float)var15 * 3.6F, var17);
            textC(String.valueOf(var15), var16, var0, var15 >= 100 ? 17.0F : 21.0F, var17, true, 1, false);
         } else {
            textC("N/A", var16, var0, 13.0F, -7433578, true, 1, false);
         }

         textS(var1, var14 + var4 * 2.0F + 10.0F, var0 - 14.0F, 27.0F, T.remainingSec >= 0 ? -1 : -7433578, true);
         if (hover(var14 - 6.0F, 38.0F, var9 + 12.0F, 58.0F)) {
            rrect(var14 - 6.0F, 38.0F, var9 + 12.0F, 58.0F, 8.0F, 536870911);
         }

         hit(var14 - 6.0F, 38.0F, var9 + 12.0F, 58.0F, () -> open(8));
         var14 += var9 + var8;
         int var28 = T.rcBars <= 0 ? 0 : T.rcBars + 1;
         int var18 = var28 >= 4 ? -1 : (var28 == 3 ? -1335487 : -906460);

         for (int var19 = 0; var19 < 5; var19++) {
            float var20 = 8.0F + (float)var19 * 4.0F;
            rrect(var14 + (float)var19 * 7.0F, var0 + 11.0F - var20, 4.6F, var20, 1.2F, var19 < var28 ? var18 : -10855844);
         }

         rrect(var14 + 39.0F, var0 - 11.0F, 31.0F, 22.0F, 3.0F, -431536311);
         textC("RC", var14 + 54.5F, var0, 16.0F, var28 >= 4 ? -1 : var18, true, 1, false);
         var14 += var10 + var8;
         int var29 = T.fourGBars > 0 ? T.fourGBars + 1 : 0;

         for (int var30 = 0; var30 < 5; var30++) {
            float var21 = 4.0F + (float)var30 * 2.5F;
            rrect(var14 + (float)var30 * 7.0F, var0 - 1.0F - var21, 5.2F, var21, 1.0F, var30 < var29 ? -1 : -10855844);
            rrect(var14 + (float)var30 * 7.0F, var0 + 1.5F, 5.2F, 9.5F, 1.0F, var30 < var29 ? -1 : -10855844);
         }

         rrect(var14 + 39.0F, var0 - 11.0F, 31.0F, 22.0F, 3.0F, -431536311);
         textC("4G", var14 + 54.5F, var0, 16.0F, var29 > 0 ? -1 : -4342339, true, 1, false);
         if (hover(var14 - 6.0F, 42.0F, var11 + 12.0F, 50.0F)) {
            rrect(var14 - 6.0F, 42.0F, var11 + 12.0F, 50.0F, 6.0F, 536870911);
         }

         float var31 = var14 + var11 / 2.0F;
         hit(var14 - 6.0F, 42.0F, var11 + 12.0F, 50.0F, () -> {
            fourGAnchor = var31;
            open(9);
         });
         var14 += var11 + var8;
         int var32 = T.avoidanceOff ? -906460 : -1;
         iconS(15, var14 + 6.0F, var0, 28.0F, var32);
         iconS(16, var14 + 32.0F, var0, 28.0F, var32);
         hit(var14 - 4.0F, 42.0F, var12 + 8.0F, 50.0F, () -> {
            settingsTab = 0;
            open(4);
         });
         var14 += var12 + var8;
         int var22 = var2 < 0 ? -7433578 : (var2 >= 20 ? -1 : (var2 >= 10 ? -90355 : -906460));
         iconS(var2 >= 10 ? 18 : 19, var14 + 16.0F, var0, 32.0F, var22);
         textS(var3, var14 + 32.0F + 4.0F, var0 - 12.0F, 24.0F, var22, true);
         if (hoverCircle(var7, var0, 24.0F)) {
            disc(var7, var0, 24.0F, 654311423);
         }

         iconS(27, var7, var0, 38.0F, -1);
         hitCircle(var7, var0, 26.0F, () -> open(4));
      }
   }

   private static boolean hidesTopBar() {
      return panel == 3 || panel == 4 || panel == 5;
   }

   private static int batteryColor(int var0) {
      return var0 <= T.forcedLandPct ? -906460 : (var0 <= T.autoReturnPct ? -548325 : -14884475);
   }

   private static void drawLeftRail() {
      railButton(2, 386.0F, waypointMode, () -> {
         waypointMode = !waypointMode;
         waypointCollapsed = false;
         toast(waypointMode ? "已开启航点飞行" : "已退出航点飞行");
      });
      railButton(3, 300.0F, trackingMode > 0, () -> {
         if (trackingMode > 0) {
            trackingMode = 0;
            toast("已退出焦点跟随");
         } else {
            open(6);
         }
      });
      int var0 = T.armed ? 6 : 4;
      railButton(var0, 214.0F, panel == 7, () -> {
         if (!T.connected) {
            toast("未连接飞行器");
         } else {
            open(7);
         }
      });
   }

   private static void railButton(int var0, float var1, boolean var2, Runnable var3) {
      boolean var4 = hoverCircle(63.0F, var1, 30.0F);
      if (var2) {
         disc(63.0F, var1, 30.0F, 1493172224);
      }

      if (var4) {
         disc(63.0F, var1, 30.0F, 654311423);
      }

      iconS(var0, 63.0F, var1, 42.0F, -1);
      hitCircle(63.0F, var1, 30.0F, var3);
   }

   private static void drawRightCluster() {
      boolean var0 = hoverCircle(1192.0F, 250.0F, 26.0F);
      if (var0) {
         disc(1192.0F, 250.0F, 26.0F, 654311423);
      }

      iconS(cameraTab == 0 ? 8 : 7, 1192.0F, 250.0F, 44.0F, -1);
      hitCircle(1192.0F, 250.0F, 26.0F, () -> open(2));
      boolean var1 = hoverCircle(1088.0F, 292.0F, 34.0F);
      disc(1088.0F, 292.0F, 34.0F, var1 ? -265934290 : -435680499);
      String var2 = zoomLabel();
      float var3 = g.textWidth(var2, 26.0F, true);
      float var4 = g.textWidth("×", 24.0F, true);
      float var5 = 1088.0F - (var3 + var4) / 2.0F;
      g.text(var2, var5, 279.0F, 26.0F, -1, true, false);
      g.text("×", var5 + var3, 280.0F, 24.0F, -16895, true, false);
      hitCircle(1088.0F, 292.0F, 34.0F, () -> {
         zoomIndex = (zoomIndex + 1) % 3;
         bridge.setZoom(new float[]{1.0F, 2.0F, 4.0F}[zoomIndex]);
         toast("变焦 " + new String[]{"1", "2", "4"}[zoomIndex] + "×");
      });
      boolean var6 = hoverCircle(1088.0F, 384.0F, 34.0F);
      disc(1088.0F, 384.0F, 34.0F, var6 ? -265934290 : -435680499);
      textC(focusMode == 0 ? "AF" : "MF", 1088.0F, 384.0F, 26.0F, -1, true, 1, false);
      hitCircle(1088.0F, 384.0F, 34.0F, () -> toast((focusMode = 1 - focusMode) == 0 ? "自动对焦 AF" : "手动对焦 MF"));
      float var7 = 1192.0F;
      float var8 = 337.0F;
      boolean var9 = hoverCircle(var7, var8, 40.0F);
      if (cameraTab == 0) {
         boolean var10 = flashUntil > now;
         ring(var7, var8, 38.0F, 4.0F, var10 ? -1 : -860309320);
         disc(var7, var8, 31.0F, var9 ? -986896 : -1);
      } else if (recording) {
         ring(var7, var8, 38.0F, 4.0F, -860309320);
         disc(var7, var8, 31.0F, -11184809);
         rrect(var7 - 13.0F, var8 - 13.0F, 26.0F, 26.0F, 4.0F, -1827031);
      } else {
         ring(var7, var8, 38.0F, 4.0F, 1303951544);
         disc(var7, var8, 33.0F, var9 ? -1034946 : -1827031);
      }

      hitCircle(var7, var8, 40.0F, Rc2FlightUi::shutter);
      if (recording) {
         long var14 = (now - recordStart) / 1000000000L;
         String var12 = clock(var14);
         float var13 = g.textWidth(var12, 18.0F, true) + 18.0F;
         disc(1192.0F - var13 / 2.0F + 4.0F, 199.0F, 4.5F, -1827031);
         textS(var12, 1192.0F - var13 / 2.0F + 14.0F, 190.0F, 18.0F, -1, true);
      }

      if (hoverCircle(1192.0F, 423.0F, 26.0F)) {
         disc(1192.0F, 423.0F, 26.0F, 654311423);
      }

      iconS(9, 1192.0F, 423.0F, 42.0F, -1);
      hitCircle(1192.0F, 423.0F, 26.0F, () -> open(3));
   }

   private static void shutter() {
      if (cameraTab == 0) {
         flashUntil = now + 260000000L;
         photoCount++;
         gallery.add(0, now);
         toast("已拍摄 · " + PHOTO_MODES[photoMode], 1200L);
      } else {
         recording = !recording;
         recordStart = recording ? now : 0L;
         if (!recording) {
            gallery.add(0, now);
         }

         toast(recording ? "开始录像 · " + VIDEO_MODES[videoMode] : "录像已保存", 1500L);
      }
   }

   private static void drawBottomLeft() {
      if (panel != 4) {
         drawThumb();
         float var0 = 310.0F;
         if (assistFloating && assistX < 430.0F && assistY + assistH() > 550.0F) {
            var0 = Math.max(var0, assistX + assistW() + 26.0F);
         }

         float var1 = 573.0F;
         float var2 = 611.0F;
         int var3 = T.connected ? -1 : -7433578;
         int var4 = Rc2View.isCruiseActive() ? -23541 : var3;
         int var5 = altitudeColor(var3);
         String var6 = T.connected ? meters(T.altitude) : "N/A";
         String var7 = T.connected ? meters(T.homeDistance) : "N/A";
         float var8 = var0 + 28.0F + 14.0F;
         float var9 = var8 + Math.max(g.textWidth(var6, 34.0F, true), g.textWidth(var7, 34.0F, true)) + 18.0F;
         iconS(13, var0 + 14.0F, var1, 28.0F, var5);
         textS(var6, var8, var1 - 18.0F, 34.0F, var5, true);
         textS(T.connected ? signedSpeed(T.vSpeed) : "N/A", var9, var1 - 10.0F, 21.0F, var3, true);
         iconS(14, var0 + 14.0F, var2, 28.0F, -1);
         textS(var7, var8, var2 - 18.0F, 34.0F, var3, true);
         textS(T.connected ? signedSpeed(T.hSpeed) : "N/A", var9, var2 - 10.0F, 21.0F, var4, true);
      }
   }

   private static int[] thumbTypes() {
      return assistFloating ? new int[]{0, 1} : new int[]{0, 1, 2};
   }

   private static float thumbWidthFor(int var0) {
      return var0 == 1 ? 164.0F : 292.0F;
   }

   private static int thumbType() {
      return assistFloating ? clampI(assistSlotType, 0, 1) : mapType;
   }

   private static void setThumbType(int var0) {
      if (assistFloating) {
         assistSlotType = var0;
      } else {
         mapType = var0;
      }
   }

   private static int thumbNeighbour(int var0) {
      int[] var1 = thumbTypes();
      int var2 = 0;

      for (int var3 = 0; var3 < var1.length; var3++) {
         if (var1[var3] == thumbType()) {
            var2 = var3;
         }
      }

      return var1[Math.floorMod(var2 + var0, var1.length)];
   }

   private static float thumbDragOffset() {
      if (thumbPressed && !assistPicked) {
         float var0 = (float)mouseX - thumbStartX;
         float var1 = thumbWidthFor(thumbType());
         return Math.abs(var0) < 6.0F ? 0.0F : clamp(var0, -var1, var1);
      } else {
         return 0.0F;
      }
   }

   private static void drawThumb() {
      float var0 = 30.0F;
      float var1 = 468.0F;
      int var2 = thumbType();
      float var3 = thumbWidthFor(var2);
      float var4 = 164.0F;
      if (!thumbPressed) {
         thumbAnim *= 0.72F;
      }

      if (Math.abs(thumbAnim) < 0.5F) {
         thumbAnim = 0.0F;
      }

      float var5 = thumbPressed ? thumbDragOffset() : thumbAnim;
      rrect(var0 - 2.0F, var1 - 2.0F, var3 + 4.0F, var4 + 4.0F, 6.0F, 1442840575);
      g.pushClip(var0, var1, var3, var4);
      thumbDrawing = true;

      try {
         thumbContent(var2, var0 + var5, var1, var3, var4);
         if (var5 > 0.5F) {
            int var6 = thumbNeighbour(-1);
            float var7 = thumbWidthFor(var6);
            thumbContent(var6, var0 + var5 - var7, var1, var7, var4);
         } else if (var5 < -0.5F) {
            int var12 = thumbNeighbour(1);
            float var14 = thumbWidthFor(var12);
            thumbContent(var12, var0 + var5 + var14, var1, var14, var4);
         }
      } finally {
         thumbDrawing = false;
         g.popClip();
      }

      int[] var13 = thumbTypes();
      float var15 = (float)var13.length * 12.0F + 8.0F;
      rrect(var0 + var3 / 2.0F - var15 / 2.0F, var1 + var4 - 16.0F, var15, 12.0F, 6.0F, 1929379840);

      for (int var8 = 0; var8 < var13.length; var8++) {
         boolean var9 = var13[var8] == var2;
         disc(var0 + var3 / 2.0F - var15 / 2.0F + 10.0F + (float)var8 * 12.0F, var1 + var4 - 10.0F, var9 ? 3.2F : 2.4F, var9 ? -1 : -1711276033);
      }

      if (hover(var0, var1, var3, var4) && !thumbPressed) {
         rrectStroke(var0 - 1.0F, var1 - 1.0F, var3 + 2.0F, var4 + 2.0F, 5.0F, 1.5F, -1);
      }

      Rc2FlightUi.Hit var16 = hit(var0, var1, var3, var4, null);
      var16.press = () -> {
         thumbPressed = true;
         thumbStartX = (float)mouseX;
         thumbStartY = (float)mouseY;
         thumbAnim = 0.0F;
         if (thumbType() == 2) {
            beginAssistPickup();
         }
      };
   }

   private static void thumbContent(int var0, float var1, float var2, float var3, float var4) {
      if (var0 == 0) {
         drawMap(var1, var2, var3, var4, 0.9, false);
         rrect(var1 + 6.0F, var2 + 6.0F, 22.0F, 22.0F, 3.0F, -218827273);
         g.icon(40, var1 + 10.0F, var2 + 9.0F, 14.0F, 14.0F, -10855328);
      } else if (var0 == 1) {
         thumbAttitude(var1, var2, var3, var4);
      } else {
         drawAssist(var1, var2, var3, var4, false);
      }
   }

   private static void thumbAttitude(float var0, float var1, float var2, float var3) {
      float var4 = Math.min(var2, var3);
      drawAttitude(var0 + (var2 - var4) / 2.0F, var1 + (var3 - var4) / 2.0F, var4, var4);
   }

   private static boolean releaseThumb(double var0, double var2) {
      if (!thumbPressed) {
         return false;
      } else {
         thumbPressed = false;
         if (assistPicked) {
            return false;
         } else {
            float var4 = (float)var0 - thumbStartX;
            float var5 = (float)var2 - thumbStartY;
            if (Math.abs(var4) >= 48.0F && Math.abs(var4) > Math.abs(var5)) {
               int var8 = var4 < 0.0F ? 1 : -1;
               setThumbType(thumbNeighbour(var8));
               float var7 = thumbWidthFor(thumbType());
               thumbAnim = var4 < 0.0F ? var4 + var7 : var4 - var7;
            } else if (Math.abs(var4) < 12.0F && Math.abs(var5) < 12.0F) {
               mapTab = thumbType();
               open(5);
            } else {
               float var6 = thumbWidthFor(thumbType());
               thumbAnim = clamp(var4, -var6, var6);
            }

            assistPickup = false;
            return true;
         }
      }
   }

   private static int altitudeColor(int var0) {
      if (!T.connected || Double.isNaN(T.altitude)) {
         return var0;
      } else if (T.altitude > 500.0) {
         return blinkColor(-906460);
      } else {
         return T.altitude > 120.0 ? blinkColor(-23541) : var0;
      }
   }

   private static int blinkColor(int var0) {
      return now / 250000000L % 2L == 0L ? var0 : alpha(var0, 0.28F);
   }

   private static void assistArrow(float var0, float var1, float var2, int var3, int var4) {
      boolean var5 = Rc2View.assistDir() == var4;
      boolean var6 = hover(var0, var1, var2, var2);
      int var7 = var5 ? -16725933 : (var6 ? 1728053247 : 654311423);
      rrect(var0, var1, var2, var2, var2 * 0.24F, var7);
      g.icon(var3, var0 + var2 * 0.18F, var1 + var2 * 0.18F, var2 * 0.64F, var2 * 0.64F, var5 ? -1 : -855638017);
      hit(var0, var1, var2, var2, () -> {
         Rc2View.selectAssistDir(var4);
         assistHoldDir = var4;
         assistHoldStart = now;
      });
   }

   private static void updateAssistHold() {
      if (assistHoldDir >= 0) {
         if (!mouseDown) {
            assistHoldDir = -1;
         } else if (now - assistHoldStart >= 450000000L) {
            Rc2View.lockAssistDir(assistHoldDir);
            assistHoldDir = -1;
         }
      }
   }

   public static float maxFlightAltitude() {
      return sliders[1][1];
   }

   public static float softwareVoiceVolume() {
      return clamp(sliders[4][0] / 100.0F, 0.0F, 1.0F);
   }

   public static int avoidanceMode() {
      return clampI(segments[0][2], 0, 2);
   }

   public static boolean assistVisible() {
      if (panel == 4) {
         return false;
      } else {
         return panel == 5 ? mapTab == 2 : assistFloating || mapExpanded && mapType == 2 || thumbPressed || thumbAnim != 0.0F;
      }
   }

   public static boolean coversMainFeed() {
      return panel == 5 && mapTab != 2;
   }

   private static float assistW() {
      return assistLarge ? 360.0F : 304.0F;
   }

   private static float assistH() {
      return assistLarge ? 211.0F : 178.0F;
   }

   private static void drawMapLauncher(int var0) {
      boolean var1 = hover(43.0F, 553.0F, 67.0F, 67.0F);
      g.icon(var0 == 2 ? 12 : 10, 43.0F, 553.0F, 67.0F, 67.0F, -1);
      if (var1) {
         rrect(43.0F, 553.0F, 67.0F, 67.0F, 8.0F, 654311423);
      }

      hit(43.0F, 553.0F, 67.0F, 67.0F, () -> mapExpanded = true);
   }

   private static void drawAssistWindow(float var0, float var1, float var2, float var3, boolean var4) {
      Rc2FlightUi.Hit var5 = hit(var0, var1, var2, var3, null);
      var5.press = Rc2FlightUi::beginAssistPickup;
      rrect(var0 - 3.0F, var1 - 3.0F, var2 + 6.0F, var3 + 6.0F, 7.0F, 872415231);
      rrectStroke(var0 - 1.0F, var1 - 1.0F, var2 + 2.0F, var3 + 2.0F, 3.0F, 1.0F, assistPicked ? -16728321 : 1728053247);
      drawAssist(var0, var1, var2, var3, false);
      if (var4) {
         cornerButton(var0 + var2 - 28.0F, var1 + 6.0F, 22.0F, 1, false, Rc2FlightUi::dockAssist);
      } else {
         cornerButton(var0 + var2 - 28.0F, var1 + 6.0F, 22.0F, assistLarge ? 38 : 39, false, () -> {
            assistLarge = !assistLarge;
            assistPickup = false;
            assistPicked = false;
            assistX = clamp(assistX, 8.0F, 1280.0F - assistW() - 8.0F);
            assistY = clamp(assistY, 8.0F, 680.0F - assistH() - 8.0F);
         });
         cornerButton(var0 + var2 - 54.0F, var1 + 6.0F, 22.0F, mapType == 0 ? 10 : (mapType == 1 ? 11 : 12), false, () -> mapType = (mapType + 1) % 3);
         cornerButton(var0 + 4.0F, var1 + var3 - 26.0F, 22.0F, 38, true, () -> {
            mapExpanded = false;
            assistPickup = false;
            assistPicked = false;
         });
      }
   }

   private static void dockAssist() {
      assistFloating = false;
      assistLarge = false;
      mapType = 2;
      mapExpanded = true;
      assistPickup = false;
      assistPicked = false;
   }

   private static void drawFloatingAssist() {
      if (panel == 0 && mapType == 2 && assistFloating) {
         float var0 = assistW();
         float var1 = assistH();
         assistX = clamp(assistX, 8.0F, 1280.0F - var0 - 8.0F);
         assistY = clamp(assistY, 8.0F, 680.0F - var1 - 8.0F);
         drawAssistWindow(assistX, assistY, var0, var1, true);
      }
   }

   private static void beginAssistPickup() {
      assistPickup = true;
      assistPicked = false;
      assistPickupAt = now;
      assistPressMouseX = (float)mouseX;
      assistPressMouseY = (float)mouseY;
      if (!assistFloating) {
         assistX = 30.0F;
         assistY = 640.0F - assistH();
      }

      assistPressBaseX = assistX;
      assistPressBaseY = assistY;
   }

   private static void updateAssistPickup() {
      if (assistPickup) {
         if (!mouseDown) {
            assistPickup = false;
            assistPicked = false;
         } else if (!assistPicked && thumbPressed && Math.abs((float)mouseX - thumbStartX) > 10.0F) {
            assistPickup = false;
         } else {
            if (!assistPicked && now - assistPickupAt >= 320000000L) {
               assistPicked = true;
               assistFloating = true;
               assistPressMouseX = (float)mouseX;
               assistPressMouseY = (float)mouseY;
               if (!assistLarge) {
                  float var0 = assistH();
                  assistLarge = true;
                  assistPressBaseY = assistY = assistY - (assistH() - var0);
               }
            }

            if (assistPicked) {
               float var2 = assistW();
               float var1 = assistH();
               assistX = clamp(assistPressBaseX + (float)mouseX - assistPressMouseX, 8.0F, 1280.0F - var2 - 8.0F);
               assistY = clamp(assistPressBaseY + (float)mouseY - assistPressMouseY, 8.0F, 680.0F - var1 - 8.0F);
            }
         }
      }
   }

   private static void drawMapWidget(float var0, float var1, float var2, float var3, int var4) {
      if (var4 != 1) {
         hit(var0, var1, var2, var3, () -> {
            mapTab = var4;
            open(5);
         });
      }

      if (var4 == 0) {
         rrect(var0 - 2.0F, var1 - 2.0F, var2 + 4.0F, var3 + 4.0F, 5.0F, -1775894);
         drawMap(var0, var1, var2, var3, 1.2, false);
         rrect(var0 + 6.0F, var1 + 6.0F, 26.0F, 26.0F, 3.0F, -218827273);
         g.icon(40, var0 + 11.0F, var1 + 9.0F, 16.0F, 16.0F, -10855328);
         g.text("N", var0 + 9.0F, var1 + 6.0F, 9.0F, -10855328, true, false);
         rect(var0 + 14.0F, var1 + var3 - 28.0F, 56.0F, 2.0F, -13947600);
         rect(var0 + 14.0F, var1 + var3 - 34.0F, 2.0F, 8.0F, -13947600);
         rect(var0 + 68.0F, var1 + var3 - 34.0F, 2.0F, 8.0F, -13947600);
         g.text("50m", var0 + 18.0F, var1 + var3 - 52.0F, 16.0F, -13947600, false, false);
         disc(var0 + var2 - 19.0F, var1 + var3 - 19.0F, 13.0F, -218103809);
         ring(var0 + var2 - 19.0F, var1 + var3 - 19.0F, 13.0F, 1.5F, -7697264);
         g.icon(40, var0 + var2 - 27.0F, var1 + var3 - 27.0F, 16.0F, 16.0F, -10855328);
         g.icon(38, var0 + 4.0F, var1 + var3 - 26.0F, 22.0F, 22.0F, -7697264);
      } else {
         drawAttitude(var0, var1, var2, var3);
      }

      if (var4 != 1) {
         cornerButton(var0 + 4.0F, var1 + var3 - 26.0F, 22.0F, 38, true, () -> mapExpanded = false);
      }

      int var5 = var4 == 0 ? 10 : 11;
      cornerButton(var0 + var2 - 26.0F, var1 + 4.0F, 20.0F, var5, false, () -> {
         if (assistFloating) {
            assistSlotType = 1 - var4;
         } else {
            mapType = (mapType + 1) % 3;
         }
      });
   }

   private static void cornerButton(float var0, float var1, float var2, int var3, boolean var4, Runnable var5) {
      boolean var6 = hover(var0 - 2.0F, var1 - 2.0F, var2 + 4.0F, var2 + 4.0F);
      rrect(var0 - 2.0F, var1 - 2.0F, var2 + 4.0F, var2 + 4.0F, 4.0F, var4 ? -1291845632 : (var6 ? 1509949439 : 855638016));
      g.icon(var3, var0 + var2 * 0.16F, var1 + var2 * 0.16F, var2 * 0.68F, var2 * 0.68F, -1);
      hit(var0 - 2.0F, var1 - 2.0F, var2 + 4.0F, var2 + 4.0F, var5);
   }

   private static void collapseButton(float var0, float var1) {
      rrect(var0, var1, 26.0F, 26.0F, 3.0F, -218827273);
      rect(var0 + 7.0F, var1 + 12.0F, 12.0F, 2.0F, -10855328);
   }

   private static void drawAttitude(float var0, float var1, float var2, float var3) {
      rrect(var0, var1, var2, var3, 8.0F, -432523207);
      float var4 = var0 + var2 / 2.0F;
      float var5 = var1 + var3 / 2.0F + 4.0F;
      float var6 = Math.min(var2, var3) / 2.0F - 14.0F;
      float var7 = var6 / 76.0F;
      if (!T.telemetry) {
         disc(var4, var5, var6, -11908532);
         textC("N/A", var4, var5, 24.0F * var7, -7433578, true, 1, false);
      } else {
         double var8 = Math.toRadians(T.yaw);
         double var10 = -Math.sin(var8);
         double var12 = Math.cos(var8);
         double var14 = -Math.cos(var8);
         double var16 = -Math.sin(var8);
         double var18 = (double)clamp((float)((T.velX * var10 + T.velZ * var12) * -2.4), -30.0F, 30.0F);
         double var20 = (double)clamp((float)((T.velX * var14 + T.velZ * var16) * 2.4), -30.0F, 30.0F);
         horizonDisc(var4, var5, var6, var18 + (double)Rc2View.brakePitchOffset(), var20, -10461086, -12221554);

         for (int var22 = 1; var22 <= 2; var22++) {
            dashedRing(var4, var5, var6 * (float)var22 / 3.0F, 1442840575);
         }

         String[] var28 = new String[]{"N", "E", "S", "W"};

         for (int var23 = 0; var23 < 4; var23++) {
            double var24 = Math.toRadians((double)(var23 * 90) - (T.yaw + 180.0));
            float var26 = (float)((double)var4 + Math.sin(var24) * (double)(var6 - 11.0F * var7));
            float var27 = (float)((double)var5 - Math.cos(var24) * (double)(var6 - 11.0F * var7));
            textC(var28[var23], var26, var27, 10.5F * var7, -1, false, 1, false);
         }

         float[] var29 = relative(T.homeX - T.droneX, T.homeZ - T.droneZ, var6 - 18.0F * var7, (double)(4.0F / var7));
         g.icon(48, var4 + var29[0] - 10.0F * var7, var5 + var29[1] - 10.0F * var7, 20.0F * var7, 20.0F * var7, -1);
         if (T.pilotKnown) {
            float[] var30 = relative(T.pilotX - T.droneX, T.pilotZ - T.droneZ, var6 - 18.0F * var7, (double)(4.0F / var7));
            disc(var4 + var30[0] - 12.0F * var7, var5 + var30[1] - 8.0F * var7, 7.0F * var7, -1);
            disc(var4 + var30[0] - 12.0F * var7, var5 + var30[1] - 8.0F * var7, 5.0F * var7, -12808961);
         }

         g.icon(40, var4 - 13.0F * var7, var5 - 15.0F * var7, 26.0F * var7, 26.0F * var7, -12805121);
      }

      g.icon(49, var0 + 8.0F, var1 + 8.0F, 18.0F * var7, 18.0F * var7, -1);
      g.icon(38, var0 + 4.0F, var1 + var3 - 26.0F, 22.0F, 22.0F, -1);
      g.icon(37, var0 + var2 - 26.0F, var1 + var3 - 26.0F, 20.0F, 20.0F, -1);
      g.icon(41, var0 + var2 - 24.0F, var1 + 6.0F, 16.0F, 16.0F, -855638017);
   }

   private static float[] relative(double var0, double var2, float var4, double var5) {
      double var7 = Math.toRadians(T.yaw);
      double var9 = var0 * -Math.sin(var7) + var2 * Math.cos(var7);
      double var11 = var0 * -Math.cos(var7) + var2 * -Math.sin(var7);
      double var13 = var11 / var5;
      double var15;
      double var17 = Math.hypot(var13, var15 = -var9 / var5);
      if (var17 > (double)var4) {
         var13 *= (double)var4 / var17;
         var15 *= (double)var4 / var17;
      }

      return new float[]{(float)var13, (float)var15};
   }

   private static void drawAssist(float var0, float var1, float var2, float var3, boolean var4) {
      g.pushClip(var0, var1, var2, var3);
      rect(var0, var1, var2, var3, -15724528);
      boolean var5 = T.telemetry && Rc2View.isAssistReady() && g.assist(var0, var1, var2, var3);
      if (!var5) {
         rect(var0, var1, var2, var3 * 0.55F, -7434610);
         rect(var0, var1 + var3 * 0.55F, var2, var3 * 0.45F, -10724260);
         textC("辅助影像准备中", var0 + var2 / 2.0F, var1 + var3 / 2.0F, 20.0F * (var4 ? 1.6F : 0.8F), -855638017, false, 1, false);
      }

      g.popClip();
      float var6 = var0 + var2 / 2.0F;
      float var7 = var1 + var3 / 2.0F;
      float var8 = var4 ? 2.0F : 1.0F;
      if (T.telemetry && !thumbDrawing) {
         float[] var9 = Rc2View.assistVector();
         float var10 = (float)Math.hypot((double)var9[0], (double)var9[1]);
         float var11 = Math.min(Math.abs(var9[2]) / 18.0F, 1.0F) * (var4 ? 150.0F : 54.0F);
         if (var10 > 1.0E-4F && var11 > 1.0F) {
            line(var6, var7, var6 + var9[0] / var10 * var11, var7 + var9[1] / var10 * var11, var4 ? 5.0F : 3.0F, -15846);
         }

         disc(var6, var7, var4 ? 7.0F : 4.0F, -15846);
         float var12 = var4 ? 54.0F : 24.0F;
         float var13 = var4 ? 20.0F : 6.0F;
         float var14 = var4 ? 74.0F : 6.0F;
         float var15 = var4 ? var0 + 86.0F : var0 + var13;
         assistArrow(var15, var1 + var14, var12, 4, 4);
         assistArrow(var4 ? var0 + var2 - var13 - var12 : var0 + var2 - var13 - var12 - 28.0F, var1 + var14, var12, 5, 5);
         assistArrow(var6 - var12 / 2.0F, var1 + var14, var12, 26, 0);
         assistArrow(var6 - var12 / 2.0F, var1 + var3 - var13 - var12, var12, 47, 1);
         assistArrow(var0 + var13, var1 + var3 / 2.0F - var12 / 2.0F, var12, 45, 2);
         assistArrow(var0 + var2 - var13 - var12, var1 + var3 / 2.0F - var12 / 2.0F, var12, 46, 3);
      }

      float var16 = (var4 ? var0 + 86.0F : var0 + 6.0F) + (var4 ? 62.0F : 30.0F);
      float var17 = var1 + (var4 ? 74.0F : 6.0F);
      float var18 = var4 ? 150.0F : 34.0F;
      float var19 = var4 ? 50.0F : 24.0F;
      rrect(var16, var17, var18, var19, 6.0F, -218827273);
      textC(Rc2View.assistDirName(), var16 + var19 * 0.62F, var17 + var19 / 2.0F, var4 ? 26.0F : 16.0F, -1, true, 1, false);
      if (var4) {
         textC(
            Rc2View.isAssistLocked() ? "锁定视角" : "辅助视角",
            var16 + 92.0F,
            var17 + var19 / 2.0F,
            20.0F,
            Rc2View.isAssistLocked() ? -14884475 : -6513506,
            false,
            1,
            false
         );
         float var20 = var0 + 20.0F;
         float var21 = var1 + var3 - 66.0F;
         rrect(var20, var21, 196.0F, 44.0F, 6.0F, Rc2View.isAssistAuto() ? -11582106 : -218827273);
         textC("自动跟随飞行方向", var20 + 98.0F, var21 + 22.0F, 19.0F, Rc2View.isAssistAuto() ? -1 : -6513506, false, 1, false);
         hit(var20, var21, 196.0F, 44.0F, () -> Rc2View.toggleAssistAuto());
      }
   }

   private static void drawBottomRight() {
      if (panel != 4) {
         float var0 = 568.5F;
         float var1 = 602.5F;
         boolean var2 = T.connected;
         int var3 = var2 ? -1 : -7433578;
         if (autoExposure) {
            float var4 = 559.0F;
            float var5 = 597.0F;
            String var6 = !var2 ? "N/A" : (cameraTab == 0 ? String.valueOf(Math.max(0, 2310 - photoCount)) : storageClock());
            String var7 = !var2 ? "N/A" : (cameraTab == 0 ? "4:3" : RES[resIndex] + " " + FPS[fpsIndex]);
            String var8 = !var2 ? "N/A" : evText();
            float var9 = 1166.0F - Math.max(g.textWidth("EV", 28.0F, true), g.textWidth(var8, 28.0F, true));
            float var10 = var9 - 34.0F - Math.max(g.textWidth("RES&FPS", 28.0F, true), g.textWidth(var7, 28.0F, true));
            float var11 = var10 - 34.0F - Math.max(g.textWidth("存储", 28.0F, true), g.textWidth(var6, 28.0F, true));
            textS("存储", var11, var4, 28.0F, -1, true);
            textS(var6, var11, var5, 28.0F, var3, true);
            textS(cameraTab == 0 ? "RATIO" : "RES&FPS", var10, var4, 28.0F, -1, true);
            textS(var7, var10, var5, 28.0F, var3, true);
            textS("EV", var9, var4, 28.0F, -1, true);
            textS(var8, var9, var5, 28.0F, var3, true);
         } else {
            drawProParams(var3);
         }

         boolean var12 = hover(1172.0F, 572.0F, 52.0F, 48.0F);
         if (var12) {
            rrect(1170.0F, 570.0F, 56.0F, 52.0F, 6.0F, 654311423);
         }

         iconS(autoExposure ? 20 : 21, 1204.0F, 592.0F, 50.0F, -1);
         hit(1172.0F, 572.0F, 52.0F, 48.0F, () -> {
            autoExposure = !autoExposure;
            toast(autoExposure ? "自动曝光 (AUTO)" : "手动曝光 (PRO)");
         });
         float var13 = autoExposure ? 720.0F : 692.0F;
         if (hover(var13, 556.0F, 1165.0F - var13, 78.0F)) {
            rrect(var13 - 4.0F, 556.0F, 1170.0F - var13, 78.0F, 8.0F, 352321535);
         }

         hit(var13, 556.0F, 1165.0F - var13, 78.0F, () -> open(11));
      }
   }

   private static void drawProParams(int var0) {
      boolean var1 = T.connected;
      float var2 = 568.5F;
      float var3 = 602.5F;
      textS(!var1 ? "N/A" : (cameraTab == 0 ? "4:3" : RES[resIndex] + " " + FPS[fpsIndex]), 700.0F, var2, 26.0F, var0, true);
      textS(colorMode == 0 ? "Normal" : (colorMode == 1 ? "HLG" : "D-Log"), 700.0F, var3, 26.0F, -1, true);
      float var4 = 820.0F;
      g.icon(24, var4 - 4.0F, var2 + 1.0F, 22.0F, 22.0F, -1);
      textS(!var1 ? "N/A" : (cameraTab == 0 ? String.valueOf(Math.max(0, 2310 - photoCount)) : storageShort()), var4 + 18.0F, var2, 26.0F, var0, true);
      float var5 = tag("WB", var4, var3 + 3.0F);
      float var6 = textS(wbIndex == 0 ? "5200K" : WB[wbIndex], var4 + var5 + 3.0F, var3, 26.0F, -1, true);
      if (wbIndex == 0) {
         textS("A", var4 + var5 + 3.0F + var6 + 1.0F, var3 - 4.0F, 12.0F, -1, true);
      }

      var4 = 958.0F;
      var5 = tag("S", var4, var2 + 3.0F);
      var6 = textS(SHUTTER[shutterIndex], var4 + var5 + 3.0F, var2, 26.0F, -1, true);
      textS("A", var4 + var5 + 3.0F + var6 + 1.0F, var2 - 4.0F, 12.0F, -1, true);
      var5 = tag("ISO", var4, var3 + 3.0F);
      var6 = textS(ISO[isoIndex], var4 + var5 + 3.0F, var3, 26.0F, -1, true);
      textS("A", var4 + var5 + 3.0F + var6 + 1.0F, var3 - 4.0F, 12.0F, -1, true);
      var4 = 1092.0F;
      var5 = tag("F", var4, var2 + 3.0F);
      textS("1.7", var4 + var5 + 3.0F, var2, 26.0F, -1, true);
      var5 = tag("EV", var4, var3 + 3.0F);
      textS(String.format(Locale.ROOT, "%.1f", ev), var4 + var5 + 3.0F, var3, 26.0F, -1, true);
   }

   private static float tag(String var0, float var1, float var2) {
      float var3 = Math.max(19.0F, g.textWidth(var0, 13.0F, true) + 6.0F);
      rrect(var1, var2, var3, 19.0F, 2.0F, -1);
      textC(var0, var1 + var3 / 2.0F, var2 + 9.5F, 13.0F, -15658735, true, 1, false);
      return var3;
   }

   private static void drawWaypointPanel() {
      float var0 = 340.0F;
      float var1 = 112.0F;
      float var2 = 600.0F;
      float var3 = 52.0F;
      float var4 = 150.0F;
      rrectCorners(var0, var1, var2, var3, 8.0F, true, true, waypointCollapsed, waypointCollapsed, -226854530);
      textC("点击遥控器 C1 或在地图设定航点", var0 + var2 / 2.0F, var1 + var3 / 2.0F, 21.0F, -1, false, 1, false);
      g.icon(waypointCollapsed ? 47 : 26, var0 + var2 - 46.0F, var1 + 12.0F, 28.0F, 28.0F, -1);
      hit(var0, var1, var2, var3, () -> waypointCollapsed = !waypointCollapsed);
      if (!waypointCollapsed) {
         float var5 = var1 + var3;
         rrectCorners(var0, var5, var2, var4, 8.0F, false, false, true, true, -230867649);
         hit(var0, var5, var2, var4, () -> {
         });
         g.icon(24, var0 + 14.0F, var5 + 22.0F, 36.0F, 36.0F, -1);
         float var6 = var0 + 68.0F;
         float var7 = 464.0F;
         float var8 = 44.0F;
         float var9 = var5 + 18.0F;
         rrect(var6, var9, var7, var8, 6.0F, -10197916);
         rrect(var6 + (waypointTab == 0 ? 0.0F : var7 / 2.0F), var9, var7 / 2.0F, var8, 6.0F, -1);
         textC("航点", var6 + var7 / 4.0F, var9 + var8 / 2.0F, 21.0F, waypointTab == 0 ? -15658735 : -1, false, 1, false);
         textC("兴趣点", var6 + var7 * 3.0F / 4.0F, var9 + var8 / 2.0F, 21.0F, waypointTab == 1 ? -15658735 : -1, false, 1, false);
         hit(var6, var9, var7 / 2.0F, var8, () -> waypointTab = 0);
         hit(var6 + var7 / 2.0F, var9, var7 / 2.0F, var8, () -> waypointTab = 1);
         g.icon(27, var0 + var2 - 54.0F, var9 + 6.0F, 32.0F, 32.0F, -1);
         hit(var0 + var2 - 60.0F, var9, 48.0F, var8, () -> open(10));
         int var10 = Math.min(waypoints.size(), 6);
         float var11 = 64.0F;
         float var12 = 12.0F;
         float var13 = (float)var10 * (var11 + var12) + var11;
         float var14 = var0 + var2 / 2.0F - var13 / 2.0F;
         float var15 = var5 + 76.0F;
         int var16 = 0;

         while (var16 < var10) {
            float var17;
            boolean var18 = hover(var17 = var14 + (float)var16 * (var11 + var12), var15, var11, var11);
            rrect(var17, var15, var11, var11, 6.0F, var18 ? -11119014 : -11908532);
            double[] var19 = waypoints.get(var16);
            int var20 = var19[3] > 0.0 ? -12218 : -1;
            textC(String.valueOf(var16 + 1), var17 + var11 / 2.0F, var15 + 24.0F, 24.0F, var20, true, 1, false);
            textC(var19[3] > 0.0 ? "兴趣点" : String.format(Locale.ROOT, "%.0fm", var19[1]), var17 + var11 / 2.0F, var15 + 48.0F, 13.0F, -6513506, false, 1, false);
            int var21 = var16++;
            hit(var17, var15, var11, var11, () -> {
               waypoints.remove(var21);
               toast("已删除航点 " + (var21 + 1));
            });
         }

         float var22;
         boolean var23 = hover(var22 = var14 + (float)var10 * (var11 + var12), var15, var11, var11);
         rrect(var22, var15, var11, var11, 6.0F, var23 ? -11908532 : -230867649);
         rrectStroke(var22, var15, var11, var11, 6.0F, 2.0F, -9868949);
         g.icon(25, var22 + 14.0F, var15 + 14.0F, 36.0F, 36.0F, -1);
         hit(var22, var15, var11, var11, Rc2FlightUi::addWaypoint);
      }
   }

   private static void addWaypoint() {
      if (!T.connected) {
         toast("未连接飞行器");
      } else if (waypoints.size() >= 6) {
         toast("航点数量已达上限");
      } else {
         waypoints.add(new double[]{T.droneX, T.altitude, T.droneZ, (double)waypointTab});
         toast("已添加" + (waypointTab == 0 ? "航点 " : "兴趣点 ") + waypoints.size());
      }
   }

   private static void drawPanel() {
      hit(0.0F, 0.0F, 1280.0F, 680.0F, () -> {
         panel = 0;
         holdId = null;
      });
      switch (panel) {
         case 1:
            drawModePanel();
            break;
         case 2:
            drawCameraMenu();
            break;
         case 3:
            drawPlaybackPanel();
            break;
         case 4:
            drawSettingsPanel();
            break;
         case 5:
            drawMapPanel();
            break;
         case 6:
            drawFocusPanel();
            break;
         case 7:
            drawTakeoffPanel();
            break;
         case 8:
            drawBatteryPanel();
            break;
         case 9:
            drawFourGPanel();
            break;
         case 10:
            drawWaypointMore();
            break;
         case 11:
            drawParamsPanel();
      }
   }

   private static void block(float var0, float var1, float var2, float var3) {
      hit(var0, var1, var2, var3, () -> {
      });
   }

   private static void drawModePanel() {
      float var0 = 104.0F;
      float var1 = 98.0F;
      float var2 = 420.0F;
      float var3 = 66.0F;
      float var4 = 16.0F + var3 * 3.0F;
      rrect(var0, var1, var2, var4, 10.0F, -179680692);
      block(var0, var1, var2, var4);
      int var5 = 0;

      while (var5 < 3) {
         float var6 = var1 + 8.0F + (float)var5 * var3;
         boolean var7 = T.mode == var5;
         boolean var8 = hover(var0 + 8.0F, var6, var2 - 16.0F, var3 - 4.0F);
         if (var7 || var8) {
            rrect(var0 + 8.0F, var6, var2 - 16.0F, var3 - 4.0F, 8.0F, var7 ? 872415231 : 452984831);
         }

         rrect(var0 + 22.0F, var6 + 13.0F, 40.0F, 36.0F, 5.0F, -15132391);
         textC(MODE_LETTERS[var5], var0 + 42.0F, var6 + 31.0F, 22.0F, var5 == 1 ? -34278 : -1, true, 1, false);
         g.text(MODE_NAMES[var5] + "  " + MODE_EN[var5], var0 + 76.0F, var6 + 9.0F, 20.0F, -1, true, false);
         g.text(trimMode(bridge.modeDescription(var5)), var0 + 76.0F, var6 + 36.0F, 14.0F, -4342339, false, false);
         if (var7) {
            g.icon(42, var0 + var2 - 44.0F, var6 + 18.0F, 24.0F, 24.0F, -1);
         }

         int var9 = var5++;
         hit(var0 + 8.0F, var6, var2 - 16.0F, var3 - 4.0F, () -> {
            bridge.setMode(var9);
            panel = 0;
         });
      }
   }

   private static String trimMode(String var0) {
      int var1 = var0.indexOf(183);
      return var1 >= 0 ? var0.substring(var1 + 1).trim() : var0;
   }

   private static void drawCameraMenu() {
      float var0 = 112.0F;
      float var1 = 384.0F;
      float var2 = 1150.0F - var0 * 2.0F;
      float var3 = 337.0F - var1 / 2.0F;
      rrectCorners(var2, var3, var0, var1, 6.0F, true, false, true, false, -181128140);
      rrectCorners(var2 + var0, var3, var0, var1, 6.0F, false, true, false, true, -182115035);
      block(var2, var3, var0 * 2.0F, var1);
      String[] var4 = new String[]{"拍照", "录像", "大师镜头", ""};
      int[] var5 = new int[]{31, 32, 33, 34};
      float var6 = var1 / 4.0F;

      for (int var7 = 0; var7 < 4; var7++) {
         float var8 = var3 + var6 * (float)var7 + var6 / 2.0F;
         boolean var9 = var7 == 0 && cameraTab == 0 || var7 == 1 && cameraTab == 1;
         menuItem(var2 + var0, var8, var0, var6, var5[var7], var4[var7], var9);
         int var10 = var7;
         hit(var2 + var0, var8 - var6 / 2.0F, var0, var6, () -> {
            if (var10 != 0 && var10 != 1) {
               if (var10 == 2) {
                  toast("大师镜头 · 请在空旷环境下使用");
               } else {
                  open(11);
               }
            } else {
               if (recording && var10 == 0) {
                  toast("录像中无法切换");
                  return;
               }

               cameraTab = var10;
            }
         });
      }

      String[] var16 = cameraTab == 0 ? PHOTO_MODES : VIDEO_MODES;
      int[] var17 = cameraTab == 0 ? PHOTO_ICONS : VIDEO_ICONS;
      int var18 = cameraTab == 0 ? photoMode : videoMode;
      float var19 = cameraTab == 0 ? var6 : var1 / 3.5F;
      float var11 = cameraTab == 0 ? var3 : var3 + (var1 - var19 * (float)var16.length) / 2.0F;

      for (int var12 = 0; var12 < var16.length; var12++) {
         float var13 = var11 + var19 * (float)var12 + var19 / 2.0F;
         boolean var14 = cameraTab == 0 && var12 == 3;
         menuItem(var2, var13, var0, var19, var17[var12], var14 ? "" : var16[var12], var12 == var18);
         int var15 = var12;
         hit(var2, var13 - var19 / 2.0F, var0, var19, () -> {
            if (cameraTab == 0) {
               photoMode = var15;
            } else {
               videoMode = var15;
            }

            toast("已选择 " + var16[var15]);
         });
      }
   }

   private static void menuItem(float var0, float var1, float var2, float var3, int var4, String var5, boolean var6) {
      boolean var7 = hover(var0, var1 - var3 / 2.0F, var2, var3);
      if (var7) {
         rect(var0 + 4.0F, var1 - var3 / 2.0F + 4.0F, var2 - 8.0F, var3 - 8.0F, 352321535);
      }

      int var8 = var6 ? -12218 : -1;
      if (var5.isEmpty()) {
         g.icon(var4, var0 + var2 / 2.0F - 17.0F, var1 - 17.0F, 34.0F, 34.0F, var8);
      } else {
         g.icon(var4, var0 + var2 / 2.0F - 16.0F, var1 - 30.0F, 32.0F, 32.0F, var8);
         textC(var5, var0 + var2 / 2.0F, var1 + 18.0F, 16.0F, var8, false, 1, false);
      }
   }

   private static void drawFocusPanel() {
      float var0 = 118.0F;
      float var1 = 262.0F;
      float var2 = 486.0F;
      float var3 = 124.0F;
      rrect(var0, var1, var2, var3, 18.0F, -230012340);
      block(var0, var1, var2, var3);
      String[] var4 = new String[]{"跟随", "锁定", "环绕"};
      int[] var5 = new int[]{3, 22, 23};
      String[] var6 = new String[]{"飞行器在拍摄目标后方跟随", "相机始终朝向拍摄目标", "飞行器环绕拍摄目标飞行"};
      float var7 = var2 / 3.0F;

      for (int var8 = 0; var8 < 3; var8++) {
         float var9 = var0 + var7 * (float)var8;
         if (var8 > 0) {
            rect(var9 - 1.0F, var1, 2.0F, var3, -10724258);
         }

         if (hover(var9, var1, var7, var3)) {
            rect(var9 + (float)(var8 == 0 ? 10 : 1), var1 + 1.0F, var7 - (float)(var8 != 0 && var8 != 2 ? 2 : 11), var3 - 2.0F, 352321535);
         }

         int var10 = trackingMode == var8 + 1 ? -12218 : -1;
         g.icon(var5[var8], var9 + var7 / 2.0F - 25.0F, var1 + 12.0F, 50.0F, 50.0F, var10);
         textC(var4[var8], var9 + var7 / 2.0F, var1 + 90.0F, 26.0F, var10, false, 1, false);
         int var11 = var8 + 1;
         hit(var9, var1, var7, var3, () -> {
            trackingMode = var11;
            panel = 0;
            toast("焦点跟随 · " + var4[var11 - 1] + "：" + var6[var11 - 1]);
         });
      }
   }

   private static void drawTakeoffPanel() {
      float var0 = 112.0F;
      float var1 = 214.0F;
      float var2 = 420.0F;
      float var3 = 282.0F;
      rrect(var0, var1, var2, var3, 12.0F, -181850572);
      block(var0, var1, var2, var3);
      if (!T.armed) {
         holdButton(var0 + var2 / 2.0F, var1 + 112.0F, "takeoff", 4, "起飞", () -> bridge.takeoffOrLand());
         textC("辅助影像准备中", var0 + var2 / 2.0F, var1 + 222.0F, 15.0F, -1, false, 1, false);
         textC("辅助影像准备中", var0 + var2 / 2.0F, var1 + 246.0F, 15.0F, -1, false, 1, false);
      } else {
         holdButton(var0 + 108.0F, var1 + 112.0F, "land", 5, "降落", () -> bridge.takeoffOrLand());
         holdButton(var0 + var2 - 108.0F, var1 + 112.0F, "rth", 6, "返航", () -> bridge.returnHome());
         g.text("长按降落：飞机将直线下降", var0 + 24.0F, var1 + 214.0F, 15.0F, -1, false, false);
         g.text("长按返航：飞机将上升到指定返航高度，直线飞回返航点上方", var0 + 24.0F, var1 + 240.0F, 15.0F, -1, false, false);
      }
   }

   private static void holdButton(float var0, float var1, String var2, int var3, String var4, Runnable var5) {
      boolean var6 = var2.equals(holdId);
      boolean var7 = hoverCircle(var0, var1, 66.0F);
      disc(var0, var1, 68.0F, -14802394);
      disc(var0, var1, 64.0F, var6 ? -9802127 : (var7 ? -10196885 : -10789022));
      if (var6) {
         float var8 = clamp((float)(now - holdStart) / 1.0E9F, 0.0F, 1.0F);
         arc(var0, var1, 68.0F, 4.0F, 0.0F, var8 * 360.0F, -1);
      }

      g.icon(var3, var0 - 19.0F, var1 - 34.0F, 38.0F, 38.0F, -1);
      textC(var4, var0, var1 + 20.0F, 18.0F, -1, false, 1, false);
      hitCircle(var0, var1, 66.0F, () -> {
         holdId = var2;
         holdStart = now;
         holdAction = () -> {
            var5.run();
            panel = 0;
         };
      });
   }

   private static void updateHold() {
      if (holdId != null) {
         if (mouseDown && panel == 7) {
            if (now - holdStart >= 1000000000L && holdAction != null) {
               Runnable var0 = holdAction;
               holdId = null;
               holdAction = null;
               var0.run();
            }
         } else {
            holdId = null;
            holdAction = null;
         }
      }
   }

   private static void drawBatteryPanel() {
      float var0 = 640.0F;
      float var1 = 98.0F;
      float var2 = 470.0F;
      float var3 = 300.0F;
      rrect(var0, var1, var2, var3, 10.0F, -179680692);
      block(var0, var1, var2, var3);
      boolean var4 = T.battery >= 0;
      int var5 = var4 ? clampI(T.battery, 0, 100) : 0;
      int var6 = var4 ? batteryColor(var5) : -7433578;
      float var7 = var0 + 80.0F;
      float var8 = var1 + 86.0F;
      disc(var7, var8, 50.0F, -14013394);
      ring(var7, var8, 50.0F, 6.0F, -12894140);
      if (var4) {
         arc(var7, var8, 50.0F, 6.0F, 0.0F, (float)var5 * 3.6F, var6);
      }

      textC(var4 ? var5 + "%" : "N/A", var7, var8 - 4.0F, 28.0F, var6, true, 1, false);
      textC("当前电量", var7, var8 + 22.0F, 13.0F, -6513506, false, 1, false);
      float var9 = var0 + 160.0F;
      batteryRow(var9, var1 + 30.0F, "剩余可飞行时间", flightTime(T.remainingSec), var4 ? -1 : -7433578, var0 + var2 - 24.0F);
      batteryRow(var9, var1 + 74.0F, "自动返航剩余", flightTime(T.autoReturnSec), var4 ? -548325 : -7433578, var0 + var2 - 24.0F);
      batteryRow(var9, var1 + 118.0F, "强制降落剩余", flightTime(T.forcedLandSec), var4 ? -906460 : -7433578, var0 + var2 - 24.0F);
      rect(var0 + 20.0F, var1 + 170.0F, var2 - 40.0F, 1.0F, 654311423);
      legend(var0 + 24.0F, var1 + 186.0F, -14884475, "绿色：电量充足");
      legend(var0 + 24.0F, var1 + 220.0F, -548325, "黄色：电量仅够返航" + (var4 ? "（" + T.autoReturnPct + "%）" : ""));
      legend(var0 + 24.0F, var1 + 254.0F, -906460, "红色：电量仅供降落" + (var4 ? "（" + T.forcedLandPct + "%）" : ""));
      String var10 = var4 ? "返航需 " + T.returnPct + "%" : "N/A · 等待恢复连接";
      g.text(var10, var0 + var2 - 24.0F - g.textWidth(var10, 14.0F, false), var1 + 188.0F, 14.0F, -6513506, false, false);
   }

   private static void batteryRow(float var0, float var1, String var2, String var3, int var4, float var5) {
      g.text(var2, var0, var1 + 6.0F, 16.0F, -3092272, false, false);
      g.text(var3, var5 - g.textWidth(var3, 24.0F, true), var1, 24.0F, var4, true, false);
   }

   private static void legend(float var0, float var1, int var2, String var3) {
      rrect(var0, var1 + 2.0F, 12.0F, 12.0F, 2.0F, var2);
      g.text(var3, var0 + 22.0F, var1, 15.0F, -2565928, false, false);
   }

   private static void drawFourGPanel() {
      float var0 = 296.0F;
      float var1 = 150.0F;
      float var2 = clamp(fourGAnchor - var0 / 2.0F, 20.0F, 1280.0F - var0 - 20.0F);
      float var3 = 96.0F;
      poly(new float[]{fourGAnchor - 9.0F, var3 + 0.5F, fourGAnchor + 9.0F, var3 + 0.5F, fourGAnchor, var3 - 8.0F}, -179680692);
      rrect(var2, var3, var0, var1, 6.0F, -179680692);
      block(var2, var3, var0, var1);
      String var4 = !T.telemetry ? "N/A" : (T.fourGBars >= 3 ? "强" : (T.fourGBars == 2 ? "中" : (T.fourGBars == 1 ? "弱" : "无信号")));
      textC(var4, var2 + var0 / 4.0F, var3 + 32.0F, 22.0F, -1, false, 1, false);
      textC("天空网络", var2 + var0 / 4.0F, var3 + 62.0F, 19.0F, -4342339, false, 1, false);
      textC(var4, var2 + var0 * 3.0F / 4.0F, var3 + 32.0F, 22.0F, -1, false, 1, false);
      textC("地面网络", var2 + var0 * 3.0F / 4.0F, var3 + 62.0F, 19.0F, -4342339, false, 1, false);
      g.text("增强图传", var2 + 24.0F, var3 + 104.0F, 21.0F, -1, false, false);
      toggle(var2 + var0 - 84.0F, var3 + 101.0F, 3, 2);
   }

   private static void drawWaypointMore() {
      float var0 = 640.0F;
      float var1 = 318.0F;
      float var2 = 300.0F;
      float var3 = 50.0F;
      String[][] var4 = new String[][]{
         {"航线全局速度", new String[]{"3 m/s", "5 m/s", "8 m/s", "10 m/s", "15 m/s"}[wpSpeed]},
         {"选择镜头", new String[]{"广角", "中长焦"}[wpLens]},
         {"任务结束行为", new String[]{"返航", "悬停", "降落"}[wpFinish]},
         {"失控行为", new String[]{"返航", "继续执行"}[wpLost]},
         {"起始航点", waypoints.isEmpty() ? "--" : "航点 " + (Math.min(wpStart, waypoints.size() - 1) + 1)}
      };
      float var5 = (float)var4.length * var3 + 70.0F;
      rrect(var0, var1, var2, var5, 8.0F, -179680692);
      block(var0, var1, var2, var5);

      for (int var6 = 0; var6 < var4.length; var6++) {
         float var7 = var1 + 8.0F + (float)var6 * var3;
         boolean var8 = hover(var0 + 6.0F, var7, var2 - 12.0F, var3);
         if (var8) {
            rrect(var0 + 6.0F, var7, var2 - 12.0F, var3, 6.0F, 452984831);
         }

         g.text(var4[var6][0], var0 + 18.0F, var7 + 15.0F, 17.0F, -1, false, false);
         g.text(var4[var6][1], var0 + var2 - 36.0F - g.textWidth(var4[var6][1], 17.0F, false), var7 + 15.0F, 17.0F, -4342339, false, false);
         g.icon(46, var0 + var2 - 32.0F, var7 + 15.0F, 20.0F, 20.0F, -4342339);
         int var9 = var6;
         hit(var0 + 6.0F, var7, var2 - 12.0F, var3, () -> {
            switch (var9) {
               case 0:
                  wpSpeed = (wpSpeed + 1) % 5;
                  break;
               case 1:
                  wpLens = (wpLens + 1) % 2;
                  break;
               case 2:
                  wpFinish = (wpFinish + 1) % 3;
                  break;
               case 3:
                  wpLost = (wpLost + 1) % 2;
                  break;
               default:
                  wpStart = waypoints.isEmpty() ? 0 : (wpStart + 1) % waypoints.size();
            }
         });
      }

      float var10 = var1 + var5 - 56.0F;
      boolean var11 = waypoints.size() >= 2;
      boolean var12 = hover(var0 + 16.0F, var10, var2 - 32.0F, 42.0F);
      rrect(var0 + 16.0F, var10, var2 - 32.0F, 42.0F, 6.0F, var11 ? (var12 ? -1644826 : -1) : -10197916);
      textC("开始执行航线", var0 + var2 / 2.0F, var10 + 21.0F, 18.0F, var11 ? -15658735 : -4342339, false, 1, false);
      hit(var0 + 16.0F, var10, var2 - 32.0F, 42.0F, () -> {
         if (!var11) {
            toast("至少需要 2 个航点");
         } else {
            panel = 0;
            toast("航线任务已开始 · " + waypoints.size() + " 个航点", 2400L);
         }
      });
   }

   private static void drawParamsPanel() {
      float var0 = 700.0F;
      float var1 = 120.0F;
      float var2 = 540.0F;
      float var3 = 420.0F;
      rrect(var0, var1, var2, var3, 10.0F, -182311901);
      block(var0, var1, var2, var3);
      g.text(autoExposure ? "拍摄参数 · AUTO" : "拍摄参数 · PRO", var0 + 24.0F, var1 + 20.0F, 21.0F, -1, true, false);
      rect(var0 + 20.0F, var1 + 58.0F, var2 - 40.0F, 1.0F, 352321535);
      float var4 = var1 + 70.0F;
      paramRow(var0, var4, var2, "分辨率", RES, resIndex, var0x -> resIndex = var0x);
      float var5 = var4 + 52.0F;
      paramRow(var0, var5, var2, "帧率", FPS, fpsIndex, var0x -> fpsIndex = var0x);
      if (autoExposure) {
         var4 = var5 + 52.0F;
         paramRow(
            var0,
            var4,
            var2,
            "EV",
            new String[]{"-1.0", "-0.3", "0.0", "+0.3", "+1.0"},
            evIndex(),
            var0x -> ev = new float[]{-1.0F, -0.3F, 0.0F, 0.3F, 1.0F}[var0x]
         );
         float var6 = var4 + 52.0F;
         paramRow(var0, var6, var2, "色彩模式", COLOR, colorMode, var0x -> colorMode = var0x);
      } else {
         var4 = var5 + 52.0F;
         paramRow(var0, var4, var2, "快门", SHUTTER, shutterIndex, var0x -> shutterIndex = var0x);
         float var11 = var4 + 52.0F;
         paramRow(var0, var11, var2, "ISO", ISO, isoIndex, var0x -> isoIndex = var0x);
         var4 = var11 + 52.0F;
         paramRow(var0, var4, var2, "白平衡", WB, wbIndex, var0x -> wbIndex = var0x);
         float var7 = var4 + 52.0F;
         paramRow(var0, var7, var2, "色彩模式", COLOR, colorMode, var0x -> colorMode = var0x);
      }

      g.text("由于机型或软件版本的差异，调参界面显示的元素略有不同", var0 + 24.0F, var1 + var3 - 34.0F, 13.0F, -6513506, false, false);
   }

   private static void paramRow(float var0, float var1, float var2, String var3, String[] var4, int var5, Rc2FlightUi.IntSetter var6) {
      g.text(var3, var0 + 24.0F, var1 + 14.0F, 17.0F, -1, false, false);
      float var7 = Math.min(76.0F, (var2 - 150.0F) / (float)var4.length);
      float var8 = var0 + var2 - 24.0F - var7 * (float)var4.length;
      rrect(var8, var1 + 6.0F, var7 * (float)var4.length, 36.0F, 4.0F, -11710642);
      int var9 = 0;

      while (var9 < var4.length) {
         float var10 = var8 + (float)var9 * var7;
         if (var9 == var5) {
            rrect(var10, var1 + 6.0F, var7, 36.0F, 4.0F, -460293);
         } else if (hover(var10, var1 + 6.0F, var7, 36.0F)) {
            rrect(var10, var1 + 6.0F, var7, 36.0F, 4.0F, 654311423);
         }

         textC(var4[var9], var10 + var7 / 2.0F, var1 + 24.0F, var4[var9].length() > 5 ? 12.0F : 14.0F, var9 == var5 ? -15658735 : -1, false, 1, false);
         int var11 = var9++;
         hit(var10, var1 + 6.0F, var7, 36.0F, () -> var6.set(var11));
      }
   }

   private static int evIndex() {
      float[] var0 = new float[]{-1.0F, -0.3F, 0.0F, 0.3F, 1.0F};
      int var1 = 0;

      for (int var2 = 1; var2 < var0.length; var2++) {
         if (Math.abs(var0[var2] - ev) < Math.abs(var0[var1] - ev)) {
            var1 = var2;
         }
      }

      return var1;
   }

   private static void drawPlaybackPanel() {
      rect(0.0F, 0.0F, 1280.0F, 680.0F, -15461356);
      block(0.0F, 0.0F, 1280.0F, 680.0F);
      rect(0.0F, 0.0F, 1280.0F, 64.0F, -14935012);
      boolean var0 = hoverCircle(40.0F, 32.0F, 22.0F);
      if (var0) {
         disc(40.0F, 32.0F, 22.0F, 654311423);
      }

      g.icon(1, 24.0F, 16.0F, 32.0F, 32.0F, -1);
      hitCircle(40.0F, 32.0F, 24.0F, () -> {
         if (gallerySelected >= 0) {
            gallerySelected = -1;
         } else {
            panel = 0;
         }
      });
      g.text("相册", 72.0F, 20.0F, 22.0F, -1, true, false);
      String var1 = "飞行器存储 · " + gallery.size() + " 项";
      g.text(var1, 1248.0F - g.textWidth(var1, 16.0F, false), 24.0F, 16.0F, -6513506, false, false);
      if (gallerySelected < 0) {
         int var2 = 0;

         while (var2 < Math.min(8, gallery.size())) {
            float var3 = (float)(32 + var2 % 4 * 306);
            float var4 = (float)(84 + var2 / 4 * 196);
            drawLandscape(var3, var4, 294.0F, 180.0F, gallery.get(var2), var2);
            if (hover(var3, var4, 294.0F, 180.0F)) {
               rrectStroke(var3, var4, 294.0F, 180.0F, 4.0F, 2.0F, -1);
            }

            rrect(var3 + 8.0F, var4 + 8.0F, 84.0F, 24.0F, 4.0F, -1728053248);
            g.text(String.format(Locale.ROOT, "DJI_%04d", var2 + 1), var3 + 15.0F, var4 + 12.0F, 14.0F, -1, false, false);
            if (var2 % 2 == 0) {
               String var5 = "00:" + String.format(Locale.ROOT, "%02d", 12 + var2 * 7);
               g.text(var5, var3 + 286.0F - g.textWidth(var5, 14.0F, true), var4 + 158.0F, 14.0F, -1, true, true);
            }

            int var9 = var2++;
            hit(var3, var4, 294.0F, 180.0F, () -> gallerySelected = var9);
         }
      } else {
         int var6 = Math.min(gallerySelected, gallery.size() - 1);
         if (var6 < 0) {
            gallerySelected = -1;
            return;
         }

         drawLandscape(140.0F, 84.0F, 1000.0F, 520.0F, gallery.get(var6), var6);
         String var7 = String.format(Locale.ROOT, "DJI_%04d.%s", var6 + 1, var6 % 2 == 0 ? "MP4" : "JPG");
         textC(var7, 640.0F, 630.0F, 18.0F, -1, false, 1, false);
         boolean var8 = hoverCircle(1220.0F, 630.0F, 24.0F);
         if (var8) {
            disc(1220.0F, 630.0F, 24.0F, 654311423);
         }

         g.icon(43, 1206.0F, 616.0F, 28.0F, 28.0F, -1);
         hitCircle(1220.0F, 630.0F, 24.0F, () -> {
            if (gallerySelected >= 0 && gallerySelected < gallery.size()) {
               gallery.remove(gallerySelected);
            }

            gallerySelected = -1;
            toast("已删除");
         });
      }
   }

   private static void drawLandscape(float var0, float var1, float var2, float var3, long var4, int var6) {
      int var7 = new int[]{-740989, -1733766, -6521896, -867221, -9459498, -2520420}[var6 % 6];
      int var8 = new int[]{-10863253, -12834227, -13753262, -12965313, -14667187, -12770506}[var6 % 6];
      gradient(var0, var1, var2, var3 * 0.62F, var7, var8);
      rect(var0, var1 + var3 * 0.62F, var2, var3 * 0.38F, -14410191);
      disc(var0 + var2 * 0.66F, var1 + var3 * 0.36F, Math.max(8.0F, var2 / 14.0F), -1426066999);
      float var9 = var1 + var3 * 0.62F;

      for (int var10 = 0; var10 < 3; var10++) {
         float var11 = var0 + var2 * (0.12F + (float)var10 * 0.3F);
         float var12 = var3 * (0.22F + (float)(var4 >> var10 * 5 & 7L) * 0.012F);
         float var13 = var2 * 0.2F;
         poly(new float[]{var11 - var13, var9, var11 + var13, var9, var11, var9 - var12}, -13686976);
      }

      for (int var14 = 0; var14 < 6; var14++) {
         rect(var0 + var2 / 3.0F, var9 + 10.0F + (float)var14 * var3 * 0.05F, var2 / 4.0F, 2.0F, 872407216);
      }
   }

   private static void drawSettingsPanel() {
      rect(0.0F, 0.0F, 1280.0F, 680.0F, -14539741);
      block(0.0F, 0.0F, 1280.0F, 680.0F);
      rect(0.0F, 0.0F, 150.0F, 680.0F, -15198184);
      rect(150.0F, 0.0F, 1130.0F, 66.0F, -15198184);
      boolean var0 = hoverCircle(60.0F, 33.0F, 22.0F);
      if (var0) {
         disc(60.0F, 33.0F, 22.0F, 654311423);
      }

      g.icon(1, 44.0F, 17.0F, 32.0F, 32.0F, -1);
      hitCircle(60.0F, 33.0F, 24.0F, () -> {
         panel = 0;
         dragTarget = null;
      });
      float var1 = 1130.0F / (float)SETTING_TABS.length;
      int var2 = 0;

      while (var2 < SETTING_TABS.length) {
         float var3 = 180.0F + var1 * (float)var2 + var1 / 2.0F;
         boolean var4 = settingsTab == var2;
         textC(SETTING_TABS[var2], var3, 32.0F, 20.0F, var4 ? -1 : -5723992, var4, 1, false);
         if (var4) {
            rect(var3 - 15.0F, 52.0F, 30.0F, 3.0F, -1);
         }

         if (var2 == 3) {
            disc(var3 + 26.0F, 20.0F, 3.5F, -50384);
         }

         int var5 = var2++;
         hit(var3 - var1 / 2.0F, 0.0F, var1, 66.0F, () -> {
            settingsTab = var5;
            dragTarget = null;
         });
      }

      float var7 = 150.0F;
      float var8 = 1130.0F;
      float var9 = 66.0F;
      switch (settingsTab) {
         case 0:
            var9 = section(var7, var9, var8, "辅助飞行");
            var9 = obstacleIllustration(var7, var9, var8);
            String[] var6 = new String[]{"开启后，手动打杆飞行时，飞机遇到障碍物将自动绕行。", "开启后，手动打杆飞行时，飞机遇到障碍物将自动悬停。", "关闭后，飞机将不会主动避障，请谨慎飞行。"};
            var9 = segmentRow(var7, var9, var8, "避障行为", var6[segments[0][2]], 0, 2, new String[]{"绕行", "刹停", "关闭"});
            var9 = toggleRow(var7, var9, var8, "显示雷达图", "开启后，飞行界面将会显示实时障碍物检测雷达图。", 0, 3);
            var9 = section(var7, var9, var8, "返航");
            var9 = sliderRow(var7, var9, var8, "返航高度", "失联或手动返航时飞行器先上升到该高度", 0, 0, 20.0F, 500.0F, "m");
            sliderRow(var7, var9, var8, "最大飞行高度", "限制飞行器相对起飞点的最大高度", 0, 1, 30.0F, 500.0F, "m");
            break;
         case 1:
            var9 = section(var7, var9, var8, "飞行器");
            var9 = segmentRow(var7, var9, var8, "摇杆模式", "美国手：左摇杆控制升降与偏航", 1, 0, new String[]{"美国手", "日本手", "中国手"});
            var9 = sliderRow(var7, var9, var8, "摇杆灵敏度", "调整手动摇杆的输出比例", 1, 0, 10.0F, 100.0F, "%");
            var9 = toggleRow(var7, var9, var8, "紧急悬停保护", "短按取消飞行指令并保持当前位置", 1, 4);
            var9 = section(var7, var9, var8, "云台");
            segments[1][5] = T.fpvGimbal ? 1 : 0;
            var9 = segmentRow(var7, var9, var8, "云台模式", "稳定：保持画面水平；FPV：跟随机身倾斜", 1, 5, new String[]{"稳定", "FPV"});
            sliderRow(var7, var9, var8, "云台俯仰速度", "云台跟随拨轮的速度", 1, 1, 10.0F, 100.0F, "");
            break;
         case 2:
            var9 = section(var7, var9, var8, "拍摄");
            var9 = toggleRow(var7, var9, var8, "网格线", "三分构图辅助线", 2, 0);
            var9 = toggleRow(var7, var9, var8, "直方图", "实时亮度分布", 2, 1);
            var9 = toggleRow(var7, var9, var8, "过曝提示", "高亮区域斑马纹提示", 2, 2);
            var9 = segmentRow(var7, var9, var8, "防闪烁", "根据当地电网频率选择", 2, 3, new String[]{"自动", "50Hz", "60Hz"});
            segmentRow(var7, var9, var8, "照片格式", "JPEG 通用 / RAW 后期", 2, 4, new String[]{"JPEG", "RAW", "J+R"});
            break;
         case 3:
            var9 = section(var7, var9, var8, "图传");
            var9 = segmentRow(var7, var9, var8, "图传频段", "自动选择干扰最小的频段", 3, 0, new String[]{"自动", "2.4G", "5.8G"});
            var9 = toggleRow(var7, var9, var8, "增强图传", "使用 4G 网络补充图传链路，复杂环境下更稳定", 3, 2);
            var9 = segmentRow(var7, var9, var8, "图传清晰度", "更高清晰度会占用更多带宽", 3, 3, new String[]{"1080p", "2.7K"});
            var9 = sliderRow(var7, var9, var8, "图传码率", "手动模式下的目标码率", 3, 4, 10.0F, 100.0F, "Mbps");
            var9 = sliderRow(var7, var9, var8, "界面字号", "调整飞行界面文字大小，拖动即时生效", 3, 5, 80.0F, 120.0F, "%");
            if (FlightSettingsClient.editable()) {
               var9 = sliderRow(var7, var9, var8, "RC 图传距离", "服务器设置 · 保存到当前世界", 3, 6, 500.0F, 10000.0F, "m");
            } else {
               var9 = infoRow(var7, var9, var8, "RC 图传距离（由服务器管理）", Math.round(Rc2View.rcRange()) + "m");
            }

            infoRow(var7, var9, var8, "4G 覆盖", "45°定向 · 三维半径 500m");
            McDjiGfx.TEXT_BOOST = clamp(sliders[3][5] / 100.0F, 0.8F, 1.2F);
            break;
         case 4:
            var9 = section(var7, var9, var8, "软件语音");
            var9 = sliderRow(var7, var9, var8, "软件语音播报提示音", "调节开机、起飞、低电量等软件语音的音量", 4, 0, 0.0F, 100.0F, "%");
            infoRow(var7, var9, var8, "说明", "该音量独立于 Minecraft 的语音分类音量");
            break;
         default:
            var9 = section(var7, var9, var8, "关于");
            var9 = infoRow(var7, var9, var8, "飞行器", T.connected ? T.model : "N/A");
            var9 = infoRow(var7, var9, var8, "遥控器", "DJI RC 2");
            var9 = infoRow(var7, var9, var8, "固件版本", "DJI 开发版 · 合并 V1.0.32");
            var9 = infoRow(var7, var9, var8, "App 版本", "DronesJoyrideInterface");
            var9 = infoRow(var7, var9, var8, "图传", "O4 · 1080p / 30fps");
            g.text("所有 UI 样式均参照 DJI Fly 实际界面，部分拍摄与智能功能仅在界面层实现。", var7 + 30.0F, var9 + 22.0F, 15.0F, -6513506, false, false);
      }
   }

   private static float section(float var0, float var1, float var2, String var3) {
      rect(var0, var1, var2, 42.0F, -14079444);
      g.text(var3, var0 + 30.0F, var1 + 12.0F, 16.0F, -4934476, false, false);
      return var1 + 42.0F;
   }

   private static float obstacleIllustration(float var0, float var1, float var2) {
      float var3 = var1 + 62.0F;
      float var4 = var0 + var2 / 2.0F;
      g.icon(50, var4 - 230.0F, var3 - 22.0F, 44.0F, 44.0F, -1);

      for (int var5 = 0; var5 < 40; var5++) {
         float var6 = (float)var5 / 39.0F;
         rect(var4 - 170.0F + (float)var5 * 5.2F, var3 - 1.5F, 5.3F, 3.0F, alpha(-1, 0.08F + var6 * 0.85F));
      }

      poly(new float[]{var4 + 40.0F, var3 - 9.0F, var4 + 58.0F, var3, var4 + 40.0F, var3 + 9.0F}, -1);
      g.icon(50, var4 + 64.0F, var3 - 22.0F, 44.0F, 44.0F, -1);
      g.icon(51, var4 + 120.0F, var3 - 44.0F, 80.0F, 80.0F, -4342339);
      rect(var0, var1 + 124.0F, var2, 1.0F, 352321535);
      return var1 + 124.0F;
   }

   private static void rowTitle(float var0, float var1, String var2, String var3) {
      g.text(var2, var0 + 30.0F, var1 + 16.0F, 19.0F, -1, true, false);
      if (var3 != null) {
         g.text(var3, var0 + 30.0F, var1 + 46.0F, 14.0F, -6513506, false, false);
      }
   }

   private static float segmentRow(float var0, float var1, float var2, String var3, String var4, int var5, int var6, String[] var7) {
      float var8 = 82.0F;
      rowTitle(var0, var1, var3, var4);
      float var9 = 108.0F;
      float var10 = var9 * (float)var7.length;
      float var11 = var0 + var2 - 36.0F - var10;
      float var12 = var1 + 18.0F;
      rrect(var11, var12, var10, 36.0F, 3.0F, -11710642);
      int var13 = segments[var5][var6];
      int var14 = 0;

      while (var14 < var7.length) {
         float var15 = var11 + (float)var14 * var9;
         if (var14 == var13) {
            rrect(var15, var12, var9, 36.0F, 3.0F, -460293);
         } else if (hover(var15, var12, var9, 36.0F)) {
            rrect(var15, var12, var9, 36.0F, 3.0F, 654311423);
         }

         textC(var7[var14], var15 + var9 / 2.0F, var12 + 18.0F, 16.0F, var14 == var13 ? -15658735 : -1, false, 1, false);
         int var16 = var14++;
         hit(var15, var12, var9, 36.0F, () -> {
            segments[var5][var6] = var16;
            if (var5 == 1 && var6 == 5) {
               bridge.setFpvGimbal(var16 == 1);
            }
         });
      }

      rect(var0 + 30.0F, var1 + var8 - 1.0F, var2 - 60.0F, 1.0F, 352321535);
      return var1 + var8;
   }

   private static float toggleRow(float var0, float var1, float var2, String var3, String var4, int var5, int var6) {
      float var7 = 82.0F;
      rowTitle(var0, var1, var3, var4);
      toggle(var0 + var2 - 36.0F - 56.0F, var1 + 22.0F, var5, var6);
      rect(var0 + 30.0F, var1 + var7 - 1.0F, var2 - 60.0F, 1.0F, 352321535);
      return var1 + var7;
   }

   private static void toggle(float var0, float var1, int var2, int var3) {
      boolean var4 = toggles[var2][var3];
      rrect(var0, var1, 56.0F, 30.0F, 15.0F, var4 ? -14312711 : -10526879);
      disc(var4 ? var0 + 41.0F : var0 + 15.0F, var1 + 15.0F, 12.0F, -1);
      hit(var0 - 6.0F, var1 - 6.0F, 68.0F, 42.0F, () -> {
         toggles[var2][var3] = !toggles[var2][var3];
         if (var2 == 2) {
            grid = toggles[2][0];
            histogram = toggles[2][1];
            overexposure = toggles[2][2];
         }
      });
   }

   private static float sliderRow(float var0, float var1, float var2, String var3, String var4, int var5, int var6, float var7, float var8, String var9) {
      float var10 = 82.0F;
      rowTitle(var0, var1, var3, var4);
      float var11 = 300.0F;
      float var12 = var0 + var2 - 36.0F - var11;
      float var13 = var1 + 36.0F;
      float var14 = sliders[var5][var6];
      float var15 = clamp((var14 - var7) / (var8 - var7), 0.0F, 1.0F);
      rrect(var12, var13 - 2.0F, var11, 4.0F, 2.0F, -11710642);
      rrect(var12, var13 - 2.0F, var11 * var15, 4.0F, 2.0F, -1);
      disc(var12 + var11 * var15, var13, 10.0F, -1);
      String var16 = String.format(Locale.ROOT, "%.0f%s", var14, var9);
      g.text(var16, var12 - 24.0F - g.textWidth(var16, 18.0F, true), var13 - 10.0F, 18.0F, -1, true, false);
      Rc2FlightUi.Hit var17 = hit(var12 - 12.0F, var13 - 20.0F, var11 + 24.0F, 40.0F, () -> {
      });
      var17.drag = var4x -> {
         sliders[var5][var6] = (float)Math.round(var7 + (var8 - var7) * (float)var4x);
         if (var5 == 3 && var6 == 6) {
            FlightSettingsClient.requestRange((double)sliders[var5][var6]);
         }
      };
      rect(var0 + 30.0F, var1 + var10 - 1.0F, var2 - 60.0F, 1.0F, 352321535);
      return var1 + var10;
   }

   private static float infoRow(float var0, float var1, float var2, String var3, String var4) {
      g.text(var3, var0 + 30.0F, var1 + 22.0F, 18.0F, -4342339, false, false);
      g.text(var4, var0 + var2 - 36.0F - g.textWidth(var4, 18.0F, true), var1 + 22.0F, 18.0F, -1, true, false);
      rect(var0 + 30.0F, var1 + 63.0F, var2 - 60.0F, 1.0F, 352321535);
      return var1 + 64.0F;
   }

   private static void updateDrag() {
      if (dragTarget != null) {
         if (mouseDown && panel == 4) {
            dragTarget.accept((double)clamp((float)((mouseX - (double)dragX) / (double)dragW), 0.0F, 1.0F));
         } else {
            dragTarget = null;
         }
      }
   }

   private static void drawMapPanel() {
      block(0.0F, 0.0F, 1280.0F, 680.0F);
      if (mapTab == 0) {
         drawMap(0.0F, 0.0F, 1280.0F, 680.0F, 0.9, true);
         float var0 = 980.0F;
         float var1 = 22.0F;
         chip(var0, var1, 132.0F, "4G 覆盖", showCoverage, () -> showCoverage = !showCoverage);
         chip(var0 + 142.0F, var1, 132.0F, "RC 范围", showRcCoverage, () -> showRcCoverage = !showRcCoverage);
         rect(40.0F, 634.0F, 90.0F, 2.0F, -13947600);
         rect(40.0F, 628.0F, 2.0F, 8.0F, -13947600);
         rect(128.0F, 628.0F, 2.0F, 8.0F, -13947600);
         g.text("100m", 44.0F, 606.0F, 17.0F, -13947600, false, false);
      } else if (mapTab == 1) {
         rect(0.0F, 0.0F, 1280.0F, 680.0F, -14013651);
         drawAttitude(380.0F, 100.0F, 520.0F, 520.0F);
      } else {
         rect(0.0F, 0.0F, 1280.0F, 680.0F, -15724528);
         drawAssist(0.0F, 0.0F, 1280.0F, 680.0F, true);
      }

      disc(48.0F, 48.0F, 26.0F, -870441442);
      g.icon(1, 32.0F, 32.0F, 32.0F, 32.0F, -1);
      hitCircle(48.0F, 48.0F, 28.0F, () -> panel = 0);
      String[] var8 = new String[]{"地图", "姿态球", "飞行辅助影像"};
      float var9 = 136.0F;
      float var2 = var9 * 3.0F;
      float var3 = 640.0F - var2 / 2.0F;
      float var4 = 22.0F;
      rrect(var3, var4, var2, 38.0F, 4.0F, -431141042);
      int var5 = 0;

      while (var5 < 3) {
         float var6 = var3 + (float)var5 * var9;
         if (var5 == mapTab) {
            rrect(var6, var4, var9, 38.0F, 4.0F, -460293);
         }

         textC(var8[var5], var6 + var9 / 2.0F, var4 + 19.0F, 16.0F, var5 == mapTab ? -15658735 : -1, false, 1, false);
         int var7 = var5++;
         hit(var6, var4, var9, 38.0F, () -> {
            mapTab = var7;
            if (assistFloating && var7 != 2) {
               assistSlotType = var7;
            } else {
               mapType = var7;
            }
         });
      }
   }

   private static void chip(float var0, float var1, float var2, String var3, boolean var4, Runnable var5) {
      rrect(var0, var1, var2, 38.0F, 4.0F, var4 ? -460293 : -431141042);
      textC(var3, var0 + var2 / 2.0F, var1 + 19.0F, 16.0F, var4 ? -15658735 : -1, false, 1, false);
      hit(var0, var1, var2, 38.0F, var5);
   }

   private static void drawMap(float var0, float var1, float var2, float var3, double var4, boolean var6) {
      double var7 = T.droneX;
      double var9 = T.droneZ;
      float var11 = var0 + var2 / 2.0F;
      float var12 = var1 + var3 / 2.0F;
      g.pushClip(var0, var1, var2, var3);
      double var13 = rasterLeft;
      double var15 = rasterTop;
      double var17 = rasterRight;
      double var19 = rasterBottom;
      rasterLeft = Math.max(rasterLeft, (double)(var0 * s));
      rasterTop = Math.max(rasterTop, (double)(var1 * s));
      rasterRight = Math.min(rasterRight, (double)((var0 + var2) * s));
      rasterBottom = Math.min(rasterBottom, (double)((var1 + var3) * s));
      rect(var0, var1, var2, var3, -1315085);
      boolean var21 = g.mapTiles(var0, var1, var2, var3, var7, var9, var4) || drawSurface(var0, var1, var2, var3, var7, var9, var4);
      if (!var21) {
         float var22 = (float)((-var7 * var4 % 40.0 + 40.0) % 40.0);
         float var23 = (float)((-var9 * var4 % 40.0 + 40.0) % 40.0);

         for (float var24 = var0 + var22; var24 < var0 + var2; var24 += 40.0F) {
            rect(var24, var1, 1.0F, var3, -2301722);
         }

         for (float var38 = var1 + var23; var38 < var1 + var3; var38 += 40.0F) {
            rect(var0, var38, var2, 1.0F, -2301722);
         }
      }

      if (var6) {
         if (showRcCoverage && T.pilotKnown) {
            double var28 = Math.sqrt(Math.max(0.0, Rc2View.rcRange() * Rc2View.rcRange() - Math.pow(T.droneY - T.pilotY, 2.0))) * var4;
            ring(var11 + (float)((T.pilotX - var7) * var4), var12 + (float)((T.pilotZ - var9) * var4), (float)var28, 3.0F, -2140161537);
         }

         if (showCoverage) {
            for (double[] var32 : T.stations) {
               double var39 = Math.sqrt(Math.max(0.0, 250000.0 - Math.pow(T.droneY - var32[1], 2.0))) * var4;
               double var26 = Math.toDegrees(Math.atan2(var32[4], var32[3])) + 90.0;
               arc(
                  var11 + (float)((var32[0] - var7) * var4),
                  var12 + (float)((var32[2] - var9) * var4),
                  (float)var39,
                  (float)var39,
                  (float)var26 - 22.5F,
                  45.0F,
                  1090498906
               );
            }
         }
      }

      for (double[] var33 : T.stations) {
         float var40 = var11 + (float)((var33[0] - var7) * var4);
         float var25 = var12 + (float)((var33[2] - var9) * var4);
         if (!(var40 < var0) && !(var40 > var0 + var2) && !(var25 < var1) && !(var25 > var1 + var3)) {
            disc(var40, var25, var6 ? 7.0F : 4.0F, -13096933);
            disc(var40, var25, var6 ? 5.0F : 3.0F, -24765);
         }
      }

      double[] var31 = null;

      for (double[] var41 : trail) {
         if (var31 != null) {
            line(
               var11 + (float)((var31[0] - var7) * var4),
               var12 + (float)((var31[1] - var9) * var4),
               var11 + (float)((var41[0] - var7) * var4),
               var12 + (float)((var41[1] - var9) * var4),
               var6 ? 3.0F : 2.0F,
               -869430032
            );
         }

         var31 = var41;
      }

      for (double[] var42 : waypoints) {
         float var45 = var11 + (float)((var42[0] - var7) * var4);
         float var47 = var12 + (float)((var42[2] - var9) * var4);
         disc(var45, var47, var6 ? 9.0F : 6.0F, var42[3] > 0.0 ? -12218 : -13792016);
      }

      if (T.connected) {
         float var36 = var11 + (float)((T.homeX - var7) * var4);
         float var43 = var12 + (float)((T.homeZ - var9) * var4);
         float var46 = var6 ? 26.0F : 18.0F;
         g.icon(48, var36 - var46 / 2.0F, var43 - var46 / 2.0F, var46, var46, -1);
      }

      if (T.pilotKnown) {
         float var37 = var11 + (float)((T.pilotX - var7) * var4);
         float var44 = var12 + (float)((T.pilotZ - var9) * var4);
         disc(var37, var44, var6 ? 10.0F : 7.0F, -1);
         disc(var37, var44, var6 ? 7.5F : 5.0F, -13792016);
      }

      if (T.connected) {
         if (var6) {
            navArrow(var11, var12, 20.0F, (float)Math.toRadians(T.yaw + 180.0), -14803426, -1);
         } else {
            disc(var11, var12, 11.0F, -1);
            disc(var11, var12, 8.0F, -13948117);
         }
      }

      g.popClip();
      rasterLeft = var13;
      rasterTop = var15;
      rasterRight = var17;
      rasterBottom = var19;
      if (var6 && !var21) {
         textC("地图加载中 · 此区域尚未探索", var0 + var2 / 2.0F, var1 + var3 - 40.0F, 16.0F, -9802640, false, 1, false);
      }
   }

   private static boolean drawSurface(float var0, float var1, float var2, float var3, double var4, double var6, double var8) {
      ClientWorld var10 = MinecraftClient.getInstance().world;
      if (var10 == null) {
         return false;
      } else {
         SurfaceMap.beginFrame();
         float var11 = Math.max(4.0F, (float)(var8 * 4.0));
         float var12 = var0 + var2 / 2.0F;
         float var13 = var1 + var3 / 2.0F;
         int var14 = 0;

         for (float var15 = 0.0F; var15 < var3; var15 += var11) {
            int var16 = 0;
            float var17 = 0.0F;

            for (float var18 = 0.0F; var18 < var2; var18 += var11) {
               double var19 = var4 + ((double)(var18 + var11 * 0.5F) - (double)(var2 / 2.0F)) / var8;
               double var21 = var6 + ((double)(var15 + var11 * 0.5F) - (double)(var3 / 2.0F)) / var8;
               int var23 = SurfaceMap.colorAt(var10, (int)Math.floor(var19), (int)Math.floor(var21));
               if (var23 != var16) {
                  if (var16 != 0) {
                     rect(var0 + var17, var1 + var15, var18 - var17, Math.min(var11, var3 - var15), var16);
                  }

                  var16 = var23;
                  var17 = var18;
               }

               if (var23 != 0) {
                  var14++;
               }
            }

            if (var16 != 0) {
               rect(var0 + var17, var1 + var15, var2 - var17, Math.min(var11, var3 - var15), var16);
            }
         }

         return var14 > 20;
      }
   }

   private static void navArrow(float var0, float var1, float var2, float var3, int var4, int var5) {
      float[] var6 = new float[]{0.0F, -1.0F, 0.78F, 0.9F, 0.0F, 0.45F, -0.78F, 0.9F};
      float[] var7 = new float[8];
      float[] var8 = new float[8];
      float var9 = (float)Math.cos((double)var3);
      float var10 = (float)Math.sin((double)var3);

      for (int var11 = 0; var11 < 4; var11++) {
         float var12 = var6[var11 * 2];
         float var13 = var6[var11 * 2 + 1];
         var8[var11 * 2] = var0 + (var12 * var9 - var13 * var10) * (var2 + 3.0F);
         var8[var11 * 2 + 1] = var1 + (var12 * var10 + var13 * var9) * (var2 + 3.0F);
         var7[var11 * 2] = var0 + (var12 * var9 - var13 * var10) * var2;
         var7[var11 * 2 + 1] = var1 + (var12 * var10 + var13 * var9) * var2;
      }

      poly(new float[]{var8[0], var8[1], var8[2], var8[3], var8[4], var8[5]}, var5);
      poly(new float[]{var8[0], var8[1], var8[4], var8[5], var8[6], var8[7]}, var5);
      poly(new float[]{var7[0], var7[1], var7[2], var7[3], var7[4], var7[5]}, var4);
      poly(new float[]{var7[0], var7[1], var7[4], var7[5], var7[6], var7[7]}, var4);
   }

   private static void drawToast() {
      if (toastText != null && now <= toastUntil) {
         float var0 = clamp((float)(toastUntil - now) / 3.0E8F, 0.0F, 1.0F);
         float var1 = g.textWidth(toastText, 21.0F, false) + 48.0F;
         float var2 = (1280.0F - var1) / 2.0F;
         float var3 = 214.0F;
         rrect(var2, var3, var1, 44.0F, 6.0F, alpha(-348505542, var0));
         textC(toastText, 640.0F, var3 + 22.0F, 21.0F, alpha(-986896, var0), false, 1, false);
      }
   }

   public static void drawNoFeedMessage(DrawContext var0, boolean var1, boolean var2) {
      McDjiGfx var3 = new McDjiGfx(var0);
      DjiGfx var4 = g;
      float var5 = s;

      try {
         g = var3;
         s = var3.scale();
         drawNoFeed(var1, var2);
      } finally {
         g = var4;
         s = var5;
         var3.end();
      }
   }

   public static void drawNoFeed(boolean var0, boolean var1) {
      g.icon(var0 ? 44 : 19, 618.0F, 286.0F, 44.0F, 44.0F, var0 ? -906460 : -855638017);
      String var2 = var0 ? "图传异常" : (var1 ? "未连接飞行器" : "无图传信号");
      String var3 = var0 ? "请重新手持遥控器" : (var1 ? "右键飞行器完成配对" : "正在等待飞行器图传");
      textC(var2, 640.0F, 360.0F, 26.0F, -1, true, 1, false);
      textC(var3, 640.0F, 394.0F, 17.0F, -6513506, false, 1, false);
   }

   public static void mousePressed(double var0, double var2, int var4) {
      mouseDown = true;
      mouseX = var0;
      mouseY = var2;

      for (int var5 = hits.size() - 1; var5 >= 0; var5--) {
         Rc2FlightUi.Hit var6 = hits.get(var5);
         if (contains(var6, var0, var2)) {
            if (var6.drag != null) {
               dragTarget = var6.drag;
               dragX = var6.x + 12.0F;
               dragW = var6.w - 24.0F;
               dragTarget.accept((double)clamp((float)((var0 - (double)dragX) / (double)dragW), 0.0F, 1.0F));
               return;
            }

            if (var6.press != null) {
               var6.press.run();
               return;
            }

            if (var6.click != null) {
               var6.click.run();
            }

            return;
         }
      }
   }

   public static void mouseReleased(double var0, double var2, int var4) {
      mouseDown = false;
      releaseThumb(var0, var2);
      if (assistPickup) {
         boolean var5 = assistPicked;
         assistPickup = false;
         assistPicked = false;
         if (!var5) {
            mapTab = 2;
            open(5);
         }
      }

      dragTarget = null;
      holdId = null;
      holdAction = null;
      assistHoldDir = -1;
   }

   private static boolean contains(Rc2FlightUi.Hit var0, double var1, double var3) {
      return var0.circle
         ? Math.hypot(var1 - (double)var0.x, var3 - (double)var0.y) <= (double)var0.w
         : var1 >= (double)var0.x && var3 >= (double)var0.y && var1 < (double)(var0.x + var0.w) && var3 < (double)(var0.y + var0.h);
   }

   private static Rc2FlightUi.Hit hit(float var0, float var1, float var2, float var3, Runnable var4) {
      Rc2FlightUi.Hit var5 = new Rc2FlightUi.Hit();
      var5.x = var0;
      var5.y = var1;
      var5.w = var2;
      var5.h = var3;
      var5.click = var4;
      if (layer >= topLayer) {
         hits.add(var5);
      }

      return var5;
   }

   private static void hitCircle(float var0, float var1, float var2, Runnable var3) {
      hit(var0, var1, var2, var2, var3).circle = true;
   }

   private static boolean hover(float var0, float var1, float var2, float var3) {
      return mouseIn
         && layer >= topLayer
         && mouseX >= (double)var0
         && mouseY >= (double)var1
         && mouseX < (double)(var0 + var2)
         && mouseY < (double)(var1 + var3);
   }

   private static boolean hoverCircle(float var0, float var1, float var2) {
      return mouseIn && layer >= topLayer && Math.hypot(mouseX - (double)var0, mouseY - (double)var1) <= (double)var2;
   }

   private static void open(int var0) {
      panel = var0;
      gallerySelected = -1;
      dragTarget = null;
      holdId = null;
   }

   private static float textS(String var0, float var1, float var2, float var3, int var4, boolean var5) {
      return g.text(var0, var1, var2, var3, var4, var5, true);
   }

   private static float textC(String var0, float var1, float var2, float var3, int var4, boolean var5, int var6, boolean var7) {
      float var8 = g.textWidth(var0, var3, var5);
      float var9 = var6 == 1 ? -var8 / 2.0F : (var6 == 2 ? -var8 : 0.0F);
      return g.text(var0, var1 + var9, var2 - var3 / 2.0F, var3, var4, var5, var7);
   }

   private static void rightText(String var0, float var1, float var2, float var3, int var4, boolean var5) {
      textS(var0, var1 - g.textWidth(var0, var3, var5), var2, var3, var4, var5);
   }

   private static List<String> wrap(String var0, float var1, float var2) {
      ArrayList var3 = new ArrayList();
      StringBuilder var4 = new StringBuilder();

      for (int var5 = 0; var5 < var0.length(); var5++) {
         var4.append(var0.charAt(var5));
         if (g.textWidth(var4.toString(), var1, false) > var2 && var4.length() > 1) {
            char var6 = var4.charAt(var4.length() - 1);
            var4.setLength(var4.length() - 1);
            if ("，。、；：".indexOf(var6) >= 0) {
               var4.append(var6);
               var3.add(var4.toString());
               var4.setLength(0);
            } else {
               var3.add(var4.toString());
               var4.setLength(0);
               var4.append(var6);
            }
         }
      }

      if (var4.length() > 0) {
         var3.add(var4.toString());
      }

      return var3;
   }

   private static String flightTime(int var0) {
      return var0 < 0 ? "N/A" : String.format(Locale.ROOT, "%02d'%02d\"", var0 / 60, var0 % 60);
   }

   private static String meters(double var0) {
      if (Double.isNaN(var0)) {
         return "N/A";
      } else {
         return var0 < 100.0 ? String.format(Locale.ROOT, "%.1fm", var0) : String.format(Locale.ROOT, "%.0fm", var0);
      }
   }

   private static String speed(double var0) {
      return Double.isNaN(var0) ? "N/A" : String.format(Locale.ROOT, "%.1fm/s", var0);
   }

   private static String signedSpeed(double var0) {
      return speed(Math.abs(var0) < 0.05 ? 0.0 : var0);
   }

   private static String clock(long var0) {
      return String.format(Locale.ROOT, "%02d:%02d", var0 / 60L, var0 % 60L);
   }

   private static String storageClock() {
      long var0 = (long)Math.max(0, gallery.size() - 6) * 40L + (recording ? (now - recordStart) / 1000000000L : 0L);
      long var2 = Math.max(0L, 17936L - var0);
      return String.format(Locale.ROOT, "%02d:%02d:%02d", var2 / 3600L, var2 / 60L % 60L, var2 % 60L);
   }

   private static String storageShort() {
      long var0 = (long)gallery.size() * 40L + (recording ? (now - recordStart) / 1000000000L : 0L);
      long var2 = Math.max(0L, 3000L - var0);
      return String.format(Locale.ROOT, "%02d:%02d", var2 / 60L, var2 % 60L);
   }

   private static String evText() {
      return String.format(Locale.ROOT, "%+.1f", ev);
   }

   private static String zoomLabel() {
      float var0 = T.zoom;
      return Math.abs(var0 - (float)Math.round(var0)) < 0.05F ? String.valueOf(Math.round(var0)) : String.format(Locale.ROOT, "%.1f", var0);
   }

   public static void tickTrail() {
      long var0;
      if (T.connected && (var0 = System.nanoTime()) - lastTrailSample >= 700000000L) {
         lastTrailSample = var0;
         trail.addLast(new double[]{T.droneX, T.droneZ});

         while (trail.size() > 120) {
            trail.removeFirst();
         }
      }
   }

   private static void spanFill(double var0, double var2, double var4, double var6, int var8, Rc2FlightUi.Spans var9) {
      var0 = Math.max(var0, rasterTop);
      var2 = Math.min(var2, rasterBottom);
      var4 = Math.max(var4, rasterLeft);
      var6 = Math.min(var6, rasterRight);
      int var10 = (int)Math.floor(var0);
      int var11 = (int)Math.ceil(var2);
      int var12 = (int)Math.floor(var4) - 1;
      int var13 = (int)Math.ceil(var6) + 1;
      if (var11 > var10 && var13 > var12) {
         int var14 = var13 - var12 + 2;
         float[] var15 = new float[var14];
         float[] var16 = new float[var14 + 1];
         int var17 = var8 >>> 24 & 0xFF;
         int var18 = var8 & 16777215;
         g.batch(() -> {
            for (int var10x = var10; var10x < var11; var10x++) {
               Arrays.fill(var15, 0.0F);
               Arrays.fill(var16, 0.0F);
               boolean var11x = false;

               for (int var12x = 0; var12x < 4; var12x++) {
                  int var13x = var9.at((double)var10x + ((double)var12x + 0.5) / 4.0, SPAN);

                  for (int var14x = 0; var14x < var13x; var14x++) {
                     double var15x = Math.max(SPAN[var14x * 2], (double)var12);
                     double var17x = Math.min(SPAN[var14x * 2 + 1], (double)var13);
                     if (!(var17x <= var15x)) {
                        var11x = true;
                        int var19x = (int)Math.floor(var15x);
                        int var20x = (int)Math.floor(var17x);
                        if (var19x == var20x) {
                           var15[var19x - var12] = var15[var19x - var12] + (float)(var17x - var15x) * 0.25F;
                        } else {
                           var15[var19x - var12] = var15[var19x - var12] + (float)((double)(var19x + 1) - var15x) * 0.25F;
                           var16[var19x + 1 - var12] = var16[var19x + 1 - var12] + 0.25F;
                           var16[var20x - var12] = var16[var20x - var12] - 0.25F;
                           var15[var20x - var12] = var15[var20x - var12] + (float)(var17x - (double)var20x) * 0.25F;
                        }
                     }
                  }
               }

               if (var11x) {
                  float var21x = 0.0F;
                  int var22x = 0;
                  int var23 = 0;

                  for (int var24 = 0; var24 < var14; var24++) {
                     var21x += var16[var24];
                     float var16x = Math.min(1.0F, var15[var24] + var21x);
                     int var25 = Math.round(var16x * 32.0F);
                     if (var25 != var23) {
                        if (var23 > 0) {
                           g.fillPx(var22x + var12, var10x, var24 + var12, var10x + 1, var17 * var23 / 32 << 24 | var18);
                        }

                        var22x = var24;
                        var23 = var25;
                     }
                  }

                  if (var23 > 0) {
                     g.fillPx(var22x + var12, var10x, var14 + var12, var10x + 1, var17 * var23 / 32 << 24 | var18);
                  }
               }
            }
         });
      }
   }

   private static void rect(float var0, float var1, float var2, float var3, int var4) {
      if (!(var2 <= 0.0F) && !(var3 <= 0.0F) && var4 >>> 24 != 0) {
         g.fillPx(Math.round(var0 * s), Math.round(var1 * s), Math.round((var0 + var2) * s), Math.round((var1 + var3) * s), var4);
      }
   }

   private static void gradient(float var0, float var1, float var2, float var3, int var4, int var5) {
      if (!(var2 <= 0.0F) && !(var3 <= 0.0F)) {
         g.gradientPx(Math.round(var0 * s), Math.round(var1 * s), Math.round((var0 + var2) * s), Math.round((var1 + var3) * s), var4, var5);
      }
   }

   private static void rrect(float var0, float var1, float var2, float var3, float var4, int var5) {
      rrectCorners(var0, var1, var2, var3, var4, true, true, true, true, var5);
   }

   private static void rrectCorners(
      float var0, float var1, float var2, float var3, float var4, boolean var5, boolean var6, boolean var7, boolean var8, int var9
   ) {
      if (!(var2 <= 0.0F) && !(var3 <= 0.0F) && var9 >>> 24 != 0) {
         var4 = Math.min(var4, Math.min(var2, var3) / 2.0F);
         double var10 = (double)(var0 * s);
         double var12 = (double)(var1 * s);
         double var14 = (double)(var2 * s);
         double var16 = (double)(var3 * s);
         double var18 = (double)(var4 * s);
         int var20 = (int)Math.ceil(var12 + var18);
         int var21 = (int)Math.floor(var12 + var16 - var18);
         Rc2FlightUi.Spans var22 = (var14x, var16x) -> {
            if (!(var14x < var12) && !(var14x >= var12 + var16)) {
               double var17 = 0.0;
               double var19 = 0.0;
               if (var14x < var12 + var18) {
                  double var21x = var12 + var18 - var14x;
                  double var23x = var18 - Math.sqrt(Math.max(0.0, var18 * var18 - var21x * var21x));
                  if (var5) {
                     var17 = var23x;
                  }

                  if (var6) {
                     var19 = var23x;
                  }
               } else if (var14x > var12 + var16 - var18) {
                  double var25 = var14x - (var12 + var16 - var18);
                  double var26 = var18 - Math.sqrt(Math.max(0.0, var18 * var18 - var25 * var25));
                  if (var7) {
                     var17 = var26;
                  }

                  if (var8) {
                     var19 = var26;
                  }
               }

               var16x[0] = var10 + var17;
               var16x[1] = var10 + var14 - var19;
               return 1;
            } else {
               return 0;
            }
         };
         if (var21 > var20) {
            spanFill(var12, (double)var20, var10, var10 + var14, var9, var22);
            spanFill((double)var21, var12 + var16, var10, var10 + var14, var9, var22);
            spanFill((double)var20, (double)var21, var10, var10 + var14, var9, (var4x, var6x) -> {
               var6x[0] = var10;
               var6x[1] = var10 + var14;
               return 1;
            });
         } else {
            spanFill(var12, var12 + var16, var10, var10 + var14, var9, var22);
         }
      }
   }

   private static void rrectStroke(float var0, float var1, float var2, float var3, float var4, float var5, int var6) {
      if (var6 >>> 24 != 0) {
         double var7 = (double)(var0 * s);
         double var9 = (double)(var1 * s);
         double var11 = (double)(var2 * s);
         double var13 = (double)(var3 * s);
         double var15 = (double)(Math.min(var4, Math.min(var2, var3) / 2.0F) * s);
         double var17 = (double)(var5 * s);
         double var19 = var7 + var17;
         double var21 = var9 + var17;
         double var23 = var11 - 2.0 * var17;
         double var25 = var13 - 2.0 * var17;
         double var27 = Math.max(0.0, var15 - var17);
         spanFill(var9, var9 + var13, var7, var7 + var11, var6, (var20, var22) -> {
            if (!(var20 < var9) && !(var20 >= var9 + var13)) {
               double var23x = roundInset(var20, var9, var13, var15);
               double var25x = var7 + var23x;
               double var27x = var7 + var11 - var23x;
               if (!(var20 < var21) && !(var20 >= var21 + var25)) {
                  double var29 = roundInset(var20, var21, var25, var27);
                  var22[0] = var25x;
                  var22[1] = var19 + var29;
                  var22[2] = var19 + var23 - var29;
                  var22[3] = var27x;
                  return 2;
               } else {
                  var22[0] = var25x;
                  var22[1] = var27x;
                  return 1;
               }
            } else {
               return 0;
            }
         });
      }
   }

   private static double roundInset(double var0, double var2, double var4, double var6) {
      if (var0 < var2 + var6) {
         double var10 = var2 + var6 - var0;
         return var6 - Math.sqrt(Math.max(0.0, var6 * var6 - var10 * var10));
      } else if (var0 > var2 + var4 - var6) {
         double var8 = var0 - (var2 + var4 - var6);
         return var6 - Math.sqrt(Math.max(0.0, var6 * var6 - var8 * var8));
      } else {
         return 0.0;
      }
   }

   private static void disc(float var0, float var1, float var2, int var3) {
      if (!(var2 <= 0.0F) && var3 >>> 24 != 0) {
         double var4 = (double)(var0 * s);
         double var6 = (double)(var1 * s);
         double var8 = (double)(var2 * s);
         spanFill(var6 - var8, var6 + var8, var4 - var8, var4 + var8, var3, (var6x, var8x) -> {
            double var9 = var6x - var6;
            if (Math.abs(var9) >= var8) {
               return 0;
            } else {
               double var11 = Math.sqrt(var8 * var8 - var9 * var9);
               var8x[0] = var4 - var11;
               var8x[1] = var4 + var11;
               return 1;
            }
         });
      }
   }

   private static void ring(float var0, float var1, float var2, float var3, int var4) {
      if (!(var2 <= 0.0F) && var4 >>> 24 != 0) {
         double var5 = (double)(var0 * s);
         double var7 = (double)(var1 * s);
         double var9 = (double)(var2 * s);
         double var11 = (double)Math.max(0.0F, (var2 - var3) * s);
         spanFill(var7 - var9, var7 + var9, var5 - var9, var5 + var9, var4, (var8, var10) -> {
            double var11x = var8 - var7;
            if (Math.abs(var11x) >= var9) {
               return 0;
            } else {
               double var13 = Math.sqrt(var9 * var9 - var11x * var11x);
               if (Math.abs(var11x) >= var11) {
                  var10[0] = var5 - var13;
                  var10[1] = var5 + var13;
                  return 1;
               } else {
                  double var15 = Math.sqrt(var11 * var11 - var11x * var11x);
                  var10[0] = var5 - var13;
                  var10[1] = var5 - var15;
                  var10[2] = var5 + var15;
                  var10[3] = var5 + var13;
                  return 2;
               }
            }
         });
      }
   }

   private static void dashedRing(float var0, float var1, float var2, int var3) {
      int var4 = Math.max(12, (int)(var2 / 3.0F));
      float var5 = 360.0F / (float)var4;

      for (int var6 = 0; var6 < var4; var6++) {
         arc(var0, var1, var2, 1.2F, (float)var6 * var5, var5 * 0.5F, var3);
      }
   }

   private static void arc(float var0, float var1, float var2, float var3, float var4, float var5, int var6) {
      if (!(var5 <= 0.0F) && var6 >>> 24 != 0) {
         if (var5 >= 359.99F) {
            ring(var0, var1, var2, var3, var6);
         } else if (var5 > 180.0F) {
            arc(var0, var1, var2, var3, var4, 180.0F, var6);
            arc(var0, var1, var2, var3, var4 + 180.0F, var5 - 180.0F, var6);
         } else {
            double var7 = (double)(var0 * s);
            double var9 = (double)(var1 * s);
            double var11 = (double)(var2 * s);
            double var13 = (double)Math.max(0.0F, (var2 - var3) * s);
            double var15 = Math.toRadians((double)(var4 - 90.0F));
            double var17 = Math.toRadians((double)(var4 + var5 - 90.0F));
            double var19 = Math.cos(var15);
            double var21 = Math.sin(var15);
            double var23 = Math.cos(var17);
            double var25 = Math.sin(var17);
            spanFill(var9 - var11, var9 + var11, var7 - var11, var7 + var11, var6, (var16, var18) -> {
               double var19x = var16 - var9;
               if (Math.abs(var19x) >= var11) {
                  return 0;
               } else {
                  double var21x = -1.0E9;
                  double var23x = 1.0E9;
                  if (Math.abs(var21) < 1.0E-9) {
                     if (var19 * var19x < 0.0) {
                        return 0;
                     }
                  } else if (var21 > 0.0) {
                     var23x = Math.min(var23x, var19 * var19x / var21);
                  } else {
                     var21x = Math.max(var21x, var19 * var19x / var21);
                  }

                  if (Math.abs(var25) < 1.0E-9) {
                     if (-var19x * var23 < 0.0) {
                        return 0;
                     }
                  } else if (var25 > 0.0) {
                     var21x = Math.max(var21x, var19x * var23 / var25);
                  } else {
                     var23x = Math.min(var23x, var19x * var23 / var25);
                  }

                  if (var23x <= var21x) {
                     return 0;
                  } else {
                     double var25x = Math.sqrt(var11 * var11 - var19x * var19x);
                     double var27 = Math.abs(var19x) < var13 ? Math.sqrt(var13 * var13 - var19x * var19x) : -1.0;
                     int var29 = 0;
                     if (var27 < 0.0) {
                        double var30 = Math.max(-var25x, var21x);
                        double var32 = Math.min(var25x, var23x);
                        if (var32 > var30) {
                           var18[0] = var7 + var30;
                           var18[1] = var7 + var32;
                           var29 = 1;
                        }
                     } else {
                        double var34 = Math.max(-var25x, var21x);
                        double var36 = Math.min(-var27, var23x);
                        if (var36 > var34) {
                           var18[0] = var7 + var34;
                           var18[1] = var7 + var36;
                           var29 = 1;
                        }

                        var34 = Math.max(var27, var21x);
                        var36 = Math.min(var25x, var23x);
                        if (var36 > var34) {
                           var18[var29 * 2] = var7 + var34;
                           var18[var29 * 2 + 1] = var7 + var36;
                           var29++;
                        }
                     }

                     return var29;
                  }
               }
            });
         }
      }
   }

   private static void poly(float[] var0, int var1) {
      int var2 = var0.length / 2;
      double[] var3 = new double[var2];
      double[] var4 = new double[var2];
      double var5 = 1.0E9;
      double var7 = -1.0E9;
      double var9 = 1.0E9;
      double var11 = -1.0E9;

      for (int var13 = 0; var13 < var2; var13++) {
         var3[var13] = (double)(var0[var13 * 2] * s);
         var4[var13] = (double)(var0[var13 * 2 + 1] * s);
         var5 = Math.min(var5, var4[var13]);
         var7 = Math.max(var7, var4[var13]);
         var9 = Math.min(var9, var3[var13]);
         var11 = Math.max(var11, var3[var13]);
      }

      spanFill(var5, var7, var9, var11, var1, (var3x, var5x) -> {
         double var6 = 1.0E9;
         double var8 = -1.0E9;

         for (int var10 = 0; var10 < var2; var10++) {
            int var11x = (var10 + 1) % var2;
            double var12 = var4[var10];
            double var14 = var4[var11x];
            if (var3x >= Math.min(var12, var14) && var3x < Math.max(var12, var14)) {
               double var16 = (var3x - var12) / (var14 - var12);
               double var18 = var3[var10] + var16 * (var3[var11x] - var3[var10]);
               var6 = Math.min(var6, var18);
               var8 = Math.max(var8, var18);
            }
         }

         if (var8 <= var6) {
            return 0;
         } else {
            var5x[0] = var6;
            var5x[1] = var8;
            return 1;
         }
      });
   }

   private static void line(float var0, float var1, float var2, float var3, float var4, int var5) {
      float var6 = var2 - var0;
      float var7 = var3 - var1;
      float var8 = (float)Math.sqrt((double)(var6 * var6 + var7 * var7));
      if (!(var8 < 0.001F)) {
         float var9 = -var7 / var8 * var4 / 2.0F;
         float var10 = var6 / var8 * var4 / 2.0F;
         poly(new float[]{var0 + var9, var1 + var10, var2 + var9, var3 + var10, var2 - var9, var3 - var10, var0 - var9, var1 - var10}, var5);
      }
   }

   private static void horizonDisc(float var0, float var1, float var2, double var3, double var5, int var7, int var8) {
      disc(var0, var1, var2, var7);
      double var9 = Math.toRadians(var5);
      double var11 = -Math.sin(var9);
      double var13 = Math.cos(var9);
      double var15 = (double)(var0 * s);
      double var17 = (double)(var1 * s);
      double var19 = (double)(var2 * s);
      double var21 = var3 * (double)var2 / 45.0 * (double)s;
      spanFill(var17 - var19, var17 + var19, var15 - var19, var15 + var19, var8, (var12, var14) -> {
         double var15x = var12 - var17;
         if (Math.abs(var15x) >= var19) {
            return 0;
         } else {
            double var17x = Math.sqrt(var19 * var19 - var15x * var15x);
            double var19x = -var17x;
            double var21x = var17x;
            double var23 = var21 - var13 * var15x;
            if (Math.abs(var11) < 1.0E-9) {
               if (var23 > 0.0) {
                  return 0;
               }
            } else if (var11 > 0.0) {
               var19x = Math.max(var19x, var23 / var11);
            } else {
               var21x = Math.min(var17x, var23 / var11);
            }

            if (var21x <= var19x) {
               return 0;
            } else {
               var14[0] = var15 + var19x;
               var14[1] = var15 + var21x;
               return 1;
            }
         }
      });
      rect(var0 - var2 * 0.55F, var1 - 0.75F, var2 * 1.1F, 1.5F, 1728053247);
   }

   private static void iconS(int var0, float var1, float var2, float var3, int var4) {
      g.icon(var0, var1 - var3 / 2.0F + 1.0F, var2 - var3 / 2.0F + 1.0F, var3, var3, 1493172224);
      g.icon(var0, var1 - var3 / 2.0F, var2 - var3 / 2.0F, var3, var3, var4);
   }

   private static float textK() {
      return layer == 0 ? 1.0F : 1.12F;
   }

   private static float clamp(float var0, float var1, float var2) {
      return var0 < var1 ? var1 : Math.min(var0, var2);
   }

   private static int clampI(int var0, int var1, int var2) {
      return var0 < var1 ? var1 : Math.min(var0, var2);
   }

   private static int alpha(int var0, float var1) {
      int var2 = var0 >>> 24 & 0xFF;
      return (int)((float)var2 * clamp(var1, 0.0F, 1.0F)) << 24 | var0 & 16777215;
   }

   public static void roundRect(DrawContext var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 0 && var4 > 0) {
         var5 = Math.min(var5, Math.min(var3, var4) / 2);

         for (int var7 = 0; var7 < var4; var7++) {
            int var8 = 0;
            if (var7 < var5) {
               double var9 = (double)(var5 - var7) - 0.5;
               var8 = var5 - (int)Math.sqrt(Math.max(0.0, (double)(var5 * var5) - var9 * var9));
            } else if (var7 >= var4 - var5) {
               double var12 = (double)(var7 - (var4 - var5)) + 0.5;
               var8 = var5 - (int)Math.sqrt(Math.max(0.0, (double)(var5 * var5) - var12 * var12));
            }

            var0.fill(var1 + var8, var2 + var7, var1 + var3 - var8, var2 + var7 + 1, var6);
         }
      }
   }

   public static void roundRectBorder(DrawContext var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      if (var3 > 0 && var4 > 0) {
         var5 = Math.min(var5, Math.min(var3, var4) / 2);

         for (int var7 = 0; var7 < var4; var7++) {
            int var8 = 0;
            if (var7 < var5) {
               double var9 = (double)(var5 - var7) - 0.5;
               var8 = var5 - (int)Math.sqrt(Math.max(0.0, (double)(var5 * var5) - var9 * var9));
            } else if (var7 >= var4 - var5) {
               double var12 = (double)(var7 - (var4 - var5)) + 0.5;
               var8 = var5 - (int)Math.sqrt(Math.max(0.0, (double)(var5 * var5) - var12 * var12));
            }

            var0.fill(var1 + var8, var2 + var7, var1 + var8 + 1, var2 + var7 + 1, var6);
            var0.fill(var1 + var3 - var8 - 1, var2 + var7, var1 + var3 - var8, var2 + var7 + 1, var6);
         }

         var0.fill(var1 + var5, var2, var1 + var3 - var5, var2 + 1, var6);
         var0.fill(var1 + var5, var2 + var4 - 1, var1 + var3 - var5, var2 + var4, var6);
      }
   }

   public static void text(DrawContext var0, String var1, float var2, float var3, float var4, int var5, boolean var6, int var7) {
      TextRenderer var8 = MinecraftClient.getInstance().textRenderer;
      float var9 = var4 * 2.25F;
      float var10 = (float)var8.getWidth(var1) * var9;
      float var11 = var7 == 1 ? -var10 / 2.0F : (var7 == 2 ? -var10 : 0.0F);
      var0.getMatrices().push();
      var0.getMatrices().translate(var2 + var11, var3, 0.0F);
      var0.getMatrices().scale(var9, var9, 1.0F);
      var0.drawText(var8, var1, 0, 0, var5, var6);
      var0.getMatrices().pop();
   }

   public static void syncRcRange(float var0) {
      sliders[3][6] = var0;
   }

   static {
      toggles[0][3] = true;
      toggles[1][4] = true;
      toggles[3][2] = true;
      segments[0][2] = 1;
      sliders[0][0] = 120.0F;
      sliders[0][1] = 120.0F;
      sliders[1][0] = 60.0F;
      sliders[1][1] = 50.0F;
      sliders[3][4] = 70.0F;
      sliders[3][5] = 100.0F;
      sliders[3][6] = 10000.0F;
      sliders[3][7] = 1000.0F;
      sliders[4][0] = 100.0F;

      for (long var3 : new long[]{20898L, 31793L, 11908L, 25520L, 6599L, 16601L}) {
         gallery.add(var3);
      }

      SETTING_TABS = new String[]{"安全", "操控", "拍摄", "图传", "声音", "关于"};
      SPAN = new double[4];
   }

   public interface Bridge {
      void fill(Rc2FlightUi.Telemetry var1);

      void exit();

      void takeoffOrLand();

      void returnHome();

      void emergencyStop();

      void setMode(int var1);

      void setZoom(float var1);

      void setFpvGimbal(boolean var1);

      String modeDescription(int var1);
   }

   private static final class Hit {
      float x;
      float y;
      float w;
      float h;
      boolean circle;
      Runnable click;
      Runnable press;
      DoubleConsumer drag;
   }

   private interface IntSetter {
      void set(int var1);
   }

   private static final class Scaled implements DjiGfx {
      final DjiGfx d;

      Scaled(DjiGfx var1) {
         this.d = var1;
      }

      @Override
      public float scale() {
         return this.d.scale();
      }

      @Override
      public void fillPx(int var1, int var2, int var3, int var4, int var5) {
         this.d.fillPx(var1, var2, var3, var4, var5);
      }

      @Override
      public void gradientPx(int var1, int var2, int var3, int var4, int var5, int var6) {
         this.d.gradientPx(var1, var2, var3, var4, var5, var6);
      }

      @Override
      public void batch(Runnable var1) {
         this.d.batch(var1);
      }

      @Override
      public void icon(int var1, float var2, float var3, float var4, float var5, int var6) {
         this.d.icon(var1, var2, var3, var4, var5, var6);
      }

      @Override
      public float text(String var1, float var2, float var3, float var4, int var5, boolean var6, boolean var7) {
         float var8 = Rc2FlightUi.textK();
         return this.d.text(var1, var2, var3 - var4 * (var8 - 1.0F) / 2.0F, var4 * var8, var5, var6, var7);
      }

      @Override
      public float textWidth(String var1, float var2, boolean var3) {
         return this.d.textWidth(var1, var2 * Rc2FlightUi.textK(), var3);
      }

      @Override
      public void pushClip(float var1, float var2, float var3, float var4) {
         this.d.pushClip(var1, var2, var3, var4);
      }

      @Override
      public void popClip() {
         this.d.popClip();
      }

      @Override
      public boolean mapTiles(float var1, float var2, float var3, float var4, double var5, double var7, double var9) {
         return this.d.mapTiles(var1, var2, var3, var4, var5, var7, var9);
      }

      @Override
      public boolean feed(float var1, float var2, float var3, float var4) {
         return this.d.feed(var1, var2, var3, var4);
      }

      @Override
      public boolean assist(float var1, float var2, float var3, float var4) {
         return this.d.assist(var1, var2, var3, var4);
      }
   }

   private interface Spans {
      int at(double var1, double[] var3);
   }

   private static record Status(String text, int kind) {
   }

   public static final class Telemetry {
      public boolean connected;
      public boolean telemetry;
      public boolean accident;
      public boolean paired;
      public boolean feedReady;
      public boolean feedFailed;
      public boolean armed;
      public boolean returning;
      public boolean obstacleWarn;
      public boolean avoidanceOff;
      public boolean fpvGimbal;
      public boolean pilotKnown;
      public boolean m400;
      public int mode = 1;
      public int battery = 99;
      public int remainingSec = 1800;
      public int rcBars;
      public int fourGBars;
      public int satellites;
      public int autoReturnPct = 25;
      public int forcedLandPct = 8;
      public int returnPct;
      public int returnSec;
      public int autoReturnSec;
      public int forcedLandSec;
      public double altitude;
      public double homeDistance;
      public double vSpeed;
      public double hSpeed;
      public double yaw;
      public double velX;
      public double velZ;
      public double droneX;
      public double droneY;
      public double droneZ;
      public double pilotX;
      public double pilotY;
      public double pilotZ;
      public double pilotYaw;
      public double homeX;
      public double homeZ;
      public float zoom = 1.0F;
      public float obstacleRange = 20.0F;
      public final float[] obstacle = new float[]{99.0F, 99.0F, 99.0F, 99.0F};
      public float obstacleUp = 99.0F;
      public float obstacleDown = 99.0F;
      public String state = "未连接";
      public String model = "--";
      public String keyHint = "";
      public final List<double[]> stations = new ArrayList<>();
   }
}
