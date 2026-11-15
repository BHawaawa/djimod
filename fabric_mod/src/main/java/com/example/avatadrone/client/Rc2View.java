package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.DroneAttitude;
import com.example.avatadrone.client.DroneChunkClient;
import com.example.avatadrone.client.DroneKeySetup;
import com.example.avatadrone.client.DroneKeys;
import com.example.avatadrone.client.McDjiGfx;
import com.example.avatadrone.client.Rc2FlightUi;
import com.example.avatadrone.client.Rc2Indicators;
import com.example.avatadrone.client.RcnSerialInput;
import com.example.avatadrone.client.VoiceAnnouncer;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.entity.M400Entity;
import com.example.avatadrone.entity.Mini4ProEntity;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.mixin.client.CameraAccessor;
import com.example.avatadrone.mixin.client.GameRendererAccessor;
import com.example.avatadrone.mixin.client.MinecraftClientAccessor;
import com.example.avatadrone.network.DroneControlNetworking;
import com.example.avatadrone.network.DroneControlPayload;
import com.example.avatadrone.network.DroneSettingsNetworking;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rc2View {
    private static final Logger LOGGER = LoggerFactory.getLogger("djimod/rc2");
    public static final float CANVAS_W = 1280.0f;
    public static final float CANVAS_H = 900.0f;
    public static final float CONTROLLER_W = 1320.0f;
    public static final float CONTROLLER_H = 1221.0f;
    public static final float CONTROLLER_X = -20.0f;
    public static final float CONTROLLER_Y = -206.25f;
    public static final float SCREEN_X = 92.75f;
    public static final float SCREEN_Y = 299.75f;
    public static final float SCREEN_W = 1094.5f;
    public static final float SCREEN_H = 580.25f;
    public static final float LEFT_STICK_X = 233.0f;
    public static final float RIGHT_STICK_X = 1047.0f;
    public static final float STICK_Y = 129.25f;
    public static final Identifier FRONT = new Identifier("djimod", "textures/gui/rc2_front.png");
    public static final Identifier FRONT_SHADOW = new Identifier("djimod", "textures/gui/rc2_front_shadow.png");
    public static final Identifier STICK = new Identifier("djimod", "textures/gui/rc2_stick.png");
    private static Vec3d feedPosition = Vec3d.ZERO;
    private static float feedYaw;
    private static float feedPitch;
    private static float feedRoll;
    private static float feedVerticalFov;
    private static float feedViewDistance;
    private static Framebuffer feed;
    private static AvataDroneEntity drone;
    private static UUID pairedId;
    private static UUID equippedPairId;
    private static long nextFrame;
    private static boolean ready;
    private static long lastFrameAt;
    private static boolean failed;
    private static long nextControl;
    private static boolean showingControls;
    private static boolean registered;
    private static volatile boolean flightView;
    private static boolean wasReturnHeld;
    private static long returnPressedAt;
    private static boolean emergencyStop;
    private static long brakeUntil;
    private static long horizontalLockUntil;
    private static boolean armed;
    private static long armLockUntil;
    private static float obstacleUp;
    private static float obstacleDown;
    private static final float[] obstacleDirections;
    private static int groundDistance;
    private static long armPressedAt;
    private static int flightMode;
    private static int lastModeKey;
    private static boolean cruise;
    private static boolean cruiseHeld;
    private static boolean cruiseStickActive;
    private static boolean cruiseStickTouched;
    private static boolean modeCycleHeld;
    private static long nextGimbalUpAt;
    private static long nextGimbalDownAt;
    private static boolean gimbalMaxHeld;
    private static final float GIMBAL_STEP_DEGREES = 5.0f;
    private static final long GIMBAL_REPEAT_NANOS = 150000000L;
    private static long physicalReturnPressed;
    private static boolean physicalReturnFired;
    private static boolean draggingMode;
    private static long flightStartedAt;
    private static int cachedRemainingSec;
    private static long nextRemainingRefreshAt;
    private static int draggedStick;
    private static float leftStickX;
    private static float leftStickY;
    private static float rightStickX;
    private static float rightStickY;
    private static boolean clickArmRequested;
    private static boolean autoTakeoff;
    private static boolean autoLand;
    private static boolean clickReturnHome;
    private static boolean clickEmergencyStop;
    private static boolean dismissed;
    private static boolean controllerEquipped;
    private static boolean ownsCursor;
    private static float cameraZoom;
    private static boolean screenOnly;
    private static boolean enterHeld;
    private static long lastEnterTapAt;
    private static final Map<UUID, CameraControls> cameraControls;
    private static boolean fpvGimbal;
    private static final int GAMEPAD = 0;
    private static final float GAMEPAD_DEADZONE = 0.12f;
    private static boolean gamepadPresent;
    private static boolean gamepadA;
    private static boolean gamepadB;
    private static boolean gamepadX;
    private static boolean gamepadY;
    private static long gamepadReturnPressed;
    private static boolean gamepadReturnFired;
    private static boolean rcnPhoto;
    private static boolean rcnReturn;
    private static boolean rcnMode;
    private static boolean rcnFn;
    private static int rcnFlightMode = -1;
    private static long nextRcnGimbalAt;
    private static UUID capturedDroneId;
    private static float stickAmount;
    private static long entryTime;
    private static UUID pendingPairId;
    private static long pendingPairUntil;
    private static long lastEnterHint;
    private static boolean batteryAutoRthDone;
    private static boolean batteryForceLandDone;
    private static float visLeftX;
    private static float visLeftY;
    private static float visRightX;
    private static float visRightY;
    private static float canvasScale;
    private static float canvasOffsetX;
    private static float canvasOffsetY;
    private static double mouseCanvasX;
    private static double mouseCanvasY;
    private static int feedTargetId;
    private static int feedWidth;
    private static int feedHeight;
    public static final int ASSIST_FRONT = 0;
    public static final int ASSIST_BACK = 1;
    public static final int ASSIST_LEFT = 2;
    public static final int ASSIST_RIGHT = 3;
    public static final int ASSIST_UP = 4;
    public static final int ASSIST_DOWN = 5;
    private static final int ASSIST_WIDTH = 640;
    private static final int ASSIST_HEIGHT = 360;
    private static final float ASSIST_VIEW_DISTANCE = 192.0f;
    private static final long ASSIST_FRAME_NANOS = 120000000L;
    private static final long ASSIST_MANUAL_NANOS = 2500000000L;
    private static int assistDir;
    private static boolean assistLocked;
    private static boolean assistAuto;
    private static long assistManualUntil;
    private static long nextAssistFrame;
    private static float assistYaw;
    private static float assistPitch;
    private static Framebuffer assistFeed;
    private static boolean assistReady;
    private static boolean assistPass;
    private static UUID obstacleTarget;
    private static long nextObstacleScan;
    private static BlockPos lastGroundBlock;
    private static long nextGroundScan;
    private static final long SENSOR_REFRESH_NANOS = 100000000L;
    private static final long GROUND_REFRESH_NANOS = 250000000L;
    private static final long MAX_FEED_FRAME_NANOS = 16666666L;

    public static double rcRange() {
        return DroneSettingsNetworking.clientRange;
    }

    private Rc2View() {
    }

    public static void registerControls() {
        if (!registered) {
            registered = true;
            ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){

                @Override
                public Identifier getFabricId() {
                    return new Identifier("djimod", "flight_ui_cache");
                }

                public void reload(ResourceManager resourceManager) {
                    McDjiGfx.clearCaches();
                }
            });
            DroneKeys.register();
            ClientTickEvents.END_CLIENT_TICK.register(minecraftClient -> {
                DroneKeys.loadBindings();
                Rc2View.pollGamepad(minecraftClient);
                DroneKeySetup.promptIfNeeded(minecraftClient);
                DroneKeySetup.tick(minecraftClient);
                Rc2View.suppressPlayerInput(minecraftClient);
                Rc2View.updateCursorSession(minecraftClient);
                Rc2View.updatePendingPair(minecraftClient);
                if (Rc2View.isControllerActive()) {
                    int n = Rc2View.controlHeld() && Rc2View.key(67) > 0.0f ? 67 : (Rc2View.controlHeld() && Rc2View.key(78) > 0.0f ? 78 : (Rc2View.controlHeld() && Rc2View.value(DroneKeys.SPORT) > 0.0f ? 83 : 0));
                    if (n != 0 && n != lastModeKey) {
                        Rc2View.setFlightMode(n == 67 ? 0 : (n == 78 ? 1 : 2));
                    }
                    lastModeKey = n;
                    Rc2View.tickModeCycleKey();
                    Rc2View.tickGimbalKeys();
                    Rc2View.tickCruiseKey();
                    Rc2View.tickAssistAuto();
                    Rc2View.updateObstacleDirections();
                    if (physicalReturnPressed != 0L && !physicalReturnFired && System.nanoTime() - physicalReturnPressed >= 500000000L) {
                        physicalReturnFired = true;
                        Rc2View.requestReturnHome();
                    }
                } else {
                    lastModeKey = 0;
                    gimbalMaxHeld = false;
                    modeCycleHeld = false;
                    nextGimbalDownAt = 0L;
                    nextGimbalUpAt = 0L;
                    physicalReturnPressed = 0L;
                    physicalReturnFired = false;
                    draggingMode = false;
                }
                if (minecraftClient.player != null && minecraftClient.currentScreen == null && !minecraftClient.player.isSpectator() && minecraftClient.player.isAlive()) {
                    ItemStack itemStack;
                    if (!Rc2View.heldController().isEmpty() && !showingControls) {
                        minecraftClient.player.sendMessage(Text.translatable("message.djimod.rc2.connection_help", DroneKeys.label(DroneKeys.ENTER), DroneKeys.label(DroneKeys.ENTER_ALT), DroneKeys.label(DroneKeys.DISCONNECT)), true);
                        showingControls = true;
                    } else if (Rc2View.heldController().isEmpty()) {
                        showingControls = false;
                    }
                    if (!Rc2View.heldController().isEmpty() && (itemStack = Rc2View.heldController()).hasNbt() && itemStack.getNbt().containsUuid("DroneUuid")) {
                        Rc2View.findDrone(itemStack);
                    }
                    Rc2View.updateBatteryProtection(minecraftClient);
                    if (!Rc2View.isVisible()) {
                        if (dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null) {
                            Rc2View.sendControl(minecraftClient);
                        }
                    } else {
                        Rc2View.updateGroundDistance(minecraftClient);
                        Rc2FlightUi.tickTrail();
                        Rc2View.sendControl(minecraftClient);
                    }
                }
            });
        }
    }

    private static void pollGamepad(MinecraftClient minecraftClient) {
        RcnSerialInput.Sample sample;
        RcnSerialInput.Sample sample2 = sample = Rc2View.isControllerActive() ? RcnSerialInput.sample() : null;
        if (sample != null) {
            gamepadPresent = true;
            leftStickX = sample.leftX();
            leftStickY = sample.leftY();
            rightStickX = sample.rightX();
            rightStickY = sample.rightY();
            Rc2View.pollRcnControls(sample);
        } else if (minecraftClient.getWindow() != null && minecraftClient.isWindowFocused() && GLFW.glfwJoystickPresent(0)) {
            Rc2View.resetRcnControls();
            gamepadPresent = true;
            FloatBuffer floatBuffer = GLFW.glfwGetJoystickAxes(0);
            ByteBuffer byteBuffer = GLFW.glfwGetJoystickButtons(0);
            if (floatBuffer != null && floatBuffer.limit() >= 4) {
                leftStickX = Rc2View.axis(floatBuffer.get(0));
                leftStickY = -Rc2View.axis(floatBuffer.get(1));
                rightStickX = Rc2View.axis(floatBuffer.get(2));
                rightStickY = -Rc2View.axis(floatBuffer.get(3));
                boolean bl = Rc2View.button(byteBuffer, 0);
                boolean bl2 = Rc2View.button(byteBuffer, 1);
                boolean bl3 = Rc2View.button(byteBuffer, 2);
                boolean bl4 = Rc2View.button(byteBuffer, 3);
                if (bl && !gamepadA && dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null) {
                    Rc2View.requestEnter(minecraftClient);
                }
                if (bl2 && !gamepadB && Rc2View.isControllerActive()) {
                    Rc2View.onEscape();
                }
                if (bl3 && !gamepadX && Rc2View.isControllerActive()) {
                    clickArmRequested = true;
                }
                if (bl4 && !gamepadY && Rc2View.isControllerActive()) {
                    gamepadReturnPressed = System.nanoTime();
                }
                if (!bl4 && gamepadY && gamepadReturnPressed != 0L) {
                    if (!gamepadReturnFired && System.nanoTime() - gamepadReturnPressed < 500000000L) {
                        Rc2View.requestEmergencyStop();
                    }
                    gamepadReturnPressed = 0L;
                    gamepadReturnFired = false;
                }
                if (bl4 && gamepadReturnPressed != 0L && !gamepadReturnFired && System.nanoTime() - gamepadReturnPressed >= 500000000L) {
                    gamepadReturnFired = true;
                    Rc2View.requestReturnHome();
                }
                gamepadA = bl;
                gamepadB = bl2;
                gamepadX = bl3;
                gamepadY = bl4;
            } else {
                rightStickY = 0.0f;
                rightStickX = 0.0f;
                leftStickY = 0.0f;
                leftStickX = 0.0f;
            }
        } else {
            Rc2View.resetRcnControls();
            gamepadPresent = false;
            gamepadY = false;
            gamepadX = false;
            gamepadB = false;
            gamepadA = false;
            gamepadReturnPressed = 0L;
            gamepadReturnFired = false;
            rightStickY = 0.0f;
            rightStickX = 0.0f;
            leftStickY = 0.0f;
            leftStickX = 0.0f;
        }
    }

    private static void pollRcnControls(RcnSerialInput.Sample sample) {
        long now = System.nanoTime();
        if (sample.modeSwitch() && !rcnMode && isControllerActive()) {
            if (drone != null && drone.areMotorsArmed()) {
                cruise = !cruise;
                cruiseStickActive = false;
                cruiseStickTouched = false;
                Rc2FlightUi.toast(cruise ? "定速巡航已开启" : "定速巡航已关闭");
            } else {
                Rc2FlightUi.toast("请先起飞再开启定速巡航");
            }
        }
        if (sample.returnHome() && !rcnReturn && isControllerActive()) {
            gamepadReturnPressed = now;
            gamepadReturnFired = false;
        } else if (!sample.returnHome() && rcnReturn && gamepadReturnPressed != 0L) {
            if (!gamepadReturnFired && now - gamepadReturnPressed < 500000000L) requestEmergencyStop();
            gamepadReturnPressed = 0L;
            gamepadReturnFired = false;
        }
        if (sample.returnHome() && gamepadReturnPressed != 0L && !gamepadReturnFired
                && now - gamepadReturnPressed >= 500000000L) {
            gamepadReturnFired = true;
            requestReturnHome();
        }
        if (sample.fn() && !rcnFn && isControllerActive()) toggleGimbalDown();
        if (sample.flightMode() >= 0 && sample.flightMode() != rcnFlightMode && isControllerActive()) {
            rcnFlightMode = sample.flightMode();
            setFlightMode(sample.flightMode());
        }
        if (isControllerActive() && Math.abs(sample.wheel()) > 0.2f && now >= nextRcnGimbalAt) {
            nextRcnGimbalAt = now + GIMBAL_REPEAT_NANOS;
            moveGimbal(sample.wheel() > 0 ? 5.0f : -5.0f);
        }
        rcnPhoto = sample.photo();
        rcnReturn = sample.returnHome();
        rcnMode = sample.modeSwitch();
        rcnFn = sample.fn();
    }

    private static void resetRcnControls() {
        rcnPhoto = false;
        rcnReturn = false;
        rcnMode = false;
        rcnFn = false;
        rcnFlightMode = -1;
        nextRcnGimbalAt = 0L;
        gamepadReturnPressed = 0L;
        gamepadReturnFired = false;
    }

    private static float axis(float f) {
        if (Math.abs(f) <= 0.12f) {
            return 0.0f;
        }
        float f2 = Math.signum(f);
        return f2 * (Math.abs(f) - 0.12f) / 0.88f;
    }

    private static boolean button(ByteBuffer byteBuffer, int n) {
        return byteBuffer != null && byteBuffer.limit() > n && byteBuffer.get(n) != 0;
    }

    public static boolean onMouseScroll(double d) {
        if (!Rc2View.isVisible()) {
            return false;
        }
        if (d == 0.0) {
            return true;
        }
        KeyBinding keyBinding = null;
        for (KeyBinding keyBinding2 : new KeyBinding[]{DroneKeys.GIMBAL, DroneKeys.ZOOM, DroneKeys.FOV, DroneKeys.ROLL}) {
            if (!DroneKeys.held(keyBinding2)) continue;
            keyBinding = keyBinding2;
            break;
        }
        return Rc2View.adjustCameraScroll(keyBinding, d);
    }

    private static boolean adjustCameraScroll(KeyBinding keyBinding, double d) {
        if (drone != null && !drone.hasAccident()) {
            boolean bl;
            CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(drone.getUuid(), uUID -> new CameraControls());
            boolean bl2 = bl = !(drone instanceof Mini4ProEntity) && !(drone instanceof M400Entity);
            if (keyBinding == DroneKeys.GIMBAL) {
                cameraControls.pitch = MathHelper.clamp(drone.getCameraPitch(1.0f) + cameraControls.pitch - (float)d * 3.0f, -90.0f, 90.0f) - drone.getCameraPitch(1.0f);
                Rc2FlightUi.toast("云台俯仰 " + Math.round(-MathHelper.clamp(drone.getCameraPitch(1.0f) + cameraControls.pitch, -90.0f, 90.0f)) + "°");
            } else if (keyBinding == DroneKeys.ZOOM) {
                Rc2View.setCameraZoom(cameraZoom * (float)Math.pow(1.1, d));
                Rc2FlightUi.toast(String.format(Locale.ROOT, "缩放 %.1fx", Float.valueOf(cameraZoom)));
            } else if (keyBinding == DroneKeys.FOV) {
                if (bl) {
                    cameraControls.fov = MathHelper.clamp(cameraControls.fov + (float)d * 3.0f, 30.0f, 120.0f);
                    Rc2FlightUi.toast("视角广度 " + Math.round(cameraControls.fov) + "°");
                } else {
                    Rc2FlightUi.toast("视角广度调整仅支持 Avata 360");
                }
            } else {
                if (keyBinding != DroneKeys.ROLL) {
                    return Rc2View.adjustStickAmount(d);
                }
                if (bl) {
                    cameraControls.roll = MathHelper.wrapDegrees(cameraControls.roll + (float)d * 3.0f);
                    Rc2FlightUi.toast("画面横滚 " + Math.round(cameraControls.roll) + "°");
                } else {
                    Rc2FlightUi.toast("画面横滚调整仅支持 Avata 360");
                }
            }
            nextFrame = 0L;
            return true;
        }
        return Rc2View.adjustStickAmount(d);
    }

    private static boolean adjustStickAmount(double d) {
        stickAmount = MathHelper.clamp(stickAmount + (float)Math.signum(d) * 0.05f, 0.1f, 1.0f);
        Rc2FlightUi.toast("杆量 " + Math.round(stickAmount * 100.0f) + "%");
        return true;
    }

    public static boolean disconnectFromKey() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null) {
            ItemStack itemStack = Rc2View.heldController();
            if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid")) {
                PacketByteBuf packetByteBuf = PacketByteBufs.create();
                packetByteBuf.writeUuid(itemStack.getNbt().getUuid("DroneUuid"));
                ClientPlayNetworking.send(DroneControlNetworking.DISCONNECT, packetByteBuf);
            }
            Rc2View.clearMouseInput();
            return true;
        }
        return false;
    }

    public static boolean onMouseButton(int n, int n2, double d, double d2) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (!Rc2View.controllerAvailable() || minecraftClient.currentScreen != null || !DroneKeys.ENTER.matchesMouse(n) && !DroneKeys.ENTER_ALT.matchesMouse(n)) {
            if (dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null && minecraftClient.isWindowFocused() && DroneKeys.DISCONNECT.matchesMouse(n)) {
                if (n2 == 1) {
                    ItemStack itemStack = Rc2View.heldController();
                    if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid")) {
                        PacketByteBuf packetByteBuf = PacketByteBufs.create();
                        packetByteBuf.writeUuid(itemStack.getNbt().getUuid("DroneUuid"));
                        ClientPlayNetworking.send(DroneControlNetworking.DISCONNECT, packetByteBuf);
                    }
                    Rc2View.clearMouseInput();
                }
                return true;
            }
            if (!Rc2View.isControllerActive()) {
                return false;
            }
            if (DroneKeys.EXIT.matchesMouse(n)) {
                if (n2 == 1) {
                    Rc2View.onEscape();
                }
                return true;
            }
            if (DroneKeys.flightMouse(n)) {
                return true;
            }
            double[] dArray = Rc2View.toCanvasPoint(d, d2);
            double[] dArray2 = Rc2View.toDesignPoint(d, d2);
            if (n2 == 0) {
                if (n == 0) {
                    if (physicalReturnPressed != 0L) {
                        if (!physicalReturnFired) {
                            if (System.nanoTime() - physicalReturnPressed >= 500000000L) {
                                Rc2View.requestReturnHome();
                            } else {
                                Rc2View.requestEmergencyStop();
                            }
                        }
                        physicalReturnPressed = 0L;
                        physicalReturnFired = false;
                        return true;
                    }
                    if (draggingMode) {
                        draggingMode = false;
                        return true;
                    }
                    if (draggedStick >= 0) {
                        Rc2View.endStickDrag();
                    } else {
                        Rc2FlightUi.mouseReleased(dArray2[0], dArray2[1], n);
                    }
                }
                return true;
            }
            if (n2 != 1) {
                return true;
            }
            if (n != 0) {
                return true;
            }
            if (screenOnly) {
                Rc2FlightUi.mousePressed(dArray2[0], dArray2[1], n);
                return true;
            }
            if (dArray[0] >= 570.0 && dArray[0] <= 710.0 && dArray[1] >= 102.0 && dArray[1] <= 226.0) {
                draggingMode = true;
                Rc2View.selectPhysicalMode(dArray[0]);
                return true;
            }
            if (Math.hypot(dArray[0] - 480.5, dArray[1] - 204.4) <= 43.0) {
                physicalReturnPressed = System.nanoTime();
                physicalReturnFired = false;
                return true;
            }
            if (Rc2View.beginStickDrag(dArray[0], dArray[1])) {
                return true;
            }
            Rc2FlightUi.mousePressed(dArray2[0], dArray2[1], n);
            return true;
        }
        if (n2 == 1 && dismissed) {
            Rc2View.requestEnter(minecraftClient);
        }
        return true;
    }

    public static void requestEnterFromKey() {
        Rc2View.requestEnter(MinecraftClient.getInstance());
    }

    public static boolean canEnterFromKey() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null;
    }

    private static void requestEnter(MinecraftClient minecraftClient) {
        if (minecraftClient.player != null && Rc2View.controllerAvailable() && dismissed) {
            ItemStack itemStack = Rc2View.heldController();
            if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid")) {
                Rc2View.enterFlightView(minecraftClient, itemStack);
            } else {
                AvataDroneEntity avataDroneEntity = Rc2View.nearestDrone(minecraftClient, Rc2View.rcRange());
                if (avataDroneEntity == null) {
                    long l = System.nanoTime();
                    if (l - lastEnterHint > 3000000000L) {
                        lastEnterHint = l;
                        minecraftClient.player.sendMessage(Text.translatable("message.djimod.rc2.no_drone_near"), false);
                    }
                } else {
                    pendingPairId = avataDroneEntity.getUuid();
                    pendingPairUntil = System.nanoTime() + 4000000000L;
                    PacketByteBuf packetByteBuf = PacketByteBufs.create();
                    packetByteBuf.writeUuid(pendingPairId);
                    ClientPlayNetworking.send(DroneControlNetworking.PAIR, packetByteBuf);
                    minecraftClient.player.sendMessage(Text.translatable("message.djimod.rc2.pairing"), true);
                }
            }
        }
    }

    private static void updatePendingPair(MinecraftClient minecraftClient) {
        if (pendingPairId != null) {
            if (minecraftClient.player != null && Rc2View.controllerAvailable()) {
                ItemStack itemStack = Rc2View.heldController();
                if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid") && pendingPairId.equals(itemStack.getNbt().getUuid("DroneUuid"))) {
                    UUID uUID = pendingPairId;
                    pendingPairId = null;
                    Rc2View.enterFlightView(minecraftClient, itemStack);
                    LOGGER.info("RC 2 paired and entering flight view for {}", (Object)uUID);
                } else if (System.nanoTime() > pendingPairUntil) {
                    pendingPairId = null;
                    minecraftClient.player.sendMessage(Text.translatable("message.djimod.rc2.pair_failed"), false);
                }
            } else {
                pendingPairId = null;
            }
        }
    }

    private static void enterFlightView(MinecraftClient minecraftClient, ItemStack itemStack) {
        screenOnly = false;
        enterHeld = false;
        lastEnterTapAt = 0L;
        AvataDroneEntity avataDroneEntity = Rc2View.findDrone(itemStack);
        if (avataDroneEntity != null) {
            flightMode = avataDroneEntity.getFlightMode();
        }
        if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid")) {
            dismissed = false;
            controllerEquipped = true;
            equippedPairId = itemStack.getNbt().getUuid("DroneUuid");
            entryTime = System.nanoTime();
            Rc2FlightUi.onEntry();
            Rc2View.updateCursorSession(minecraftClient);
            LOGGER.info("RC 2 flight view opened for {}", (Object)equippedPairId);
        }
    }

    private static AvataDroneEntity nearestDrone(MinecraftClient minecraftClient, double d) {
        if (minecraftClient.world != null && minecraftClient.player != null) {
            AvataDroneEntity avataDroneEntity = null;
            double d2 = d * d;
            for (Entity entity : minecraftClient.world.getEntities()) {
                double d3;
                AvataDroneEntity avataDroneEntity2;
                if (!(entity instanceof AvataDroneEntity) || !(avataDroneEntity2 = (AvataDroneEntity)entity).isAlive() || avataDroneEntity2.isRemoved() || !((d3 = avataDroneEntity2.squaredDistanceTo(minecraftClient.player)) <= d2)) continue;
                d2 = d3;
                avataDroneEntity = avataDroneEntity2;
            }
            return avataDroneEntity;
        }
        return null;
    }

    public static void onMouseMove(double d, double d2) {
        double[] dArray = Rc2View.toCanvasPoint(d, d2);
        mouseCanvasX = dArray[0];
        mouseCanvasY = dArray[1];
        if (draggingMode && Rc2View.isControllerActive()) {
            Rc2View.selectPhysicalMode(dArray[0]);
        }
        if (draggedStick >= 0 && Rc2View.isControllerActive()) {
            Rc2View.updateStickFromCanvas(dArray[0], dArray[1]);
        }
    }

    private static boolean beginStickDrag(double d, double d2) {
        if (!Rc2View.isControllerActive()) {
            return false;
        }
        if (Math.hypot(d - 233.0, d2 - 129.25) <= 62.0) {
            draggedStick = 0;
            Rc2View.updateStickFromCanvas(d, d2);
            return true;
        }
        if (Math.hypot(d - 1047.0, d2 - 129.25) <= 62.0) {
            draggedStick = 1;
            Rc2View.updateStickFromCanvas(d, d2);
            return true;
        }
        return false;
    }

    private static void updateStickFromCanvas(double d, double d2) {
        double d3;
        double d4 = draggedStick == 0 ? 233.0 : 1047.0;
        double d5 = (d - d4) / 62.0;
        double d6 = Math.sqrt(d5 * d5 + (d3 = (129.25 - d2) / 62.0) * d3);
        if (d6 > 1.0) {
            d5 /= d6;
            d3 /= d6;
        }
        if (draggedStick == 0) {
            leftStickX = (float)d5;
            leftStickY = (float)d3;
        } else {
            rightStickX = (float)d5;
            rightStickY = (float)d3;
        }
    }

    private static void endStickDrag() {
        draggedStick = -1;
        physicalReturnPressed = 0L;
        physicalReturnFired = false;
        draggingMode = false;
        rightStickY = 0.0f;
        rightStickX = 0.0f;
        leftStickY = 0.0f;
        leftStickX = 0.0f;
    }

    public static boolean onEscape() {
        if (!Rc2View.isControllerActive()) {
            return false;
        }
        if (Rc2FlightUi.closeTopPanel()) {
            return true;
        }
        dismissed = true;
        flightView = false;
        Rc2View.clearMouseInput();
        Rc2View.releaseCursor(MinecraftClient.getInstance());
        Rc2FlightUi.onExit();
        nextControl = 0L;
        Rc2View.sendControl(MinecraftClient.getInstance());
        return true;
    }

    public static boolean isFlightView() {
        return flightView;
    }

    public static boolean isControllerActive() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return Rc2View.isVisible() && minecraftClient.isWindowFocused();
    }

    private static void suppressPlayerInput(MinecraftClient minecraftClient) {
        if (flightView && minecraftClient.player != null && minecraftClient.player.input != null) {
            Input input = minecraftClient.player.input;
            input.movementSideways = 0.0f;
            input.movementForward = 0.0f;
            input.pressingForward = false;
            input.pressingBack = false;
            input.pressingLeft = false;
            input.pressingRight = false;
            input.jumping = false;
            input.sneaking = false;
        }
    }

    private static boolean controllerAvailable() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return minecraftClient.world != null && minecraftClient.player != null && minecraftClient.player.isAlive() && !minecraftClient.player.isSpectator() && !Rc2View.heldController().isEmpty();
    }

    private static void updateCursorSession(MinecraftClient minecraftClient) {
        boolean bl = Rc2View.controllerAvailable();
        if (!bl) {
            dismissed = true;
            controllerEquipped = false;
            equippedPairId = null;
            showingControls = false;
            Rc2View.clearMouseInput();
            Rc2View.releaseCursor(minecraftClient);
            Rc2FlightUi.onExit();
        } else {
            ItemStack itemStack;
            UUID uUID;
            if (!controllerEquipped) {
                dismissed = true;
                controllerEquipped = true;
            }
            UUID uUID2 = uUID = (itemStack = Rc2View.heldController()).hasNbt() && itemStack.getNbt().containsUuid("DroneUuid") ? itemStack.getNbt().getUuid("DroneUuid") : null;
            if (!Objects.equals(equippedPairId, uUID)) {
                equippedPairId = uUID;
                dismissed = true;
                Rc2View.clearMouseInput();
                Rc2FlightUi.onExit();
            }
            flightView = Rc2View.isVisible();
            if (!Rc2View.isControllerActive()) {
                Rc2View.clearMouseInput();
                if (minecraftClient.currentScreen == null) {
                    Rc2View.releaseCursor(minecraftClient);
                }
            } else {
                if (!ownsCursor) {
                    ownsCursor = true;
                    KeyBinding.unpressAll();
                }
                if (minecraftClient.mouse.isCursorLocked()) {
                    minecraftClient.mouse.unlockCursor();
                }
                if (draggedStick >= 0 && GLFW.glfwGetMouseButton(minecraftClient.getWindow().getHandle(), 0) != 1) {
                    Rc2View.endStickDrag();
                }
            }
        }
    }

    private static void releaseCursor(MinecraftClient minecraftClient) {
        if (ownsCursor) {
            ownsCursor = false;
            if (minecraftClient.currentScreen == null && minecraftClient.player != null && minecraftClient.isWindowFocused()) {
                minecraftClient.mouse.lockCursor();
                KeyBinding.unpressAll();
            }
        }
    }

    private static void clearMouseInput() {
        draggedStick = -1;
        physicalReturnPressed = 0L;
        physicalReturnFired = false;
        draggingMode = false;
        rightStickY = 0.0f;
        rightStickX = 0.0f;
        leftStickY = 0.0f;
        leftStickX = 0.0f;
        clickEmergencyStop = false;
        clickReturnHome = false;
        autoLand = false;
        autoTakeoff = false;
        clickArmRequested = false;
        emergencyStop = false;
        wasReturnHeld = false;
        armPressedAt = 0L;
        armLockUntil = 0L;
    }

    private static void sendControl(MinecraftClient minecraftClient) {
        ItemStack itemStack;
        if (minecraftClient.player != null && minecraftClient.getNetworkHandler() != null && System.nanoTime() >= nextControl && Rc2Item.hasPower(itemStack = Rc2View.heldController()) && itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid") && minecraftClient.world.getRegistryKey().getValue().toString().equals(itemStack.getNbt().getString("DroneDimension"))) {
            boolean bl;
            boolean bl2;
            boolean bl3;
            boolean bl4;
            nextControl = System.nanoTime() + 50000000L;
            if (drone == null || !drone.areMotorsArmed()) {
                cruise = false;
                cruiseStickActive = false;
                cruiseStickTouched = false;
            }
            double d = 0.0;
            double d2 = 0.0;
            double d3 = 0.0;
            double d4 = 0.0;
            if (!DroneKeys.isRemoteMode()) {
                d = Rc2View.key(68) - Rc2View.key(65);
                d2 = Rc2View.key(87) - Rc2View.backwardKey();
                d3 = Rc2View.verticalKeys();
                d4 = Rc2View.key(88) - Rc2View.key(90);
            }
            d = MathHelper.clamp(d + (double)rightStickX, -1.0, 1.0);
            d2 = MathHelper.clamp(d2 + (double)rightStickY, -1.0, 1.0);
            d3 = MathHelper.clamp(d3 + (double)leftStickY, -1.0, 1.0);
            d4 = MathHelper.clamp(d4 + (double)leftStickX, -1.0, 1.0);
            if (!Rc2View.isControllerActive()) {
                d = 0.0;
                d2 = 0.0;
                d3 = 0.0;
                d4 = 0.0;
            }
            boolean bl5 = bl4 = d3 <= -0.6 && d2 <= -0.6 && (d4 <= -0.6 && d >= 0.6 || d4 >= 0.6 && d <= -0.6);
            if (autoTakeoff && drone != null && drone.getGroundDistance() >= 1.2) {
                autoTakeoff = false;
            }
            float f = (float)(d2 * (double)stickAmount);
            float f2 = (float)(d * (double)stickAmount);
            d3 *= (double)stickAmount;
            d4 *= (double)stickAmount;
            if (autoLand && drone != null && !drone.areMotorsArmed()) {
                autoLand = false;
            }
            if (autoLand) {
                d3 = -0.9;
                f2 = 0.0f;
                f = 0.0f;
            }
            if (autoTakeoff && drone != null && drone.areMotorsArmed()) {
                d3 = 0.5;
                f2 = 0.0f;
                f = 0.0f;
            }
            long l = System.nanoTime();
            if (bl4 && armPressedAt == 0L) {
                armPressedAt = l;
            }
            if (!bl4) {
                armPressedAt = 0L;
            }
            if (drone != null && drone.areMotorsArmed()) {
                armed = true;
            }
            if (clickArmRequested && drone != null && drone.areMotorsArmed()) {
                clickArmRequested = false;
            }
            boolean bl6 = bl3 = clickArmRequested || bl4 && l - armPressedAt >= 300000000L && drone != null && !drone.areMotorsArmed();
            if (bl3) {
                armLockUntil = l + 700000000L;
            }
            if (l < armLockUntil) {
                d3 = 0.0;
                f2 = 0.0f;
                f = 0.0f;
                d4 = 0.0;
            }
            if ((bl2 = DroneKeys.held(DroneKeys.RETURN)) && !wasReturnHeld) {
                wasReturnHeld = true;
                returnPressedAt = System.nanoTime();
            }
            if (!bl2 && wasReturnHeld) {
                long l2 = System.nanoTime() - returnPressedAt;
                wasReturnHeld = false;
                if (l2 < 500000000L) {
                    emergencyStop = true;
                }
            }
            boolean bl7 = bl = clickReturnHome || bl2 && System.nanoTime() - returnPressedAt >= 500000000L;
            if (bl) {
                emergencyStop = false;
            }
            if (bl || emergencyStop || clickEmergencyStop) {
                autoLand = false;
                autoTakeoff = false;
                clickArmRequested = false;
                d3 = 0.0;
                f2 = 0.0f;
                f = 0.0f;
            }
            if (Rc2View.isBraking()) {
                d3 = 0.0;
                d4 = 0.0;
                f = 0.0f;
                f2 = 0.0f;
            }
            float f3 = minecraftClient.player.getYaw();
            if (drone != null) {
                f3 = drone.getYaw();
            }
            if (Rc2View.isHorizontalLocked()) {
                f = 0.0f;
                f2 = 0.0f;
                d4 = 0.0;
            }
            if (emergencyStop || clickEmergencyStop) {
                VoiceAnnouncer.cancelAutoReturn();
            }
            if (cruise && (bl || emergencyStop || clickEmergencyStop)) {
                cruise = false;
                cruiseStickActive = false;
                cruiseStickTouched = false;
                Rc2FlightUi.toast("定速巡航已关闭");
            } else if (cruise) {
                boolean bl8;
                cruiseStickActive = bl8 = (double)(Math.abs(f) + Math.abs(f2)) + Math.abs(d3) + Math.abs(d4) > (double)0.15f;
                if (bl8 && !cruiseStickTouched) {
                    cruiseStickTouched = true;
                    Rc2FlightUi.toast("已修改巡航速度");
                } else if (!bl8) {
                    cruiseStickTouched = false;
                }
            } else {
                cruiseStickActive = false;
                cruiseStickTouched = false;
            }
            PacketByteBuf packetByteBuf = PacketByteBufs.create();
            DroneControlPayload.write(packetByteBuf, new DroneControlPayload(itemStack.getNbt().getUuid("DroneUuid"), f3, (float)d3, (float)d4, f, f2, groundDistance, flightMode, bl3, emergencyStop || clickEmergencyStop, bl, cruise, Rc2FlightUi.avoidanceMode()));
            emergencyStop = false;
            clickEmergencyStop = false;
            clickReturnHome = false;
            ClientPlayNetworking.send(DroneControlPayload.ID, packetByteBuf);
        }
    }

    private static boolean controlHeld() {
        return DroneKeys.MODE_MOD.isUnbound() || DroneKeys.held(DroneKeys.MODE_MOD);
    }

    private static void tickModeCycleKey() {
        boolean bl;
        boolean bl2 = bl = Rc2View.value(DroneKeys.MODE_CYCLE) > 0.0f;
        if (bl && !modeCycleHeld) {
            Rc2View.cycleFlightMode();
        }
        modeCycleHeld = bl;
    }

    private static void tickCruiseKey() {
        boolean bl;
        boolean bl2 = bl = Rc2View.value(DroneKeys.CRUISE) > 0.0f;
        if (bl && !cruiseHeld) {
            if (drone == null || !drone.areMotorsArmed()) {
                Rc2FlightUi.toast("请先起飞再开启定速巡航");
            } else if (cruise && cruiseStickActive) {
                Rc2FlightUi.toast("请先松开摇杆，再按 H 退出定速巡航");
            } else {
                cruise = !cruise;
                Rc2FlightUi.toast(cruise ? "定速巡航已开启" : "定速巡航已关闭");
            }
        }
        cruiseHeld = bl;
    }

    public static void cycleFlightMode() {
        Rc2View.setFlightMode((flightMode + 1) % 3);
    }

    private static void tickGimbalKeys() {
        boolean bl;
        long l = System.nanoTime();
        if (Rc2View.value(DroneKeys.GIMBAL_UP) > 0.0f) {
            if (nextGimbalUpAt == 0L || l >= nextGimbalUpAt) {
                nextGimbalUpAt = l + 150000000L;
                Rc2View.moveGimbal(-5.0f);
            }
        } else {
            nextGimbalUpAt = 0L;
        }
        if (Rc2View.value(DroneKeys.GIMBAL_DOWN) > 0.0f) {
            if (nextGimbalDownAt == 0L || l >= nextGimbalDownAt) {
                nextGimbalDownAt = l + 150000000L;
                Rc2View.moveGimbal(5.0f);
            }
        } else {
            nextGimbalDownAt = 0L;
        }
        boolean bl2 = bl = Rc2View.value(DroneKeys.GIMBAL_MAX) > 0.0f;
        if (bl && !gimbalMaxHeld) {
            Rc2View.toggleGimbalDown();
        }
        gimbalMaxHeld = bl;
    }

    private static void toggleGimbalDown() {
        if (drone != null && !drone.hasAccident()) {
            CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(drone.getUuid(), uUID -> new CameraControls());
            float f = drone.getCameraPitch(1.0f);
            float f2 = MathHelper.clamp(f + cameraControls.pitch, -90.0f, 90.0f);
            if (f2 > 45.0f) {
                Rc2View.aimGimbal(0.0f);
            } else {
                Rc2View.aimGimbal(90.0f);
            }
        } else {
            Rc2FlightUi.toast("未连接飞行器");
        }
    }

    public static float obstacleUp() {
        return obstacleUp;
    }

    public static float obstacleDown() {
        return obstacleDown;
    }

    public static float nearestObstacleDistance() {
        float f = 25.0f;
        for (int i = 0; i < 12; ++i) {
            float f2 = obstacleDirections[i];
            if (!(f2 > 0.0f) || !(f2 < f)) continue;
            f = f2;
        }
        if (obstacleUp > 0.0f && obstacleUp < f) {
            f = obstacleUp;
        }
        if (obstacleDown > 0.0f && obstacleDown < f) {
            f = obstacleDown;
        }
        return f;
    }

    public static float nearestHorizontalObstacleDistance() {
        float f = 25.0f;
        for (int i = 0; i < 12; ++i) {
            float f2 = obstacleDirections[i];
            if (!(f2 > 0.0f) || !(f2 < f)) continue;
            f = f2;
        }
        return f;
    }

    public static float obstacleDirection(int n) {
        return n >= 0 && n < 12 ? obstacleDirections[n] : 99.0f;
    }

    private static void updateObstacleDirections() {
        if (drone != null && !drone.hasAccident()) {
            long l = System.nanoTime();
            UUID uUID = drone.getUuid();
            if (uUID.equals(obstacleTarget) && l < nextObstacleScan) {
                return;
            }
            obstacleTarget = uUID;
            nextObstacleScan = l + 33000000L;
            double d = drone.getX();
            double d2 = drone.getY() + (double)drone.getHeight() * 0.55;
            double d3 = drone.getZ();
            double d4 = Math.toRadians(drone.getYaw());
            for (int i = 0; i < 12; ++i) {
                double d5 = d4 + (double)i * Math.PI / 6.0;
                Rc2View.obstacleDirections[i] = Rc2View.obstacleRay(d, d2, d3, -Math.sin(d5), Math.cos(d5), 25.0f);
            }
            double d6 = drone.getY() + 0.6;
            Vec3d vec3d = new Vec3d(d, d6, d3);
            BlockHitResult blockHitResult = drone.getWorld().raycast(new RaycastContext(vec3d, vec3d.add(0.0, 5.0, 0.0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, drone));
            obstacleUp = blockHitResult.getType() == HitResult.Type.MISS ? 5.0f : (float)blockHitResult.getPos().distanceTo(vec3d);
            Vec3d vec3d2 = new Vec3d(d, drone.getY() + 0.05, d3);
            BlockHitResult blockHitResult2 = drone.getWorld().raycast(new RaycastContext(vec3d2, vec3d2.add(0.0, -5.0, 0.0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, drone));
            obstacleDown = blockHitResult2.getType() == HitResult.Type.MISS ? 99.0f : (float)blockHitResult2.getPos().distanceTo(vec3d2);
        } else {
            obstacleTarget = null;
            nextObstacleScan = 0L;
            obstacleUp = 99.0f;
            obstacleDown = 99.0f;
            for (int i = 0; i < 12; ++i) {
                Rc2View.obstacleDirections[i] = 99.0f;
            }
        }
    }

    private static float obstacleRay(double d, double d2, double d3, double d4, double d5, float f) {
        Vec3d vec3d = new Vec3d(d, d2, d3);
        Vec3d vec3d2 = vec3d.add(d4 * (double)f, 0.0, d5 * (double)f);
        BlockHitResult blockHitResult = drone.getWorld().raycast(new RaycastContext(vec3d, vec3d2, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, drone));
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return 99.0f;
        }
        double d6 = blockHitResult.getPos().distanceTo(vec3d);
        return d6 > (double)f ? 99.0f : (float)d6;
    }

    public static int assistDir() {
        return assistDir;
    }

    public static String assistDirName() {
        switch (assistDir) {
            case 1: {
                return "后";
            }
            case 2: {
                return "左";
            }
            case 3: {
                return "右";
            }
            case 4: {
                return "上";
            }
            case 5: {
                return "下";
            }
        }
        return "前";
    }

    public static boolean isBraking() {
        return System.nanoTime() < brakeUntil;
    }

    public static boolean isHorizontalLocked() {
        return System.nanoTime() < horizontalLockUntil;
    }

    public static float brakePitchOffset() {
        long l = horizontalLockUntil - System.nanoTime();
        if (l <= 0L) {
            return 0.0f;
        }
        float f = 1.0f - (float)l / 1.2E9f;
        return f < 0.1f ? 10.0f * (f / 0.1f) : 10.0f * Math.max(0.0f, 1.0f - (f - 0.1f) / 0.9f);
    }

    public static boolean isAssistLocked() {
        return assistLocked;
    }

    public static boolean isAssistAuto() {
        return assistAuto;
    }

    public static boolean isAssistReady() {
        return assistReady && assistFeed != null;
    }

    public static boolean isRenderingAssist() {
        return assistPass;
    }

    public static void selectAssistDir(int n) {
        assistDir = MathHelper.clamp(n, 0, 5);
        assistLocked = false;
        assistManualUntil = System.nanoTime() + 2500000000L;
        nextAssistFrame = 0L;
    }

    public static void lockAssistDir(int n) {
        assistDir = MathHelper.clamp(n, 0, 5);
        assistLocked = true;
        nextAssistFrame = 0L;
        Rc2FlightUi.toast("已锁定辅助视角：" + Rc2View.assistDirName());
    }

    public static void toggleAssistAuto() {
        assistAuto = !assistAuto;
        assistLocked = false;
        Rc2FlightUi.toast(assistAuto ? "辅助视角：自动跟随飞行方向" : "辅助视角：手动选择方向");
    }

    private static void tickAssistAuto() {
        double d;
        double d2;
        if (assistAuto && !assistLocked && drone != null && !drone.hasAccident() && System.nanoTime() >= assistManualUntil && !(Math.hypot(d2 = Rc2View.drone.getVelocity().x, d = Rc2View.drone.getVelocity().z) * 20.0 < 1.5)) {
            double d3 = Math.toRadians(drone.getYaw());
            double d4 = d2 * -Math.sin(d3) + d * Math.cos(d3);
            double d5 = d2 * -Math.cos(d3) + d * -Math.sin(d3);
            int n = Math.abs(d4) >= Math.abs(d5) ? (d4 > 0.0 ? 0 : 1) : (d5 > 0.0 ? 2 : 3);
            if (n != assistDir) {
                assistDir = n;
                nextAssistFrame = 0L;
            }
        }
    }

    public static float[] assistVector() {
        if (drone != null && Rc2View.hasTelemetry()) {
            double d = Rc2View.drone.getVelocity().x;
            double d2 = Rc2View.drone.getVelocity().z;
            double d3 = Math.hypot(d, d2) * 20.0;
            float f = assistDir != 4 && assistDir != 5 ? (assistDir == 1 ? 180.0f : (assistDir == 2 ? -90.0f : (assistDir == 3 ? 90.0f : 0.0f))) : 0.0f;
            double d4 = Math.toRadians(drone.getYaw() + f);
            double d5 = d * -Math.sin(d4) + d2 * Math.cos(d4);
            double d6 = d * -Math.cos(d4) + d2 * -Math.sin(d4);
            return new float[]{0.0f, (float)(-d5), (float)d3};
        }
        return new float[]{0.0f, 0.0f, 0.0f};
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderAssistPass(MinecraftClient minecraftClient, AvataDroneEntity avataDroneEntity, Runnable runnable, float f) {
        long l;
        if (Rc2FlightUi.assistVisible() && (l = System.nanoTime()) >= nextAssistFrame) {
            nextAssistFrame = l + 50000000L;
            if (assistFeed == null) {
                assistFeed = new SimpleFramebuffer(960, 540, true, MinecraftClient.IS_SYSTEM_MAC);
                assistFeed.setTexFilter(9729);
            }
            Framebuffer framebuffer = minecraftClient.getFramebuffer();
            Vec3d vec3d = feedPosition;
            float f2 = feedYaw;
            float f3 = feedPitch;
            float f4 = feedRoll;
            float f5 = feedVerticalFov;
            float f6 = feedViewDistance;
            int n = feedWidth;
            int n2 = feedHeight;
            try {
                float f7;
                float f8 = f7 = avataDroneEntity.getYaw();
                float f9 = 0.0f;
                float f10 = 0.0f;
                if (assistDir == 1) {
                    f8 = f7 + 180.0f;
                } else if (assistDir == 2) {
                    f8 = f7 - 90.0f;
                } else if (assistDir == 3) {
                    f8 = f7 + 90.0f;
                } else if (assistDir == 4) {
                    f9 = -89.5f;
                    f8 = f7 + 180.0f;
                    f10 = 0.0f;
                } else if (assistDir == 5) {
                    f9 = 89.5f;
                    f10 = 0.0f;
                }
                assistYaw = f8;
                assistPitch = f9;
                double d = Math.toRadians(f8);
                feedPosition = avataDroneEntity.getLerpedPos(f).add(-Math.sin(d) * (double)f10, 0.24, Math.cos(d) * (double)f10);
                ((MinecraftClientAccessor)((Object)minecraftClient)).avatadrone$setFramebuffer(assistFeed);
                assistFeed.setClearColor(0.42f, 0.62f, 0.78f, 1.0f);
                assistFeed.clear(MinecraftClient.IS_SYSTEM_MAC);
                assistFeed.beginWrite(true);
                feedYaw = f8;
                feedPitch = f9;
                feedRoll = 0.0f;
                feedVerticalFov = (float)avataDroneEntity.getCameraVerticalFov();
                feedViewDistance = 192.0f;
                feedWidth = 960;
                feedHeight = 540;
                assistPass = true;
                runnable.run();
                assistPass = false;
                assistFeed.beginWrite(true);
                RenderSystem.colorMask(false, false, false, true);
                RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
                RenderSystem.clear(16384, MinecraftClient.IS_SYSTEM_MAC);
                RenderSystem.colorMask(true, true, true, true);
                assistReady = true;
            }
            catch (RuntimeException runtimeException) {
                assistReady = false;
                LOGGER.warn("RC 2 assist view render failed", runtimeException);
            }
            finally {
                assistPass = false;
                RenderSystem.colorMask(true, true, true, true);
                ((MinecraftClientAccessor)((Object)minecraftClient)).avatadrone$setFramebuffer(framebuffer);
                framebuffer.beginWrite(true);
                feedPosition = vec3d;
                feedYaw = f2;
                feedPitch = f3;
                feedRoll = f4;
                feedVerticalFov = f5;
                feedViewDistance = f6;
                feedWidth = n;
                feedHeight = n2;
            }
        }
    }

    public static float gimbalPitch() {
        if (drone != null && !drone.hasAccident()) {
            CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(drone.getUuid(), uUID -> new CameraControls());
            return MathHelper.clamp(drone.getCameraPitch(1.0f) + cameraControls.pitch, -90.0f, 90.0f);
        }
        return Float.NaN;
    }

    private static void moveGimbal(float f) {
        if (drone != null && !drone.hasAccident()) {
            CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(drone.getUuid(), uUID -> new CameraControls());
            float f2 = drone.getCameraPitch(1.0f);
            cameraControls.pitch = MathHelper.clamp(f2 + cameraControls.pitch + f, -90.0f, 90.0f) - f2;
            nextFrame = 0L;
            Rc2FlightUi.toast("云台俯仰 " + Math.round(-MathHelper.clamp(f2 + cameraControls.pitch, -90.0f, 90.0f)) + "°");
        } else {
            Rc2FlightUi.toast("未连接飞行器");
        }
    }

    private static void aimGimbal(float f) {
        if (drone != null && !drone.hasAccident()) {
            CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(drone.getUuid(), uUID -> new CameraControls());
            float f2 = drone.getCameraPitch(1.0f);
            float f3 = MathHelper.clamp(f, -90.0f, 90.0f);
            cameraControls.pitch = f3 - f2;
            nextFrame = 0L;
            Rc2FlightUi.toast("云台俯仰 " + Math.round(-f3) + "°");
        } else {
            Rc2FlightUi.toast("未连接飞行器");
        }
    }

    private static float backwardKey() {
        if (DroneKeys.isRemoteMode()) {
            return 0.0f;
        }
        return Rc2View.controlHeld() && DroneKeys.held(DroneKeys.SPORT) && DroneKeys.BACK.getBoundKeyTranslationKey().equals(DroneKeys.SPORT.getBoundKeyTranslationKey()) ? 0.0f : Rc2View.key(83);
    }

    private static float verticalKeys() {
        if (DroneKeys.isRemoteMode()) {
            return 0.0f;
        }
        return Math.max(Rc2View.value(DroneKeys.UP), Rc2View.value(DroneKeys.UP_ALT)) - Math.max(Rc2View.value(DroneKeys.DOWN), Rc2View.value(DroneKeys.DOWN_ALT));
    }

    private static float value(KeyBinding keyBinding) {
        return Rc2View.isControllerActive() && DroneKeys.held(keyBinding) ? 1.0f : 0.0f;
    }

    /** Consume Enter in the flight view so quick double taps cannot open chat. */
    public static boolean onScreenOnlyKey(int key, int action) {
        if ((key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER) || !isControllerActive()) return false;
        if (action == GLFW.GLFW_PRESS) {
            long now = System.nanoTime();
            if (lastEnterTapAt != 0L && now - lastEnterTapAt <= 400_000_000L) {
                screenOnly = !screenOnly;
                lastEnterTapAt = 0L;
                Rc2FlightUi.toast(screenOnly ? "屏幕放大：已隐藏遥控器摇杆" : "已恢复遥控器界面");
            } else {
                lastEnterTapAt = now;
            }
        }
        return true;
    }

    private static float key(int n) {
        KeyBinding keyBinding = switch (n) {
            case 65 -> DroneKeys.LEFT;
            default -> null;
            case 67 -> DroneKeys.CINE;
            case 68 -> DroneKeys.RIGHT;
            case 70 -> DroneKeys.RETURN;
            case 78 -> DroneKeys.NORMAL;
            case 83 -> DroneKeys.BACK;
            case 87 -> DroneKeys.FORWARD;
            case 88 -> DroneKeys.YAW_RIGHT;
            case 90 -> DroneKeys.YAW_LEFT;
        };
        return keyBinding == null ? 0.0f : Rc2View.value(keyBinding);
    }

    private static void updateGroundDistance(MinecraftClient minecraftClient) {
        if (drone != null && minecraftClient.world != null) {
            long l = System.nanoTime();
            BlockPos blockPos = BlockPos.ofFloored(drone.getX(), drone.getY(), drone.getZ());
            if (blockPos.equals(lastGroundBlock) && l < nextGroundScan) {
                return;
            }
            lastGroundBlock = blockPos;
            nextGroundScan = l + 250000000L;
            groundDistance = 512;
            BlockPos.Mutable mutable = blockPos.mutableCopy();
            int n = minecraftClient.world.getBottomY();
            while (mutable.getY() > n && minecraftClient.world.getBlockState(mutable).isAir()) {
                mutable.move(0, -1, 0);
            }
            groundDistance = Math.max(0, MathHelper.floor(drone.getY() - (double)mutable.getY() - 1.0));
        } else {
            lastGroundBlock = null;
            nextGroundScan = 0L;
            groundDistance = 512;
        }
    }

    public static ItemStack heldController() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient.player == null) {
            return ItemStack.EMPTY;
        }
        if (minecraftClient.player.getMainHandStack().isOf(AvataDrone.DJI_RC2)) {
            return minecraftClient.player.getMainHandStack();
        }
        return minecraftClient.player.getOffHandStack().isOf(AvataDrone.DJI_RC2) ? minecraftClient.player.getOffHandStack() : ItemStack.EMPTY;
    }

    public static boolean isVisible() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return !dismissed && Rc2View.controllerAvailable() && minecraftClient.currentScreen == null;
    }

    private static AvataDroneEntity findDrone(ItemStack itemStack) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient.world != null && minecraftClient.player != null) {
            if (Rc2Item.hasPower(itemStack) && itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid") && minecraftClient.world.getRegistryKey().getValue().toString().equals(itemStack.getNbt().getString("DroneDimension"))) {
                UUID uUID = itemStack.getNbt().getUuid("DroneUuid");
                if (!uUID.equals(pairedId)) {
                    Rc2View.clearMouseInput();
                    pairedId = uUID;
                    drone = null;
                    assistReady = false;
                    nextAssistFrame = 0L;
                    ready = false;
                    nextFrame = 0L;
                }
                if (drone == null || !drone.isAlive() || drone.isRemoved() || drone.getWorld() != minecraftClient.world) {
                    drone = null;
                    for (Entity entity : minecraftClient.world.getEntities()) {
                        if (!(entity instanceof AvataDroneEntity)) continue;
                        AvataDroneEntity avataDroneEntity = (AvataDroneEntity)entity;
                        if (!uUID.equals(entity.getUuid())) continue;
                        drone = avataDroneEntity;
                        failed = false;
                        nextFrame = 0L;
                        break;
                    }
                }
                return drone != null && drone.isAlive() && drone.hasControlSignal(minecraftClient.player) ? drone : null;
            }
            pairedId = null;
            drone = null;
            return null;
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void capture(float f, Runnable runnable) {
        if (!Rc2View.isVisible()) {
            Rc2View.close();
        } else {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            AvataDroneEntity avataDroneEntity = Rc2View.findDrone(Rc2View.heldController());
            if (avataDroneEntity == null) {
                if (System.nanoTime() - lastFrameAt > 1000000000L) {
                    ready = false;
                }
            } else if (ready && avataDroneEntity.getUuid().equals(capturedDroneId) && Rc2FlightUi.coversMainFeed()) {
                nextFrame = 0L;
            } else {
                long l = System.nanoTime();
                if (l >= nextFrame) {
                    long l2 = Math.max(avataDroneEntity.getCameraFrameIntervalNanos(), 16666666L);
                    nextFrame = Math.max(nextFrame, l - l2) + l2;
                    Framebuffer framebuffer = minecraftClient.getFramebuffer();
                    Entity entity = minecraftClient.getCameraEntity();
                    Perspective perspective = minecraftClient.options.getPerspective();
                    Matrix4f matrix4f = new Matrix4f(RenderSystem.getProjectionMatrix());
                    Matrix3f matrix3f = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
                    float f2 = ((GameRendererAccessor)((Object)minecraftClient.gameRenderer)).avatadrone$getViewDistance();
                    RenderSystem.getModelViewStack().push();
                    RenderSystem.getModelViewStack().loadIdentity();
                    RenderSystem.applyModelViewMatrix();
                    minecraftClient.getProfiler().push("rc2_feed");
                    try {
                        Vec3d vec3d;
                        Object object;
                        float f3;
                        if (feed == null || Rc2View.feed.textureWidth != framebuffer.textureWidth || Rc2View.feed.textureHeight != framebuffer.textureHeight) {
                            if (feed != null) {
                                feed.delete();
                            }
                            feed = new SimpleFramebuffer(framebuffer.textureWidth, framebuffer.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
                            feed.setTexFilter(9729);
                        }
                        ((MinecraftClientAccessor)((Object)minecraftClient)).avatadrone$setFramebuffer(feed);
                        feedTargetId = avataDroneEntity.getId();
                        feedWidth = Rc2View.feed.textureWidth;
                        feedHeight = Rc2View.feed.textureHeight;
                        feed.setClearColor(0.42f, 0.62f, 0.78f, 1.0f);
                        feed.clear(MinecraftClient.IS_SYSTEM_MAC);
                        feed.beginWrite(true);
                        minecraftClient.setCameraEntity(avataDroneEntity);
                        minecraftClient.options.setPerspective(Perspective.FIRST_PERSON);
                        float f4 = avataDroneEntity.getCameraYaw(f);
                        CameraControls cameraControls = Rc2View.cameraControls.computeIfAbsent(avataDroneEntity.getUuid(), uUID -> new CameraControls());
                        boolean bl = !(avataDroneEntity instanceof Mini4ProEntity) && !(avataDroneEntity instanceof M400Entity);
                        float f5 = MathHelper.clamp(avataDroneEntity.getCameraPitch(f) + cameraControls.pitch, -90.0f, 90.0f);
                        float f6 = f3 = bl ? cameraControls.roll : 0.0f;
                        if (fpvGimbal) {
                            Vec3d vec3d2 = avataDroneEntity.getVelocity();
                            object = DroneAttitude.calculate(vec3d2.x, vec3d2.z, avataDroneEntity.bodyYaw, avataDroneEntity.areMotorsArmed(), !(avataDroneEntity instanceof Mini4ProEntity) && !(avataDroneEntity instanceof M400Entity));
                            f5 = ((DroneAttitude)object).cameraPitch(f5, true);
                            f3 += ((DroneAttitude)object).cameraRoll(true);
                        }
                        float f7 = (float)Math.toRadians(f4);
                        object = new Vec3d(-Math.sin(f7), 0.0, Math.cos(f7));
                        feedPosition = vec3d = avataDroneEntity.getLerpedPos(f).add(((Vec3d)object).multiply(1.1)).add(0.0, 0.28, 0.0);
                        feedYaw = f4;
                        feedPitch = f5;
                        feedRoll = f3;
                        feedVerticalFov = (float)((bl ? (double)cameraControls.fov : avataDroneEntity.getCameraVerticalFov()) / (double)Math.max(1.0f, cameraZoom));
                        feedViewDistance = (float)DroneChunkClient.viewRadius() * 16.0f + (float)Math.max(0.0, avataDroneEntity.getGroundDistance());
                        runnable.run();
                        Rc2View.renderAssistPass(minecraftClient, avataDroneEntity, runnable, f);
                        feed.beginWrite(true);
                        RenderSystem.colorMask(false, false, false, true);
                        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
                        RenderSystem.clear(16384, MinecraftClient.IS_SYSTEM_MAC);
                        RenderSystem.colorMask(true, true, true, true);
                        capturedDroneId = avataDroneEntity.getUuid();
                        ready = true;
                        failed = false;
                        lastFrameAt = System.nanoTime();
                    }
                    catch (RuntimeException runtimeException) {
                        failed = true;
                        nextFrame = System.nanoTime() + 1000000000L;
                        LOGGER.error("RC 2 video render failed; retrying in one second", runtimeException);
                    }
                    finally {
                        RenderSystem.colorMask(true, true, true, true);
                        minecraftClient.getProfiler().pop();
                        RenderSystem.getModelViewStack().pop();
                        RenderSystem.applyModelViewMatrix();
                        feedTargetId = -1;
                        ((GameRendererAccessor)((Object)minecraftClient.gameRenderer)).avatadrone$setViewDistance(f2);
                        minecraftClient.setCameraEntity(entity);
                        minecraftClient.options.setPerspective(perspective);
                        ((MinecraftClientAccessor)((Object)minecraftClient)).avatadrone$setFramebuffer(framebuffer);
                        framebuffer.beginWrite(true);
                        minecraftClient.gameRenderer.loadProjectionMatrix(matrix4f);
                        RenderSystem.setInverseViewRotationMatrix(matrix3f);
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthMask(true);
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    public static boolean isRenderingFeed() {
        return feedTargetId != -1;
    }

    public static Vec3d feedCameraPosition() {
        return feedPosition;
    }

    public static double feedFov() {
        return feedVerticalFov;
    }

    public static float feedDistance() {
        return feedViewDistance;
    }

    public static Matrix4f feedProjection() {
        return new Matrix4f().perspective((float)Math.toRadians(feedVerticalFov), (float)feedWidth / (float)feedHeight, 0.05f, Math.max(512.0f, feedViewDistance * 2.0f));
    }

    public static void configureFeedCamera(Camera camera, MatrixStack matrixStack) {
        ((CameraAccessor)((Object)camera)).avatadrone$setPos(Rc2View.feedPosition.x, Rc2View.feedPosition.y, Rc2View.feedPosition.z);
        float f = assistPass ? assistYaw : feedYaw;
        float f2 = assistPass ? assistPitch : feedPitch;
        float f3 = assistPass ? 0.0f : feedRoll;
        ((CameraAccessor)((Object)camera)).avatadrone$setRotation(f, f2);
        camera.getRotation().rotateZ((float)Math.toRadians(-f3));
        camera.getHorizontalPlane().set(0.0f, 0.0f, 1.0f).rotate(camera.getRotation());
        camera.getVerticalPlane().set(0.0f, 1.0f, 0.0f).rotate(camera.getRotation());
        camera.getDiagonalPlane().set(1.0f, 0.0f, 0.0f).rotate(camera.getRotation());
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(feedRoll));
    }

    public static boolean isFeedTarget(Entity entity) {
        return entity.getId() == feedTargetId;
    }

    public static void renderHud(DrawContext drawContext, float f) {
        if (Rc2View.isVisible()) {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            flightView = true;
            int n = drawContext.getScaledWindowWidth();
            int n2 = drawContext.getScaledWindowHeight();
            if (screenOnly) {
                Rc2View.renderScreenOnly(drawContext, n, n2, f);
                return;
            }
            float f2 = Math.min((float)n * 0.96f / 1280.0f, (float)n2 * 0.96f / 900.0f);
            float f3 = ((float)n - 1280.0f * f2) / 2.0f;
            float f4 = ((float)n2 - 900.0f * f2) / 2.0f;
            canvasScale = f2;
            canvasOffsetX = f3;
            canvasOffsetY = f4;
            drawContext.fill(0, 0, n, n2, -939062770);
            drawContext.fillGradient(0, 0, n, n2 / 2, 2014517794, 461326);
            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(f3, f4, 0.0f);
            drawContext.getMatrices().scale(f2, f2, 1.0f);
            Rc2View.drawController(drawContext);
            Rc2View.drawPhysicalControls(drawContext);
            Rc2View.drawSticks(drawContext);
            Rc2View.drawStickHints(drawContext);
            int n3 = (int)Math.floor(f3 + 92.75f * f2);
            int n4 = (int)Math.floor(f4 + 299.75f * f2);
            int n5 = (int)Math.ceil(f3 + 1187.25f * f2);
            int n6 = (int)Math.ceil(f4 + 880.0f * f2);
            drawContext.enableScissor(n3, n4, n5, n6);
            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(92.75f, 299.75f, 0.0f);
            drawContext.getMatrices().scale(0.8550781f, 0.8533088f, 1.0f);
            Rc2View.drawScreenBackdrop(drawContext);
            Rc2FlightUi.setMouse(Rc2View.designMouseX(), Rc2View.designMouseY(), Rc2View.isDesignInside(Rc2View.designMouseX(), Rc2View.designMouseY()));
            Rc2FlightUi.render(drawContext, f);
            drawContext.draw();
            drawContext.getMatrices().pop();
            drawContext.disableScissor();
            drawContext.getMatrices().pop();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderScreenOnly(DrawContext drawContext, int n, int n2, float f) {
        float f2 = Math.min((float)n / 1280.0f, (float)n2 / 680.0f) * 0.98f;
        float f3 = ((float)n - 1280.0f * f2) / 2.0f;
        float f4 = ((float)n2 - 680.0f * f2) / 2.0f;
        canvasScale = f2;
        canvasOffsetX = f3;
        canvasOffsetY = f4;
        drawContext.fill(0, 0, n, n2, -16777216);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(f3, f4, 0.0f);
        drawContext.getMatrices().scale(f2, f2, 1.0f);
        Rc2View.drawScreenBackdrop(drawContext);
        Rc2FlightUi.setMouse(Rc2View.designMouseX(), Rc2View.designMouseY(), Rc2View.isDesignInside(Rc2View.designMouseX(), Rc2View.designMouseY()));
        Rc2FlightUi.render(drawContext, f);
        drawContext.draw();
        drawContext.getMatrices().pop();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private static void drawController(DrawContext drawContext) {
        drawContext.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawContext.drawTexture(FRONT_SHADOW, -2, -182, 1320, 1221, 0.0f, 0.0f, 1440, 1332, 1440, 1332);
        drawContext.drawTexture(FRONT, -20, -206, 1320, 1221, 0.0f, 0.0f, 1440, 1332, 1440, 1332);
        drawContext.draw();
    }

    private static void drawSticks(DrawContext drawContext) {
        float f = MathHelper.clamp((DroneKeys.isRemoteMode() ? 0.0f : Rc2View.key(88) - Rc2View.key(90)) + leftStickX, -1.0f, 1.0f);
        float f2 = MathHelper.clamp(Rc2View.verticalKeys() + leftStickY, -1.0f, 1.0f);
        float f3 = MathHelper.clamp((DroneKeys.isRemoteMode() ? 0.0f : Rc2View.key(68) - Rc2View.key(65)) + rightStickX, -1.0f, 1.0f);
        float f4 = MathHelper.clamp((DroneKeys.isRemoteMode() ? 0.0f : Rc2View.key(87) - Rc2View.backwardKey()) + rightStickY, -1.0f, 1.0f);
        float f5 = f * stickAmount;
        visLeftX += (f5 - visLeftX) * 0.38f;
        float f6 = f2 * stickAmount;
        visLeftY += (f6 - visLeftY) * 0.38f;
        float f7 = f3 * stickAmount;
        visRightX += (f7 - visRightX) * 0.38f;
        float f8 = f4 * stickAmount;
        visRightY += (f8 - visRightY) * 0.38f;
        Rc2View.drawKnob(drawContext, 233.0f, 129.25f, visLeftX, visLeftY, draggedStick == 0);
        Rc2View.drawKnob(drawContext, 1047.0f, 129.25f, visRightX, visRightY, draggedStick == 1);
    }

    private static void selectPhysicalMode(double d) {
        int n = d < 618.5 ? 0 : (d < 661.5 ? 1 : 2);
        if (n != flightMode) {
            Rc2View.setFlightMode(n);
        }
    }

    private static void drawPhysicalControls(DrawContext drawContext) {
        boolean bl;
        int n;
        int n2;
        ItemStack itemStack = Rc2View.heldController();
        int n3 = Rc2Item.batteryLights(itemStack);
        for (n2 = 0; n2 < 4; ++n2) {
            int n4 = Math.round(640.0f - (0.44f - (float)n2 * 0.59f) * 55.0f);
            Rc2FlightUi.roundRect(drawContext, n4 - 5, 31, 10, 10, 5, n2 < n3 ? -10295631 : -12236726);
        }
        drawContext.fill(553, 30, 580, 42, Rc2Indicators.connected(itemStack) ? -10295631 : -46004);
        if (mouseCanvasX >= 550.0 && mouseCanvasX <= 722.0 && mouseCanvasY >= 24.0 && mouseCanvasY <= 48.0) {
            Rc2View.drawPill(drawContext, 480.0f, 55.0f, 320.0f, 28.0f, -586148840, 0x33FFFFFF, 8);
            Rc2View.drawText(drawContext, "遥控器电量 " + Math.round(DeviceCharging.getCharge(itemStack) * 100.0) + "% · " + (Rc2Indicators.connected(itemStack) ? "已连接" : "已断连"), 640.0f, 61.0f, 1.0f, -985865, false, 1);
        }
        drawContext.fill(571, 187, 710, 223, -14079187);
        drawContext.fill(574, 190, 707, 220, -15658219);
        for (n2 = 578; n2 < 706; n2 = (int)((short)(n2 + 5))) {
            drawContext.fill(n2, 192, n2 + 1, 218, -14210773);
        }
        n2 = 597 + flightMode * 43;
        drawContext.fill(n2 - 14, 194, n2 + 14, 216, -13354949);
        drawContext.fill(n2 - 12, 201, n2 + 12, 209, -947402);
        drawContext.fill(580, 102, 700, 141, -9079435);
        String[] stringArray = new String[]{"C", "N", "S"};
        for (n = 0; n < 3; ++n) {
            Rc2View.drawText(drawContext, stringArray[n], 597 + n * 43, 109.0f, 2.8f, n == flightMode ? -678822 : -2960686, true, 1);
        }
        n = mouseCanvasX >= 570.0 && mouseCanvasX <= 710.0 && mouseCanvasY >= 102.0 && mouseCanvasY <= 226.0 ? 1 : 0;
        boolean bl2 = bl = Math.hypot(mouseCanvasX - 480.5, mouseCanvasY - 204.4) <= 43.0;
        if (bl || physicalReturnPressed != 0L) {
            Rc2View.drawRing(drawContext, 480.5f, 204.4f, 42, 2.0f, physicalReturnFired ? -10295631 : -678822);
            if (physicalReturnPressed != 0L) {
                float f = MathHelper.clamp((float)(System.nanoTime() - physicalReturnPressed) / 5.0E8f, 0.0f, 1.0f);
                drawContext.fill(449, 251, 512, 255, -1441786856);
                drawContext.fill(449, 251, 449 + (int)(63.0f * f), 255, -10295631);
            }
        }
        if (n != 0 || bl) {
            String string = n != 0 ? "C 平稳 · N 普通 · S 运动（点击或拖动）" : "短按：紧急悬停 · 长按 0.5 秒：返航";
            Rc2View.drawPill(drawContext, 345.0f, 267.0f, 590.0f, 26.0f, -586148840, 0x33FFFFFF, 8);
            Rc2View.drawText(drawContext, string, 640.0f, 274.0f, 1.0f, -985865, false, 1);
        }
    }

    private static void drawKnob(DrawContext drawContext, float f, float f2, float f3, float f4, boolean bl) {
        float f5 = f + f3 * 17.0f;
        float f6 = f2 - f4 * 13.0f;
        drawContext.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Rc2View.drawRoundLine(drawContext, f, f2, f5, f6, 9.0f, -14341584);
        Rc2View.drawEllipse(drawContext, f5 + 3.0f, f6 + 5.0f, 29, 24, 0x66000000);
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(f5, f6, 0.0f);
        drawContext.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f3 * 12.0f));
        drawContext.getMatrices().scale(1.0f, 1.0f - Math.abs(f4) * 0.1f, 1.0f);
        drawContext.drawTexture(STICK, -29, -29, 58, 58, 0.0f, 0.0f, 256, 256, 256, 256);
        drawContext.getMatrices().pop();
        if (bl) {
            Rc2View.drawRing(drawContext, f5, f6, 33, 2.0f, -10295631);
        }
        drawContext.draw();
    }

    private static void drawStickHints(DrawContext drawContext) {
        boolean bl;
        boolean bl2 = bl = System.nanoTime() - entryTime < 7000000000L;
        if (bl) {
            boolean bl3;
            boolean bl4 = Math.hypot(mouseCanvasX - 233.0, mouseCanvasY - 129.25) < 70.0;
            boolean bl5 = bl3 = Math.hypot(mouseCanvasX - 1047.0, mouseCanvasY - 129.25) < 70.0;
            if (bl4 || bl3) {
                float f = bl4 ? 233.0f : 1047.0f;
                String string = bl4 ? "左摇杆 · 偏航 / 升降" : "右摇杆 · 俯仰 / 横滚";
                Rc2View.drawPill(drawContext, f - 118.0f, 67.25f, 236.0f, 30.0f, -653586412, 0x33FFFFFF, 10);
                Rc2View.drawText(drawContext, string, f, 76.25f, 0.82f, -1510671, false, 1);
            }
        }
    }

    public static NbtCompound accidentReport() {
        ItemStack itemStack = Rc2View.heldController();
        if (itemStack.hasNbt() && itemStack.getNbt().containsUuid("DroneUuid") && itemStack.getNbt().contains("DroneAccident")) {
            NbtCompound nbtCompound = itemStack.getNbt().getCompound("DroneAccident");
            return nbtCompound.containsUuid("Drone") && nbtCompound.getUuid("Drone").equals(itemStack.getNbt().getUuid("DroneUuid")) ? nbtCompound : null;
        }
        return null;
    }

    private static void drawScreenBackdrop(DrawContext drawContext) {
        drawContext.fill(0, 0, 1280, 680, -16447477);
        drawContext.fill(2, 2, 1278, 678, -16117996);
        NbtCompound nbtCompound = Rc2View.accidentReport();
        if (nbtCompound != null) {
            if (feed != null && capturedDroneId != null && capturedDroneId.equals(nbtCompound.getUuid("Drone"))) {
                Rc2View.drawVideo(drawContext, 4, 4, 1272, 672);
            }
            drawContext.fill(0, 0, 1280, 680, -1727723509);
            Rc2View.drawText(drawContext, "图传断连 · " + (nbtCompound.getInt("Reason") == 2 ? "飞行器进水" : "飞行器撞击"), 640.0f, 260.0f, 1.4f, -42149, true, 1);
            Rc2View.drawText(drawContext, String.format(Locale.ROOT, "断连位置  X %.1f   Y %.1f   Z %.1f", nbtCompound.getDouble("X"), nbtCompound.getDouble("Y"), nbtCompound.getDouble("Z")), 640.0f, 310.0f, 1.1f, -854537, true, 1);
            Rc2View.drawText(drawContext, "飞行器损坏 · 无法返航", 640.0f, 354.0f, 1.0f, -15801, true, 1);
            Rc2View.drawText(drawContext, nbtCompound.getString("Dimension"), 640.0f, 390.0f, 0.8f, -6444621, false, 1);
        } else if (ready && feed != null) {
            Rc2View.drawVideo(drawContext, 4, 4, 1272, 672);
        } else {
            Rc2View.drawNoFeed(drawContext);
        }
    }

    private static void drawNoFeed(DrawContext drawContext) {
        drawContext.fillGradient(0, 0, 1280, 680, -15722979, -16315633);
        for (int i = 0; i < 10; ++i) {
            int n = 34 + i * 62;
            drawContext.fill(0, n, 1280, n + 1, 0xAFFFFFF);
        }
        Rc2FlightUi.drawNoFeedMessage(drawContext, failed, pairedId == null);
    }

    private static void drawGlass(DrawContext drawContext) {
        for (int n = 0; n < 680; n = (int)((byte)(n + 3))) {
            int n2 = (int)(102.399994f + (float)n * 0.62f);
            int n3 = 90 + (int)((float)n * 0.08f);
            drawContext.fill(n2, n, n2 + n3, n + 2, 0x5FFFFFF);
        }
        drawContext.fillGradient(0, 0, 1280, 70, 1711737616, 461584);
        drawContext.fillGradient(0, 584, 1280, 680, 461584, 1996950288);
    }

    public static boolean drawFeedInto(DrawContext drawContext, int n, int n2, int n3, int n4) {
        if (ready && feed != null && n3 > 0 && n4 > 0) {
            Rc2View.drawTexture(drawContext, feed, n, n2, n3, n4);
            return true;
        }
        return false;
    }

    public static boolean drawAssistInto(DrawContext drawContext, int n, int n2, int n3, int n4) {
        if (assistReady && assistFeed != null && n3 > 0 && n4 > 0) {
            Rc2View.drawTexture(drawContext, assistFeed, n, n2, n3, n4);
            return true;
        }
        return false;
    }

    private static void drawVideo(DrawContext drawContext, int n, int n2, int n3, int n4) {
        Rc2View.drawTexture(drawContext, feed, n, n2, n3, n4);
    }

    private static void drawTexture(DrawContext drawContext, Framebuffer framebuffer, int n, int n2, int n3, int n4) {
        drawContext.draw();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Matrix4f matrix4f = drawContext.getMatrices().peek().getPositionMatrix();
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        float f = 0.028f;
        float f2 = 0.972f;
        bufferBuilder.vertex(matrix4f, n, n2 + n4, 0.0f).texture(0.0f, f).next();
        bufferBuilder.vertex(matrix4f, n + n3, n2 + n4, 0.0f).texture(1.0f, f).next();
        bufferBuilder.vertex(matrix4f, n + n3, n2, 0.0f).texture(1.0f, f2).next();
        bufferBuilder.vertex(matrix4f, n, n2, 0.0f).texture(0.0f, f2).next();
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }

    private static double designMouseX() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return Rc2View.toDesignPoint(minecraftClient.mouse.getX(), minecraftClient.mouse.getY())[0];
    }

    private static double designMouseY() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return Rc2View.toDesignPoint(minecraftClient.mouse.getX(), minecraftClient.mouse.getY())[1];
    }

    private static boolean isDesignInside(double d, double d2) {
        return d >= 0.0 && d2 >= 0.0 && d <= 1280.0 && d2 <= 680.0;
    }

    public static double[] toCanvasPoint(double d, double d2) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        double d3 = Math.min((double)minecraftClient.getWindow().getScaledWidth() * 0.96 / 1280.0, (double)minecraftClient.getWindow().getScaledHeight() * 0.96 / 900.0);
        double d4 = ((double)minecraftClient.getWindow().getScaledWidth() - 1280.0 * d3) / 2.0;
        double d5 = ((double)minecraftClient.getWindow().getScaledHeight() - 900.0 * d3) / 2.0;
        double d6 = d * (double)minecraftClient.getWindow().getScaledWidth() / (double)minecraftClient.getWindow().getWidth();
        double d7 = d2 * (double)minecraftClient.getWindow().getScaledHeight() / (double)minecraftClient.getWindow().getHeight();
        return new double[]{(d6 - d4) / d3, (d7 - d5) / d3};
    }

    public static double[] toDesignPoint(double d, double d2) {
        if (screenOnly) {
            MinecraftClient minecraftClient = MinecraftClient.getInstance();
            double d3 = minecraftClient.getWindow().getScaledWidth();
            double d4 = minecraftClient.getWindow().getScaledHeight();
            double d5 = Math.min(d3 / 1280.0, d4 / 680.0) * 0.98;
            double d6 = (d3 - 1280.0 * d5) / 2.0;
            double d7 = (d4 - 680.0 * d5) / 2.0;
            double d8 = d * d3 / (double)minecraftClient.getWindow().getWidth();
            double d9 = d2 * d4 / (double)minecraftClient.getWindow().getHeight();
            return new double[]{(d8 - d6) / d5, (d9 - d7) / d5};
        }
        double[] dArray = Rc2View.toCanvasPoint(d, d2);
        return new double[]{(dArray[0] - 92.75) * 1280.0 / 1094.5, (dArray[1] - 299.75) * 680.0 / 580.25};
    }

    private static void drawRoundLine(DrawContext drawContext, float f, float f2, float f3, float f4, float f5, int n) {
        float f6 = f3 - f;
        float f7 = f4 - f2;
        float f8 = (float)Math.sqrt(f6 * f6 + f7 * f7);
        if (!(f8 < 0.01f)) {
            float f9 = -f7 / f8 * f5 / 2.0f;
            float f10 = f6 / f8 * f5 / 2.0f;
            float[] fArray = new float[]{f + f9, f - f9, f3 + f9, f3 - f9};
            float[] fArray2 = new float[]{f2 + f10, f2 - f10, f4 + f10, f4 - f10};
            Rc2View.fillQuad(drawContext, fArray, fArray2, n);
            Rc2View.drawEllipse(drawContext, f, f2, (int)(f5 / 2.0f), (int)(f5 / 2.0f), n);
            Rc2View.drawEllipse(drawContext, f3, f4, (int)(f5 / 2.0f), (int)(f5 / 2.0f), n);
        }
    }

    private static void drawEllipse(DrawContext drawContext, float f, float f2, int n, int n2, int n3) {
        for (int i = -n2; i <= n2; ++i) {
            double d = 1.0 - (double)(i * i) / (double)(n2 * n2);
            if (d <= 0.0) continue;
            int n4 = (int)((double)n * Math.sqrt(d));
            drawContext.fill((int)f - n4, (int)f2 + i, (int)f + n4 + 1, (int)f2 + i + 1, n3);
        }
    }

    private static void drawRing(DrawContext drawContext, float f, float f2, int n, float f3, int n2) {
        int n3 = (int)((float)n - f3);
        for (int i = -n; i <= n; ++i) {
            int n4;
            double d = 1.0 - (double)(i * i) / (double)(n * n);
            if (d <= 0.0) continue;
            int n5 = (int)((double)n * Math.sqrt(d));
            double d2 = 1.0 - (double)(i * i) / (double)(n3 * n3);
            int n6 = n4 = d2 > 0.0 ? (int)((double)n3 * Math.sqrt(d2)) : 0;
            if (n4 <= 0) {
                drawContext.fill((int)f - n5, (int)f2 + i, (int)f + n5 + 1, (int)f2 + i + 1, n2);
                continue;
            }
            drawContext.fill((int)f - n5, (int)f2 + i, (int)f - n4, (int)f2 + i + 1, n2);
            drawContext.fill((int)f + n4, (int)f2 + i, (int)f + n5 + 1, (int)f2 + i + 1, n2);
        }
    }

    private static void fillQuad(DrawContext drawContext, float[] fArray, float[] fArray2, int n) {
        int n2 = (int)Math.floor(Math.min(Math.min(fArray[0], fArray[1]), Math.min(fArray[2], fArray[3])));
        int n3 = (int)Math.ceil(Math.max(Math.max(fArray[0], fArray[1]), Math.max(fArray[2], fArray[3])));
        int n4 = (int)Math.floor(Math.min(Math.min(fArray2[0], fArray2[1]), Math.min(fArray2[2], fArray2[3])));
        int n5 = (int)Math.ceil(Math.max(Math.max(fArray2[0], fArray2[1]), Math.max(fArray2[2], fArray2[3])));
        for (int i = n4; i <= n5; ++i) {
            int n6 = Integer.MAX_VALUE;
            int n7 = Integer.MIN_VALUE;
            for (int j = 0; j < 4; ++j) {
                float f = fArray2[j];
                int n8 = (j + 1) % 4;
                float f2 = fArray2[n8];
                if (!((float)i + 0.5f >= Math.min(f, f2)) || !((float)i + 0.5f <= Math.max(f, f2)) || !(Math.abs(f2 - f) > 1.0E-5f)) continue;
                float f3 = ((float)i + 0.5f - f) / (f2 - f);
                int n9 = (int)(fArray[j] + f3 * (fArray[n8] - fArray[j]));
                n6 = Math.min(n6, n9);
                n7 = Math.max(n7, n9);
            }
            if (n6 > n7) continue;
            drawContext.fill(n6, i, n7 + 1, i + 1, n);
        }
    }

    private static void drawPill(DrawContext drawContext, float f, float f2, float f3, float f4, int n, int n2, int n3) {
        Rc2FlightUi.roundRect(drawContext, (int)f, (int)f2, (int)f3, (int)f4, n3, n);
        Rc2FlightUi.roundRectBorder(drawContext, (int)f, (int)f2, (int)f3, (int)f4, n3, n2);
    }

    private static void drawText(DrawContext drawContext, String string, float f, float f2, float f3, int n, boolean bl, int n2) {
        Rc2FlightUi.text(drawContext, string, f, f2, f3, n, bl, n2);
    }

    public static boolean hasTelemetry() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        return drone != null && drone.isAlive() && !drone.isRemoved() && minecraftClient.player != null && drone.getWorld() == minecraftClient.world && drone.hasControlSignal(minecraftClient.player);
    }

    public static AvataDroneEntity drone() {
        return Rc2View.hasTelemetry() ? drone : null;
    }

    public static int flightMode() {
        return flightMode;
    }

    public static String modeDescription(int n) {
        String string = (new String[]{"平稳 C", "普通 N", "运动 S"})[n];
        if (drone instanceof M400Entity) {
            return string + " · 上升 " + (new int[]{5, 8, 10})[n] + " / 下降 " + (new int[]{5, 8, 10})[n] + " / 水平 " + (new int[]{13, 21, 27})[n] + " m/s";
        }
        return drone instanceof Mini4ProEntity ? string + " · 上升/下降 " + (n == 0 ? 3 : 5) + " / 水平 " + (n == 2 ? 16 : (n == 0 ? 6 : 12)) + " m/s" : string + " · 上升 " + (new int[]{3, 6, 10})[n] + " / 下降 " + (new int[]{3, 6, 10})[n] + " / 水平 " + (new int[]{8, 15, 18})[n] + " m/s";
    }

    public static boolean isCruiseActive() {
        return cruise;
    }

    public static void setFlightMode(int n) {
        int n2 = flightMode;
        flightMode = MathHelper.clamp(n, 0, 2);
        if (flightMode == 2 && n2 != 2) {
            VoiceAnnouncer.playSportMode();
        }
        nextControl = 0L;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient.player != null) {
            minecraftClient.player.sendMessage(Text.literal(Rc2View.modeDescription(flightMode)), true);
        }
    }

    public static float stickAmount() {
        return stickAmount;
    }

    public static void resetStickAmount() {
        stickAmount = 1.0f;
    }

    public static boolean isFpvGimbal() {
        return fpvGimbal;
    }

    public static void setFpvGimbal(boolean bl) {
        fpvGimbal = bl;
        Rc2FlightUi.toast(bl ? "云台已切换为 FPV 模式" : "云台已切换为稳定模式");
    }

    public static float cameraZoom() {
        return cameraZoom;
    }

    public static void setCameraZoom(float f) {
        cameraZoom = MathHelper.clamp(f, 1.0f, 10.0f);
        nextFrame = 0L;
    }

    public static boolean isFeedReady() {
        return ready && feed != null;
    }

    public static boolean isFeedFailed() {
        return failed;
    }

    public static boolean hasPairing() {
        return pairedId != null;
    }

    public static boolean isNearStick(int n) {
        float f = n == 0 ? 233.0f : 1047.0f;
        return Math.hypot(mouseCanvasX - (double)f, mouseCanvasY - 129.25) < 70.0;
    }

    public static boolean isDraggingStick() {
        return draggedStick >= 0;
    }

    public static int draggedStick() {
        return draggedStick;
    }

    public static float visualLeftX() {
        return visLeftX;
    }

    public static float visualLeftY() {
        return visLeftY;
    }

    public static float visualRightX() {
        return visRightX;
    }

    public static float visualRightY() {
        return visRightY;
    }

    public static long entryTime() {
        return entryTime;
    }

    public static void requestTakeoffOrLand() {
        if (drone == null) {
            Rc2FlightUi.toast("未连接飞行器");
        } else if (drone.areMotorsArmed()) {
            autoLand = true;
            autoTakeoff = false;
            Rc2FlightUi.toast("自动降落中");
        } else {
            autoTakeoff = true;
            autoLand = false;
            clickArmRequested = true;
            Rc2FlightUi.toast("正在解锁电机 · 自动起飞至 1.2m");
        }
    }

    public static void requestReturnHome() {
        VoiceAnnouncer.cancelAutoReturn();
        if (drone != null && drone.areMotorsArmed()) {
            clickReturnHome = true;
            VoiceAnnouncer.playReturnHome();
            Rc2FlightUi.toast("正在返航");
        } else {
            Rc2FlightUi.toast("飞行器未起飞，无法返航");
        }
    }

    public static void requestEmergencyStop() {
        VoiceAnnouncer.cancelAutoReturn();
        brakeUntil = System.nanoTime() + 200000000L;
        horizontalLockUntil = System.nanoTime() + 1200000000L;
        clickEmergencyStop = true;
        Rc2FlightUi.toast("紧急悬停");
    }

    public static int batteryPercent() {
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        if (drone != null && drone.getBatteryPercent() >= 0.0f) {
            return Math.round(drone.getBatteryPercent());
        }
        if (drone != null && drone.areMotorsArmed()) {
            if (flightStartedAt == 0L) {
                flightStartedAt = System.nanoTime();
            }
            long l = (System.nanoTime() - flightStartedAt) / 1000000000L;
            double d = drone == null ? 1.0 : Math.max(0.25, drone.getEnduranceFactor());
            return Math.max(0, 99 - (int)((double)l / d * 99.0 / 1380.0));
        }
        flightStartedAt = 0L;
        return 99;
    }

    public static int remainingSeconds() {
        int n;
        long l = System.nanoTime();
        if (cachedRemainingSec >= 0 && l < nextRemainingRefreshAt) {
            return cachedRemainingSec;
        }
        if (!Rc2View.hasTelemetry()) {
            n = -1;
        } else if (drone != null && drone.getRemainingFlightSeconds() >= 0) {
            n = drone.getRemainingFlightSeconds();
        } else if (drone != null && drone.areMotorsArmed() && flightStartedAt != 0L) {
            int n2 = (int)Math.max(0L, 1380L - (l - flightStartedAt) / 1000000000L);
            n = (int)((double)n2 * Math.max(0.25, drone.getEnduranceFactor()));
        } else {
            n = 1380;
        }
        cachedRemainingSec = n;
        nextRemainingRefreshAt = l + 500000000L;
        return n;
    }

    public static int returnSeconds() {
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        AvataDroneEntity avataDroneEntity = drone;
        if (avataDroneEntity == null) {
            return 0;
        }
        double d = Math.max(0.0, avataDroneEntity.getHomeDistance()) * 1.15;
        double d2 = Math.max(0.0, avataDroneEntity.getGroundDistance());
        double d3 = Math.max(d2, 120.0);
        double d4 = Math.max(0.0, d3 - d2);
        double d5 = 4.0;
        double d6 = 2.0;
        double d7 = 0.6;
        return (int)Math.ceil(d / d5 + d4 / d6 + d3 / d7 + 12.0);
    }

    public static int returnBatteryPercent() {
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        int n = Rc2View.remainingSeconds();
        int n2 = Rc2View.batteryPercent();
        double d = n2 > 0 ? (double)n / (double)n2 : 18.18;
        int n3 = (int)Math.ceil((double)Rc2View.returnSeconds() / d) + 2;
        return MathHelper.clamp(n3, 3, 45);
    }

    public static int autoReturnBatteryPercent() {
        return !Rc2View.hasTelemetry() ? -1 : MathHelper.clamp(Rc2View.returnBatteryPercent() + 5, 10, 45);
    }

    public static int forcedLandingBatteryPercent() {
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        return drone != null ? drone.getForcedLandingPercent() : 8;
    }

    public static int autoReturnSeconds() {
        int n;
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        int n2 = Rc2View.batteryPercent();
        if (n2 <= (n = Rc2View.autoReturnBatteryPercent())) {
            return 0;
        }
        int n3 = Rc2View.remainingSeconds();
        return (int)Math.max(0.0, (double)(n3 * (n2 - n)) / (double)n2);
    }

    public static int forcedLandingSeconds() {
        int n;
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        int n2 = Rc2View.batteryPercent();
        if (n2 <= (n = Rc2View.forcedLandingBatteryPercent())) {
            return 0;
        }
        int n3 = Rc2View.remainingSeconds();
        return (int)Math.max(0.0, (double)(n3 * (n2 - n)) / (double)n2);
    }

    private static void updateBatteryProtection(MinecraftClient minecraftClient) {
        if (Rc2View.hasTelemetry()) {
            if (drone != null && drone.areMotorsArmed()) {
                int n = Rc2View.batteryPercent();
                int n2 = Rc2View.forcedLandingBatteryPercent();
                int n3 = Rc2View.autoReturnBatteryPercent();
                if (n <= n2 && !batteryForceLandDone) {
                    batteryForceLandDone = true;
                    autoLand = true;
                    autoTakeoff = false;
                    clickArmRequested = false;
                    if (minecraftClient.player != null) {
                        minecraftClient.player.sendMessage(Text.literal("电量极低，强制降落"), true);
                    }
                    Rc2FlightUi.toast("电量极低 · 强制降落");
                } else if (!(n > n3 || batteryAutoRthDone || autoLand || drone.isReturningHome() || VoiceAnnouncer.isLowBatterySequenceActive())) {
                    batteryAutoRthDone = true;
                    clickReturnHome = true;
                    VoiceAnnouncer.playReturnHome();
                    if (minecraftClient.player != null) {
                        minecraftClient.player.sendMessage(Text.literal("电量低，自动返航"), true);
                    }
                    Rc2FlightUi.toast("电量低 · 自动返航");
                }
                if (n > n3 + 2) {
                    batteryAutoRthDone = false;
                }
                if (n > n2 + 2) {
                    batteryForceLandDone = false;
                }
            } else {
                batteryAutoRthDone = false;
                batteryForceLandDone = false;
            }
        }
    }

    public static int satelliteCount() {
        if (!Rc2View.hasTelemetry()) {
            return -1;
        }
        if (drone == null) {
            return 0;
        }
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient.world == null) {
            return 0;
        }
        int n = drone instanceof Mini4ProEntity ? 32 : 40;
        double d = Math.max(0.0, drone.getGroundDistance());
        double d2 = MathHelper.clamp(d / 120.0, 0.0, 1.0);
        int n2 = 14 + (int)Math.round(d2 * (double)(n - 14));
        if (!minecraftClient.world.isSkyVisible(drone.getBlockPos())) {
            n2 = Math.min(n2, 18);
        }
        int n3 = Math.abs(drone.getUuid().hashCode() % 5);
        return MathHelper.clamp(n2 + n3, 3, n);
    }

    public static int rcBars() {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (!Rc2View.hasTelemetry()) {
            return 0;
        }
        double d = drone.distanceTo(minecraftClient.player);
        if (d > Rc2View.rcRange()) {
            return 0;
        }
        return d < Rc2View.rcRange() * 0.25 ? 4 : (d < Rc2View.rcRange() * 0.5 ? 3 : (d < Rc2View.rcRange() * 0.75 ? 2 : 1));
    }

    public static int fourGBars() {
        return Rc2View.hasTelemetry() && drone.hasFourGSignal() ? 4 : 0;
    }

    public static double verticalSpeed() {
        if (!Rc2View.hasTelemetry()) {
            return Double.NaN;
        }
        return drone == null ? 0.0 : Rc2View.drone.getVelocity().y * 20.0;
    }

    public static double horizontalSpeed() {
        if (!Rc2View.hasTelemetry()) {
            return Double.NaN;
        }
        return drone == null ? 0.0 : drone.getVelocity().horizontalLength() * 20.0;
    }

    public static double homeDistance() {
        if (!Rc2View.hasTelemetry()) {
            return Double.NaN;
        }
        return drone == null ? 0.0 : drone.getHomeDistance();
    }

    public static double altitude() {
        if (!Rc2View.hasTelemetry()) {
            return Double.NaN;
        }
        return drone == null ? 0.0 : drone.getGroundDistance();
    }

    public static String flightState() {
        if (Rc2View.accidentReport() != null) {
            return "事故断连";
        }
        if (!Rc2View.hasTelemetry()) {
            return Rc2View.hasPairing() ? "信号丢失" : "未连接";
        }
        if (drone.isReturningHome()) {
            return "自动返航中";
        }
        if (drone.areMotorsArmed()) {
            return "飞行中";
        }
        return drone.isCableConnected() ? "充电线已连接 · 请先拔线" : "可以起飞";
    }

    public static void close() {
        cachedRemainingSec = -1;
        nextRemainingRefreshAt = 0L;
        screenOnly = false;
        enterHeld = false;
        lastEnterTapAt = 0L;
        obstacleTarget = null;
        nextObstacleScan = 0L;
        lastGroundBlock = null;
        nextGroundScan = 0L;
        if (feed != null) {
            feed.delete();
            feed = null;
            capturedDroneId = null;
        }
        if (assistFeed != null) {
            assistFeed.delete();
            assistFeed = null;
            assistReady = false;
        }
        ready = false;
        failed = false;
        pairedId = null;
        drone = null;
        nextFrame = 0L;
        nextControl = 0L;
        flightView = false;
        Rc2FlightUi.onExit();
    }

    static {
        obstacleUp = 99.0f;
        obstacleDown = 99.0f;
        obstacleDirections = new float[]{99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f, 99.0f};
        flightMode = 1;
        cachedRemainingSec = -1;
        draggedStick = -1;
        dismissed = true;
        cameraZoom = 1.0f;
        cameraControls = new HashMap<UUID, CameraControls>();
        stickAmount = 1.0f;
        canvasScale = 1.0f;
        mouseCanvasX = -999.0;
        mouseCanvasY = -999.0;
        feedTargetId = -1;
        feedWidth = 1;
        feedHeight = 1;
        assistDir = 0;
        assistAuto = true;
    }

    private static class CameraControls {
        float pitch;
        float roll;
        float fov = 70.0f;

        private CameraControls() {
        }
    }
}

