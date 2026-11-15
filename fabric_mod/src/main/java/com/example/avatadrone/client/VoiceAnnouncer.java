package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.Rc2FlightUi;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.entity.AvataDroneEntity;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public final class VoiceAnnouncer {
    private static final long NANOS_PER_MS = 1000000L;
    private static final long TAKE_OFF_DURATION_MS = 853L;
    private static final long LOW_BATTERY_DURATION_MS = 2987L;
    private static final long AUTO_RETURN_DELAY_MS = 13987L;
    private static final long POWER_ON_DELAY_MS = 2000L;
    private static final long VOICE_6_MIN_INTERVAL = 6000000000L;
    private static final double AIRBORNE_HEIGHT = 0.1;
    private static final float VOICE_VOLUME = 3.0f;
    private static final float OBSTACLE_VOLUME_BASE = 1.0f;
    private static final float OBSTACLE_VOLUME_MIN = 0.5f;
    private static final float OBSTACLE_VOLUME_MAX = 1.2f;
    private static final float OBSTACLE_RANGE = 25.0f;
    private static boolean initialized;
    private static boolean wasAirborne;
    private static boolean low30Played;
    private static boolean low15Played;
    private static boolean above500;
    private static long lastVoice6At;
    private static boolean autoReturnActive;
    private static boolean voice2Played;
    private static long voice1EndedAt;
    private static long autoReturnAt;
    private static long powerOnAt;
    private static int powerOnVoice;
    private static long obstacleStartedAt;
    private static ObstacleSound obstacleSound;
    private static float obstacleVolume;
    private static long lastReturnVoiceAt;
    private static UUID activeDroneId;

    private static void resetFlightState() {
        initialized = wasAirborne = low30Played = low15Played = above500 = false;
        autoReturnActive = voice2Played = false;
        voice1EndedAt = autoReturnAt = lastVoice6At = lastReturnVoiceAt = 0L;
        stopObstacleSound();
    }

    public static void reset() {
        resetFlightState();
        activeDroneId = null;
        powerOnAt = 0L;
        powerOnVoice = 0;
    }

    private VoiceAnnouncer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(VoiceAnnouncer::tick);
    }

    public static boolean isAutoReturnActive() {
        return autoReturnActive;
    }

    public static boolean isLowBatterySequenceActive() {
        return autoReturnActive || autoReturnAt != 0L;
    }

    public static void cancelAutoReturn() {
        autoReturnActive = false;
        autoReturnAt = 0L;
    }

    public static void schedulePowerOn(String string) {
        powerOnVoice = "mini4pro".equals(string) ? 2 : 1;
        powerOnAt = System.nanoTime() + 2000000000L;
    }

    public static void playSportMode() {
        VoiceAnnouncer.play(AvataDrone.VOICE_WUBIZHANG);
    }

    public static void playReturnHome() {
        AvataDroneEntity avataDroneEntity = Rc2View.drone();
        if (avataDroneEntity != null && avataDroneEntity.isReturningHome()) {
            return;
        }
        long l = System.nanoTime();
        if (l - lastReturnVoiceAt < 1000000000L) {
            return;
        }
        lastReturnVoiceAt = l;
        VoiceAnnouncer.play(AvataDrone.VOICE_5);
    }

    private static void tick(MinecraftClient minecraftClient) {
        if (minecraftClient.world == null || minecraftClient.player == null) {
            reset();
            return;
        }
        MinecraftClient minecraftClient2;
        boolean bl;
        AvataDroneEntity avataDroneEntity = Rc2View.drone();
        UUID nextId = avataDroneEntity == null ? null : avataDroneEntity.getUuid();
        if (!Objects.equals(activeDroneId, nextId)) {
            resetFlightState();
            activeDroneId = nextId;
        }
        double d = Rc2View.altitude();
        boolean bl2 = avataDroneEntity != null && avataDroneEntity.areMotorsArmed() && d > 0.1;
        long l = System.nanoTime();
        if (!initialized) {
            initialized = true;
            wasAirborne = bl2;
        } else {
            if (bl2 && !wasAirborne) {
                VoiceAnnouncer.onTakeOff(l);
            } else if (!bl2 && wasAirborne) {
                VoiceAnnouncer.onLanded();
            }
            wasAirborne = bl2;
        }
        if (!voice2Played && voice1EndedAt != 0L && l >= voice1EndedAt && bl2) {
            voice2Played = true;
            voice1EndedAt = 0L;
            VoiceAnnouncer.play(AvataDrone.VOICE_2);
        }
        if (powerOnAt != 0L && l >= powerOnAt) {
            powerOnAt = 0L;
            VoiceAnnouncer.play(powerOnVoice == 2 ? AvataDrone.VOICE_XINBAN : AvataDrone.VOICE_JIUBAN);
            powerOnVoice = 0;
        }
        boolean bl3 = bl = bl2 && avataDroneEntity != null && Rc2View.flightMode() != 2 && Rc2FlightUi.avoidanceMode() != 2 && Rc2View.nearestHorizontalObstacleDistance() < 23.0f;
        if (obstacleSound != null && (minecraftClient2 = MinecraftClient.getInstance()) != null && minecraftClient2.getSoundManager() != null && !minecraftClient2.getSoundManager().isPlaying(obstacleSound) && l - obstacleStartedAt > 500000000L) {
            obstacleSound = null;
        }
        if (bl) {
            if (obstacleSound == null) {
                VoiceAnnouncer.startObstacleSound(l);
            } else {
                float f = VoiceAnnouncer.obstacleTargetVolume();
                obstacleVolume += (f - obstacleVolume) * 0.2f;
                obstacleSound.setVolume(obstacleVolume);
            }
        } else {
            VoiceAnnouncer.stopObstacleSound();
        }
        if (autoReturnAt != 0L && l >= autoReturnAt) {
            autoReturnAt = 0L;
            VoiceAnnouncer.triggerAutoReturn();
        }
        if (bl2 && avataDroneEntity != null) {
            boolean bl4;
            int n = Rc2View.batteryPercent();
            if (n >= 0 && n < 30 && !low30Played) {
                low30Played = true;
                VoiceAnnouncer.play(AvataDrone.VOICE_3);
                autoReturnAt = l + 13987000000L;
            }
            if (n >= 0 && n <= 15 && !low15Played) {
                low15Played = true;
                VoiceAnnouncer.play(AvataDrone.VOICE_4);
            }
            boolean bl5 = bl4 = d >= 500.0;
            if (bl4 && !above500 && l - lastVoice6At >= 6000000000L) {
                lastVoice6At = l;
                VoiceAnnouncer.play(AvataDrone.VOICE_6);
            }
            above500 = bl4;
        }
    }

    private static void onTakeOff(long l) {
        low30Played = false;
        low15Played = false;
        above500 = false;
        autoReturnActive = false;
        autoReturnAt = 0L;
        VoiceAnnouncer.play(AvataDrone.VOICE_1);
        voice1EndedAt = l + 853000000L;
        voice2Played = false;
    }

    private static void onLanded() {
        voice1EndedAt = 0L;
        voice2Played = false;
        above500 = false;
        autoReturnActive = false;
        autoReturnAt = 0L;
        VoiceAnnouncer.play(AvataDrone.VOICE_7);
    }

    private static void triggerAutoReturn() {
        AvataDroneEntity avataDroneEntity = Rc2View.drone();
        if (avataDroneEntity == null || !avataDroneEntity.areMotorsArmed()) {
            return;
        }
        Rc2View.requestReturnHome();
        autoReturnActive = true;
    }

    private static void startObstacleSound(long l) {
        ObstacleSound obstacleSound;
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient == null || minecraftClient.getSoundManager() == null) {
            return;
        }
        obstacleVolume = 1.0f * Rc2FlightUi.softwareVoiceVolume() * 0.5f;
        VoiceAnnouncer.obstacleSound = obstacleSound = new ObstacleSound(obstacleVolume);
        obstacleStartedAt = l;
        minecraftClient.getSoundManager().play(obstacleSound);
    }

    private static float obstacleTargetVolume() {
        float f = 1.0f - MathHelper.clamp(Rc2View.nearestHorizontalObstacleDistance() / 25.0f, 0.0f, 1.0f);
        float f2 = 0.5f + 0.70000005f * f;
        return 1.0f * Rc2FlightUi.softwareVoiceVolume() * f2;
    }

    private static void stopObstacleSound() {
        if (obstacleSound == null) {
            return;
        }
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient != null && minecraftClient.getSoundManager() != null) {
            minecraftClient.getSoundManager().stop(obstacleSound);
        }
        obstacleSound = null;
        obstacleStartedAt = 0L;
    }

    private static void play(SoundEvent soundEvent) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient == null || minecraftClient.getSoundManager() == null || soundEvent == null) {
            return;
        }
        Identifier identifier = soundEvent.getId();
        PositionedSoundInstance positionedSoundInstance = new PositionedSoundInstance(identifier, SoundCategory.VOICE, 3.0f * Rc2FlightUi.softwareVoiceVolume(), 1.0f, Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
        minecraftClient.getSoundManager().play(positionedSoundInstance);
    }

    static {
        obstacleVolume = 1.0f;
    }

    private static final class ObstacleSound
    extends PositionedSoundInstance
    implements TickableSoundInstance {
        private float volume;

        ObstacleSound(float f) {
            super(AvataDrone.VOICE_BIZHANG.getId(), SoundCategory.VOICE, f, 1.0f, Random.create(), true, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
            this.volume = f;
        }

        @Override
        public float getVolume() {
            return this.volume;
        }

        void setVolume(float f) {
            this.volume = f;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public void tick() {
        }
    }
}

