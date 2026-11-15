package com.example.avatadrone.entity;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.DroneSettings;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.DroneBatteryStorage;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.navigation.ReturnRoutePlanner;
import com.example.avatadrone.network.ChargingNetwork;
import com.example.avatadrone.network.DroneControlPayload;
import com.example.avatadrone.signal.FourGNetwork;
import java.util.ArrayDeque;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AvataDroneEntity extends PathAwareEntity implements GeoEntity {
   private static final TrackedData<Float> CAMERA_YAW = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private float previousCameraYaw;
   private float cameraYaw;
   private boolean cameraYawInitialized;
   private static final TrackedData<Integer> ACCIDENT = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private Vec3d accidentPosition = Vec3d.ZERO;
   private static final TrackedData<Boolean> FOUR_G = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> MODE_360 = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> TRACKED_ARMED = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> TRACKED_RETURNING = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> TRACKED_OBSTACLE = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> OBSTACLE_FRONT = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> OBSTACLE_BACK = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> OBSTACLE_LEFT = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Float> OBSTACLE_RIGHT = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Integer> TRACKED_FLIGHT_MODE = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Float> TRACKED_GROUND_DISTANCE = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Boolean> HAS_BATTERY = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Float> AVATA_BATTERY = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
   private static final TrackedData<Long> BATTERY_CHANGED_AT = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.LONG);
   private static final TrackedData<Vector3f> TRACKED_HOME = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.avata360.idle");
   private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.avata360.fly");
   private static final RawAnimation CAM_360 = RawAnimation.begin().thenPlayAndHold("animation.avata360.mode_360");
   private static final RawAnimation CAM_SINGLE = RawAnimation.begin().thenPlayAndHold("animation.avata360.mode_single");
   private static final RawAnimation BAT_INSERT = RawAnimation.begin().thenPlay("animation.avata360.battery_insert");
   private static final RawAnimation BAT_REMOVE = RawAnimation.begin().thenPlay("animation.avata360.battery_remove");
   private static final RawAnimation BAT_INSTALLED = RawAnimation.begin().thenPlayAndHold("animation.avata360.battery_installed");
   private static final RawAnimation BAT_EMPTY = RawAnimation.begin().thenPlayAndHold("animation.avata360.battery_empty");
   public static final int BATTERY_REMOVE_TICKS = 32;
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private boolean cameraWasRotated = false;
   private float pilotYaw;
   private boolean motorsArmed;
   private boolean returningHome;
   private boolean failsafeReturning;
   private boolean emergencyStopped;
   private boolean landing;
   private boolean cruiseActive;
   private double cruiseVX;
   private double cruiseVZ;
   private double cruiseY;
   protected int avoidanceMode = 1;
   private float pilotPitch;
   private float pilotRoll;
   private float throttle;
   private double homeX;
   private double homeY;
   private double homeZ;
   private float homeYaw;
   private double pendingHomeX;
   private double pendingHomeY;
   private double pendingHomeZ;
   private float pendingHomeYaw;
   private boolean homeSetForFlight;
   private long lastSignalTick;
   private int lowThrottleTicks;
   private int groundDistance;
   private int nextSignalPacket;
   private UUID pilotId;
   private static final int SIGNAL_RANGE = 500;
   private static final ChunkTicketType<Integer> FLIGHT_TICKET = ChunkTicketType.create("avatadrone_flight", Integer::compareTo, 40);
   private int flightMode = 1;
   private double enduranceFactor = 1.0;
   private double holdX;
   private double holdY;
   private double holdZ;
   private boolean obstacleDetected;
   private boolean returnPlanValid;
   private final ArrayDeque<ReturnRoutePlanner.Point> returnRoute = new ArrayDeque<>();
   private long nextReturnPlan;
   private static final TrackedData<Integer> RETURN_PHASE = DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private ItemStack installedBattery = ItemStack.EMPTY;
   private double avataCharge = 1.0;

   public float getCameraYaw(float var1) {
      if (!this.cameraYawInitialized) {
         float var2 = this.dataTracker.get(CAMERA_YAW);
         return Float.isFinite(var2) ? var2 : this.getYaw(var1);
      } else {
         return MathHelper.lerpAngleDegrees(var1, this.previousCameraYaw, this.cameraYaw);
      }
   }

   public boolean hasAccident() {
      return this.dataTracker.get(ACCIDENT) != 0;
   }

   public int accidentReason() {
      return this.dataTracker.get(ACCIDENT);
   }

   public Vec3d getAccidentPosition() {
      return this.accidentPosition;
   }

   @Override
   public void move(MovementType movementType, Vec3d movement) {
      Vec3d var3 = this.getPos();
      boolean var4 = this.motorsArmed;
      super.move(movementType, movement);
      if (!this.getWorld().isClient && !this.hasAccident() && var4) {
         Vec3d var5 = this.getPos().subtract(var3);
         boolean var6 = this.horizontalCollision && (Math.abs(movement.x - var5.x) > 1.0E-5 || Math.abs(movement.z - var5.z) > 1.0E-5);
         boolean var7 = this.verticalCollision && Math.abs(movement.y - var5.y) > 1.0E-5 && (movement.y > 0.001 || movement.y < -0.12);
         if (var6 || var7) {
            this.triggerAccident(1);
         }
      }
   }

   private void triggerAccident(int var1) {
      if (!this.getWorld().isClient && !this.hasAccident()) {
         this.accidentPosition = this.getPos();
         this.dataTracker.set(ACCIDENT, var1);
         this.disarm(true);
         this.dataTracker.set(FOUR_G, false);
         ServerWorld var2 = (ServerWorld)this.getWorld();
         var2.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 2.0F, 0.9F);
         var2.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + (double)this.getHeight() * 0.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
         var2.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + (double)this.getHeight() * 0.5, this.getZ(), 30, 0.4, 0.3, 0.4, 0.04);

         for (ServerPlayerEntity var4 : var2.getServer().getPlayerManager().getPlayerList()) {
            for (int var5 = 0; var5 < var4.getInventory().size(); var5++) {
               ItemStack var6 = var4.getInventory().getStack(var5);
               if (var6.getItem() instanceof Rc2Item
                  && var6.hasNbt()
                  && var6.getNbt().containsUuid("DroneUuid")
                  && this.getUuid().equals(var6.getNbt().getUuid("DroneUuid"))) {
                  this.writeAccidentReport(var6);
                  var4.getInventory().markDirty();
                  var4.currentScreenHandler.sendContentUpdates();
               }
            }
         }
      }
   }

   public void writeAccidentReport(ItemStack var1) {
      if (this.hasAccident() && !this.getWorld().isClient) {
         NbtCompound var2 = new NbtCompound();
         var2.putUuid("Drone", this.getUuid());
         var2.putString("Dimension", this.getWorld().getRegistryKey().getValue().toString());
         var2.putInt("Reason", this.accidentReason());
         var2.putDouble("X", this.accidentPosition.x);
         var2.putDouble("Y", this.accidentPosition.y);
         var2.putDouble("Z", this.accidentPosition.z);
         var1.getOrCreateNbt().put("DroneAccident", var2);
      }
   }

   public String getReturnPhaseLabel() {
      return switch (this.dataTracker.get(RETURN_PHASE)) {
         case 1 -> "智能返航 · 规划路线";
         case 2 -> "智能返航 · 爬升避障";
         case 3 -> "智能返航 · 沿航线飞行";
         case 4 -> "智能返航 · 自动降落";
         case 5 -> "返航受阻 · 悬停重规划";
         default -> "自动返航中";
      };
   }

   public AvataDroneEntity(EntityType<? extends PathAwareEntity> var1, World var2) {
      super(var1, var2);
      this.moveControl = new FlightMoveControl(this, 20, true);
      this.setNoGravity(true);
      this.setPersistent();
   }

   public void setPilotYaw(float var1) {
      this.pilotYaw = var1;
   }

   public void setPilotInput(float var1, float var2, float var3, float var4) {
      this.throttle = var1;
      this.pilotYaw = var2;
      this.pilotPitch = var3;
      this.pilotRoll = var4;
   }

   public void applyPilotCommand(DroneControlPayload var1, ServerPlayerEntity var2) {
      if (this.hasControlSignal(var2) && (this.pilotId == null || this.pilotId.equals(var2.getUuid()))) {
         this.lastSignalTick = (long)var2.getServerWorld().getServer().getTicks();
         this.avoidanceMode = Math.max(0, Math.min(2, var1.avoidanceMode()));
         if (this.returningHome && this.failsafeReturning) {
            this.pilotYaw = var1.yaw();
            this.setYaw(this.pilotYaw);
            this.bodyYaw = this.pilotYaw;
            this.headYaw = this.pilotYaw;
            this.returningHome = false;
            this.failsafeReturning = false;
            this.landing = false;
            this.holdX = this.getX();
            this.holdY = this.getY();
            this.holdZ = this.getZ();
            this.dataTracker.set(TRACKED_RETURNING, false);
         }

         if (var1.returnHome()) {
            this.failsafeReturning = false;
            if (this.motorsArmed && !this.returningHome) {
               this.returningHome = true;
               this.returnPlanValid = false;
               this.cruiseActive = false;
               this.dataTracker.set(TRACKED_RETURNING, true);
               this.setPilotInput(0.0F, 0.0F, 0.0F, 0.0F);
               this.pilotId = var2.getUuid();
               var2.sendMessage(Text.translatable("message.djimod.rc2.returning"), true);
            }
         } else if (var1.emergencyStop()) {
            if (this.motorsArmed) {
               this.returningHome = false;
               this.failsafeReturning = false;
               this.landing = false;
               this.returnPlanValid = false;
               this.cruiseActive = false;
               this.returnRoute.clear();
               this.dataTracker.set(TRACKED_RETURNING, false);
               this.dataTracker.set(RETURN_PHASE, 0);
               this.holdX = this.getX();
               this.holdY = this.getY();
               this.holdZ = this.getZ();
               this.setPilotInput(0.0F, this.pilotYaw, 0.0F, 0.0F);
               this.setVelocity(Vec3d.ZERO);
               this.setNoGravity(true);
               this.emergencyStopped = false;
               this.pilotId = var2.getUuid();
               var2.sendMessage(Text.translatable("message.djimod.rc2.emergency_stop"), true);
            }
         } else if (this.motorsArmed) {
            if (this.hasControlSignal(var2) && (this.pilotId == null || this.pilotId.equals(var2.getUuid())) && !this.returningHome && !this.emergencyStopped) {
               this.lastSignalTick = (long)var2.getServerWorld().getServer().getTicks();
               this.groundDistance = Math.round(var1.groundDistance());
               this.flightMode = var1.flightMode();
               this.dataTracker.set(TRACKED_FLIGHT_MODE, this.flightMode);
               if (!var1.cruise() || !this.motorsArmed) {
                  this.cruiseActive = false;
               } else if (!this.cruiseActive) {
                  this.cruiseActive = true;
                  this.cruiseVX = this.getVelocity().x;
                  this.cruiseVZ = this.getVelocity().z;
                  this.cruiseY = this.getY();
               }

               this.setPilotInput(var1.throttle(), this.pilotYaw + var1.yawStick() * this.yawDegreesPerCommand(), var1.pitch(), var1.roll());
               if (Math.abs(var1.pitch()) + Math.abs(var1.roll()) > 0.001F) {
                  this.holdX = this.getX();
                  this.holdZ = this.getZ();
               }

               if (Math.abs(var1.throttle()) > 0.001F) {
                  this.holdY = this.getY();
               }
            }
         } else if (var1.arm() && !this.isCableConnected() && this.canArm() && Math.abs(this.getVelocity().y) < 0.08) {
            this.motorsArmed = true;
            this.emergencyStopped = false;
            this.dataTracker.set(TRACKED_ARMED, true);
            this.pilotId = var2.getUuid();
            this.pendingHomeX = this.getX();
            this.pendingHomeY = this.getY();
            this.pendingHomeZ = this.getZ();
            this.pendingHomeYaw = var2.getYaw();
            this.homeSetForFlight = false;
            this.holdX = this.pendingHomeX;
            this.holdY = this.pendingHomeY;
            this.holdZ = this.pendingHomeZ;
            this.setPilotYaw(this.pendingHomeYaw);
            this.lastSignalTick = (long)var2.getServerWorld().getServer().getTicks();
            var2.sendMessage(Text.translatable("message.djimod.rc2.armed"), true);
         } else if (var1.arm() && this.isCableConnected()) {
            var2.sendMessage(Text.literal("请先拔出充电线再起飞"), true);
         } else if (var1.arm() && this.usesBattery() && !this.hasBattery()) {
            var2.sendMessage(Text.translatable("message.djimod.battery.missing"), true);
         }
      }
   }

   private void disarm(boolean var1) {
      this.motorsArmed = false;
      this.returningHome = false;
      this.returnPlanValid = false;
      this.returnRoute.clear();
      this.dataTracker.set(RETURN_PHASE, 0);
      this.failsafeReturning = false;
      this.emergencyStopped = var1;
      this.landing = false;
      this.cruiseActive = false;
      this.homeSetForFlight = false;
      this.dataTracker.set(TRACKED_ARMED, false);
      this.dataTracker.set(TRACKED_RETURNING, false);
      this.setPilotInput(0.0F, 0.0F, 0.0F, 0.0F);
      this.setVelocity(Vec3d.ZERO);
      this.setNoGravity(false);
      this.pilotId = null;
   }

   private boolean pairedController(ItemStack var1) {
      return Rc2Item.hasPower(var1) && var1.hasNbt() && var1.getNbt().containsUuid("DroneUuid") && this.getUuid().equals(var1.getNbt().getUuid("DroneUuid"));
   }

   public boolean hasFourGSignal() {
      return !this.hasAccident()
         && (this.getWorld().isClient ? this.dataTracker.get(FOUR_G) : FourGNetwork.get((ServerWorld)this.getWorld()).covers(this.getPos()));
   }

   public boolean hasControlSignal(PlayerEntity var1) {
      if (!this.hasAccident() && var1.getWorld() == this.getWorld()) {
         double var2 = DroneSettings.range(this.getWorld());
         return this.squaredDistanceTo(var1) <= var2 * var2 || this.hasFourGSignal();
      } else {
         return false;
      }
   }

   public float getPilotPitch() {
      return this.pilotPitch;
   }

   public float getPilotRoll() {
      return this.pilotRoll;
   }

   public boolean areMotorsArmed() {
      return this.getWorld().isClient ? this.dataTracker.get(TRACKED_ARMED) : this.motorsArmed;
   }

   public boolean isReturningHome() {
      return this.getWorld().isClient ? this.dataTracker.get(TRACKED_RETURNING) : this.returningHome;
   }

   public double getGroundDistance() {
      return (double)this.dataTracker.get(TRACKED_GROUND_DISTANCE).floatValue();
   }

   public double getHomeDistance() {
      if (!this.getWorld().isClient) {
         return this.horizontalHomeDistance();
      } else {
         Vector3f var1 = this.dataTracker.get(TRACKED_HOME);
         return Math.hypot(this.getX() - (double)var1.x, this.getZ() - (double)var1.z);
      }
   }

   public double getHomeX() {
      return this.getWorld().isClient ? (double)this.dataTracker.get(TRACKED_HOME).x : this.homeX;
   }

   public double getHomeY() {
      return this.getWorld().isClient ? (double)this.dataTracker.get(TRACKED_HOME).y : this.homeY;
   }

   public double getHomeZ() {
      return this.getWorld().isClient ? (double)this.dataTracker.get(TRACKED_HOME).z : this.homeZ;
   }

   public boolean hasObstacleWarning() {
      return this.getWorld().isClient ? this.dataTracker.get(TRACKED_OBSTACLE) : this.obstacleDetected;
   }

   public float obstacleFrontDistance() {
      return this.dataTracker.get(OBSTACLE_FRONT);
   }

   public float obstacleBackDistance() {
      return this.dataTracker.get(OBSTACLE_BACK);
   }

   public float obstacleLeftDistance() {
      return this.dataTracker.get(OBSTACLE_LEFT);
   }

   public float obstacleRightDistance() {
      return this.dataTracker.get(OBSTACLE_RIGHT);
   }

   public int getFlightMode() {
      return this.getWorld().isClient ? this.dataTracker.get(TRACKED_FLIGHT_MODE) : this.flightMode;
   }

   protected double modeHorizontalSpeed() {
      return new double[]{8.0, 15.0, 18.0}[this.getFlightMode()];
   }

   protected double effectiveMaxHorizontalSpeed() {
      return this.modeHorizontalSpeed();
   }

   protected double modeVerticalSpeed() {
      return new double[]{3.0, 6.0, 10.0}[this.getFlightMode()];
   }

   protected double modeDescentSpeed() {
      return new double[]{3.0, 6.0, 10.0}[this.getFlightMode()];
   }

   public double getModeHorizontalSpeed() {
      return this.modeHorizontalSpeed();
   }

   public double getModeVerticalSpeed() {
      return this.modeVerticalSpeed();
   }

   public double getModeDescentSpeed() {
      return this.modeDescentSpeed();
   }

   public int getForcedLandingPercent() {
      return 8;
   }

   public String getModelLabel() {
      return "AVATA 360";
   }

   public boolean isCableConnected() {
      return ChargingNetwork.connected(this) != null;
   }

   protected boolean canArm() {
      return (!this.usesBattery() || this.hasBattery()) && (!this.isAvata360() || this.avataCharge > 0.08);
   }

   private boolean isAvata360() {
      return !(this instanceof Mini4ProEntity) && !(this instanceof M400Entity);
   }

   protected float yawDegreesPerCommand() {
      return 6.0F;
   }

   protected double obstacleMargin() {
      return 0.5;
   }

   protected double enduranceFactor() {
      return this.enduranceFactor;
   }

   public double getEnduranceFactor() {
      return this.enduranceFactor;
   }

   private void tickEnduranceFactor() {
      double var1 = Math.abs(this.getVelocity().horizontalLength() * 20.0);
      double var7 = this.getVelocity().y * 20.0;
      double target = EnduranceModel.targetFactor(var1, var7, this.effectiveMaxHorizontalSpeed(),
            this.modeVerticalSpeed(), this.getWorld().isRaining());
      this.enduranceFactor = this.enduranceFactor + (target - this.enduranceFactor) * 0.03;
   }

   public float getBatteryPercent() {
      return this.isAvata360() ? this.dataTracker.get(AVATA_BATTERY) : -1.0F;
   }

   public int getRemainingFlightSeconds() {
      if (!this.isAvata360()) {
         return -1;
      } else {
         double var1 = this.getWorld() != null && this.getWorld().isClient
            ? (double)this.dataTracker.get(AVATA_BATTERY).floatValue() / 100.0
            : this.avataCharge;
         return (int)(var1 * 1380.0 * Math.max(0.25, this.enduranceFactor));
      }
   }

   public long getCameraFrameIntervalNanos() {
      return 0L;
   }

   public boolean usesBattery() {
      return true;
   }

   public Item batteryItem() {
      return this instanceof Mini4ProEntity ? AvataDrone.MINI4PRO_BATTERY : AvataDrone.AVATA360_BATTERY;
   }

   public boolean hasBattery() {
      return this.dataTracker.get(HAS_BATTERY);
   }

   public long getBatteryChangedAt() {
      return this.dataTracker.get(BATTERY_CHANGED_AT);
   }

   public void setHasBattery(boolean var1) {
      this.dataTracker.set(HAS_BATTERY, var1);
      this.dataTracker.set(BATTERY_CHANGED_AT, this.getWorld().getTime());
      if (!var1) {
         this.installedBattery = ItemStack.EMPTY;
         if (this.isAvata360()) {
            this.avataCharge = 0.0;
            this.dataTracker.set(AVATA_BATTERY, 0.0F);
         }
      }
   }

   public void installBattery(ItemStack var1) {
      this.installedBattery = DroneBatteryStorage.accepts(var1, this.batteryItem()) ? DroneBatteryStorage.one(var1) : ItemStack.EMPTY;
      this.setHasBattery(!this.installedBattery.isEmpty());
      if (this.isAvata360() && !this.installedBattery.isEmpty()) {
         this.avataCharge = MathHelper.clamp(DeviceCharging.getCharge(this.installedBattery), 0.0, 1.0);
         this.dataTracker.set(AVATA_BATTERY, (float)(this.avataCharge * 100.0));
      }
   }

   public void updateChargingBattery(ItemStack var1) {
      long var2 = this.getBatteryChangedAt();
      this.installBattery(var1);
      this.dataTracker.set(BATTERY_CHANGED_AT, var2);
   }

   public ItemStack getInstalledBattery() {
      if (!this.hasBattery()) {
         return ItemStack.EMPTY;
      } else {
         ItemStack var1 = this.installedBattery.isEmpty() ? new ItemStack(this.batteryItem()) : DroneBatteryStorage.one(this.installedBattery);
         if (this.isAvata360() && !var1.isEmpty()) {
            var1.getOrCreateNbt().putDouble("BatteryCharge", this.avataCharge);
         }

         return var1;
      }
   }

   protected void requestLowBatteryLanding() {
      if (this.motorsArmed) {
         this.landing = true;
      }
   }

   public boolean isObstacleAvoidanceEnabled() {
      return this.horizontalAvoidanceAvailable();
   }

   public float getCameraPitch(float var1) {
      return this.isMode360() ? -90.0F : this.getPitch(var1);
   }

   public double getCameraVerticalFov() {
      return 70.0;
   }

   protected boolean horizontalAvoidanceAvailable() {
      return this.getFlightMode() != 2 && this.avoidanceMode != 2;
   }

   protected boolean verticalAvoidanceAvailable() {
      return false;
   }

   protected double emergencyStopDistance() {
      return 0.5;
   }

   protected double horizontalSensorRange(double var1) {
      return var1 > 0.5 ? 20.0 : 18.0;
   }

   protected double obstacleSensorRangeForDirection(int var1) {
      return var1 == 0 ? this.horizontalSensorRange(1.0) : (var1 == 1 ? this.horizontalSensorRange(-1.0) : this.horizontalSensorRange(0.0));
   }

   protected double verticalSensorRange(boolean var1) {
      return var1 ? 18.0 : 10.0;
   }

   private double groundHeight() {
      return this.groundHeightAt(this.getX(), this.getY(), this.getZ());
   }

   private double groundHeightAt(double var1, double var3, double var5) {
      double var7 = (double)this.getWidth() / 2.0 - 0.001;
      int var9 = MathHelper.floor(var1 - var7);
      int var10 = MathHelper.floor(var1 + var7);
      int var11 = MathHelper.floor(var5 - var7);
      int var12 = MathHelper.floor(var5 + var7);

      for (int var13 = MathHelper.floor(var3); var13 >= this.getWorld().getBottomY(); var13--) {
         double var14 = Double.NEGATIVE_INFINITY;

         for (int var16 = var9; var16 <= var10; var16++) {
            for (int var17 = var11; var17 <= var12; var17++) {
               BlockPos var18 = new BlockPos(var16, var13, var17);
               if (!this.getWorld().isChunkLoaded(var18)) {
                  return Double.NaN;
               }

               VoxelShape var19 = this.getWorld().getBlockState(var18).getCollisionShape(this.getWorld(), var18, ShapeContext.of(this));

               for (Box var21 : var19.getBoundingBoxes()) {
                  Box var22 = var21.offset((double)var16, (double)var13, (double)var17);
                  if (var22.maxX > var1 - var7
                     && var22.minX < var1 + var7
                     && var22.maxZ > var5 - var7
                     && var22.minZ < var5 + var7
                     && var22.maxY <= var3 + 0.001) {
                     var14 = Math.max(var14, var22.maxY);
                  }
               }
            }
         }

         if (Double.isFinite(var14)) {
            return var14;
         }
      }

      return Double.NaN;
   }

   private boolean landedOnCurrentSurface(double var1) {
      return Double.isFinite(var1) && this.getY() - var1 <= 0.01 && this.hasLandingSupport();
   }

   @Override
   public void tick() {
      this.tickEnduranceFactor();
      if (this.isAvata360() && !this.getWorld().isClient && this.motorsArmed && this.hasBattery()) {
         double var1 = 3.6231884057971014E-5;
         var1 /= Math.max(0.25, this.enduranceFactor);
         this.avataCharge = Math.max(0.0, this.avataCharge - var1);
         this.dataTracker.set(AVATA_BATTERY, (float)(this.avataCharge * 100.0));
         if (this.avataCharge <= 0.08) {
            this.requestLowBatteryLanding();
         }
      }

      super.tick();
      if (this.getWorld().isClient) {
         float var4 = this.dataTracker.get(CAMERA_YAW);
         if (Float.isFinite(var4)) {
            this.previousCameraYaw = this.cameraYawInitialized ? this.cameraYaw : var4;
            this.cameraYaw = var4;
            this.cameraYawInitialized = true;
         }
      } else {
         this.tickFlight();
         this.dataTracker.set(CAMERA_YAW, this.getYaw());
      }
   }

   private void tickCruise() {
      double var1 = Math.toRadians((double)this.pilotYaw);
      double var3 = -Math.sin(var1);
      double var5 = Math.cos(var1);
      double var7 = -Math.cos(var1);
      double var9 = -Math.sin(var1);
      double var11 = var3 * (double)this.pilotPitch + var7 * (double)this.pilotRoll;
      double var13 = var5 * (double)this.pilotPitch + var9 * (double)this.pilotRoll;
      double var15 = Math.sqrt(var11 * var11 + var13 * var13);
      double var17 = this.cruiseVX;
      double var19 = this.cruiseVZ;
      if (var15 > 0.05) {
         double var21 = this.effectiveMaxHorizontalSpeed() / 20.0;
         double var23 = Math.min(1.0, var15 / 0.75);
         var17 = var11 / var15 * var23 * var21;
         var19 = var13 / var15 * var23 * var21;
         this.cruiseVX = var17;
         this.cruiseVZ = var19;
      }

      double var26 = (double)this.throttle * this.modeVerticalSpeed() / 20.0;
      double var27;
      if (Math.abs(var26) > 0.001) {
         this.cruiseY += var26;
         var27 = MathHelper.clamp(var26, -0.6, 0.6);
      } else {
         var27 = MathHelper.clamp((this.cruiseY - this.getY()) * 0.12, -0.25, 0.25);
      }

      Vec3d var25 = this.getVelocity();
      this.setVelocity(var25.add((var17 - var25.x) * 0.18, (var27 - var25.y) * 0.2, (var19 - var25.z) * 0.18));
      this.setYaw(this.pilotYaw);
      this.bodyYaw = this.pilotYaw;
      this.headYaw = this.pilotYaw;
      this.setNoGravity(true);
   }

   private Vec3d rotateHorizontal(Vec3d var1, double var2) {
      double var4 = Math.cos(var2);
      double var6 = Math.sin(var2);
      return new Vec3d(var1.x * var4 - var1.z * var6, 0.0, var1.x * var6 + var1.z * var4);
   }

   private double rayDistanceFrom(Vec3d var1, Vec3d var2) {
      Vec3d var3 = this.getPos().add(0.0, (double)this.getHeight() * 0.55, 0.0).add(var1);
      Vec3d var4 = var3.add(var2);
      BlockHitResult var5 = this.getWorld().raycast(new RaycastContext(var3, var4, ShapeType.COLLIDER, FluidHandling.NONE, this));
      return var5.getType() == Type.MISS ? var2.length() : var5.getPos().distanceTo(var3);
   }

   private double corridorClearance(Vec3d var1, double var2) {
      Vec3d var4 = var1.normalize();
      Vec3d var5 = new Vec3d(-var4.z, 0.0, var4.x);
      double var6 = var2;

      for (double var11 : new double[]{-0.55, 0.0, 0.55}) {
         double var13 = this.rayDistanceFrom(var5.multiply(var11), var4.multiply(var2));
         var6 = Math.min(var6, var13);
      }

      return var6;
   }

   private Vec3d chooseAvoidanceDirection(Vec3d var1, double var2) {
      double[] var4 = new double[]{
         0.0,
         0.2617988,
         -0.2617988,
         0.5235988,
         -0.5235988,
         0.7853982,
         -0.7853982,
         1.0471976,
         -1.0471976,
         1.309,
         -1.309,
         1.5707964,
         -1.5707964,
         1.832596,
         -1.832596
      };
      Vec3d var5 = var1;
      double var6 = -Double.MAX_VALUE;

      for (double var11 : var4) {
         Vec3d var13 = this.rotateHorizontal(var1, var11);
         double var14 = this.corridorClearance(var13, var2);
         double var16 = var14 <= 3.0 ? -1000.0 - Math.abs(var11) : var14 - Math.abs(var11) * 0.45;
         if (var16 > var6) {
            var6 = var16;
            var5 = var13;
         }
      }

      return var5;
   }

   private double avoidanceSafeSpeed(double var1) {
      return var1 <= 3.0 ? 0.0 : Math.sqrt(Math.max(0.0, (var1 - 2.0) * 0.06));
   }

   private boolean largeObstacleField(Vec3d var1, double var2) {
      double[] var4 = new double[]{-1.0471976, -0.5235988, 0.0, 0.5235988, 1.0471976};
      int var5 = 0;
      double var6 = 5.0;

      for (double var11 : var4) {
         Vec3d var13 = this.rotateHorizontal(var1, var11);
         if (this.corridorClearance(var13, var2) < var6 - 0.1) {
            var5++;
         }
      }

      return var5 >= 3;
   }

   private void tickFlight() {
      if (!this.hasAccident() && (this.isTouchingWater() || this.isSubmergedInWater())) {
         this.triggerAccident(2);
      }

      if (!this.hasAccident()) {
         double var1 = this.groundHeight();
         this.dataTracker.set(TRACKED_GROUND_DISTANCE, Double.isFinite(var1) ? (float)Math.max(0.0, this.getY() - var1) : 512.0F);
         if (this.motorsArmed && !this.homeSetForFlight && Double.isFinite(var1) && this.getY() - var1 > 0.1) {
            this.homeX = this.pendingHomeX;
            this.homeY = this.pendingHomeY;
            this.homeZ = this.pendingHomeZ;
            this.homeYaw = this.pendingHomeYaw;
            this.homeSetForFlight = true;
            this.dataTracker.set(TRACKED_HOME, new Vector3f((float)this.homeX, (float)this.homeY, (float)this.homeZ));
         }

         this.dataTracker.set(FOUR_G, this.hasFourGSignal());
         boolean var3 = this.getWorld()
            .getServer()
            .getPlayerManager()
            .getPlayerList()
            .stream()
            .anyMatch(
               var1x -> var1x.getWorld() == this.getWorld()
                     && (this.pairedController(var1x.getMainHandStack()) || this.pairedController(var1x.getOffHandStack()))
            );
         if (this.motorsArmed || var3) {
            ((ServerWorld)this.getWorld()).getChunkManager().addTicket(FLIGHT_TICKET, this.getChunkPos(), 3, this.getId());
         }

         if (this.motorsArmed) {
            ((ServerWorld)this.getWorld()).getChunkManager().addTicket(FLIGHT_TICKET, this.getChunkPos(), 3, this.getId());
            long var4 = (long)this.getWorld().getServer().getTicks();
            ServerPlayerEntity var6 = this.pilotId == null ? null : this.getWorld().getServer().getPlayerManager().getPlayer(this.pilotId);
            if (var6 != null
               && (!this.hasControlSignal(var6) || !var6.getMainHandStack().isOf(AvataDrone.DJI_RC2) && !var6.getOffHandStack().isOf(AvataDrone.DJI_RC2))) {
               if (!this.returningHome) {
                  this.failsafeReturning = true;
                  this.returnPlanValid = false;
               }

               this.returningHome = true;
               this.dataTracker.set(TRACKED_RETURNING, true);
            }

            if (var6 == null || var4 - this.lastSignalTick > 20L) {
               if (!this.returningHome) {
                  this.failsafeReturning = true;
                  this.returnPlanValid = false;
               }

               this.returningHome = true;
               this.dataTracker.set(TRACKED_RETURNING, true);
            }

            if (this.returningHome) {
               this.tickSmartReturn();
            } else if (this.motorsArmed) {
               if (this.cruiseActive) {
                  this.tickCruise();
                  return;
               }

               double var7 = Math.toRadians((double)this.pilotYaw);
               double var9 = -Math.sin(var7);
               double var11 = Math.cos(var7);
               double var13 = -Math.cos(var7);
               double var15 = -Math.sin(var7);
               double var17 = var9 * (double)this.pilotPitch + var13 * (double)this.pilotRoll;
               double var19 = var11 * (double)this.pilotPitch + var15 * (double)this.pilotRoll;
               double var21 = Math.sqrt(var17 * var17 + var19 * var19);
               double var23 = this.effectiveMaxHorizontalSpeed() / 20.0;
               double var25 = Math.min(1.0, var21 / 0.75);
               boolean var27 = this.throttle <= -0.35F && var21 < 0.2;
               double var28 = var21 > 0.001 ? var17 / var21 * var25 * var23 : 0.0;
               double var30 = var21 > 0.001 ? var19 / var21 * var25 * var23 : 0.0;
               this.obstacleDetected = false;
               boolean var32 = !var27 && !this.landing && this.getFlightMode() != 2 && this.avoidanceMode != 2;
               Vec3d var33 = new Vec3d(var9, 0.0, var11);
               Vec3d var34 = new Vec3d(var13, 0.0, var15);
               this.dataTracker.set(OBSTACLE_FRONT, (float)this.rayDistance(var33.multiply(this.obstacleSensorRangeForDirection(0))));
               this.dataTracker.set(OBSTACLE_BACK, (float)this.rayDistance(var33.multiply(-this.obstacleSensorRangeForDirection(1))));
               this.dataTracker.set(OBSTACLE_LEFT, (float)this.rayDistance(var34.multiply(-this.obstacleSensorRangeForDirection(2))));
               this.dataTracker.set(OBSTACLE_RIGHT, (float)this.rayDistance(var34.multiply(this.obstacleSensorRangeForDirection(3))));

               for (int var35 = 0; var35 < 8; var35++) {
                  double var36 = (double)var35 * Math.PI / 4.0;
                  Vec3d var38 = new Vec3d(Math.sin(var36), 0.0, Math.cos(var36));
                  double var39 = this.horizontalSensorRange(var38.dotProduct(var33));
                  if (var32 && this.rayDistance(var38.multiply(var39)) < var39 - 0.1) {
                     this.obstacleDetected = true;
                  }
               }

               if (var32 && this.rayDistance(new Vec3d(0.0, this.verticalSensorRange(true), 0.0)) < this.verticalSensorRange(true) - 0.1) {
                  this.obstacleDetected = true;
               }

               this.dataTracker.set(TRACKED_OBSTACLE, this.obstacleDetected);
               boolean var50 = false;
               boolean var51 = false;
               if (var21 > 0.001 && this.horizontalAvoidanceAvailable()) {
                  Vec3d var37 = new Vec3d(var28, 0.0, var30).normalize();
                  double var54 = this.horizontalSensorRange(var37.dotProduct(var33));
                  if (this.avoidanceMode == 0) {
                     Vec3d var40 = this.chooseAvoidanceDirection(var37, var54);
                     double var41 = this.corridorClearance(var40, var54);
                     if (var41 < var54 - 0.1) {
                        this.obstacleDetected = true;
                        double var43 = this.avoidanceSafeSpeed(var41);
                        double var45 = Math.min(var25 * var23, var43);
                        var28 = var40.x * var45;
                        var30 = var40.z * var45;
                        if (var41 <= this.emergencyStopDistance()) {
                           var50 = true;
                           var28 = 0.0;
                           var30 = 0.0;
                        }
                     } else {
                        var28 = var40.x * var25 * var23;
                        var30 = var40.z * var25 * var23;
                     }

                     double var61 = this.rayDistance(new Vec3d(0.0, 5.0, 0.0));
                     if (var61 > 3.0 && this.largeObstacleField(var37, var54)) {
                        this.obstacleDetected = true;
                        var51 = true;
                        var28 *= 0.2;
                        var30 *= 0.2;
                     }
                  } else if (this.avoidanceMode == 1) {
                     double var58 = this.corridorClearance(var37, var54);
                     if (var58 < var54 - 0.1) {
                        this.obstacleDetected = true;
                        double var42 = Math.min(var25 * var23, this.avoidanceSafeSpeed(var58));
                        var28 = var37.x * var42;
                        var30 = var37.z * var42;
                        if (var58 <= this.emergencyStopDistance()) {
                           var50 = true;
                           var28 = 0.0;
                           var30 = 0.0;
                        }
                     }
                  }
               }

               if (var32) {
                  double var52 = this.getVelocity().x;
                  double var55 = this.getVelocity().z;
                  double var59 = Math.sqrt(var52 * var52 + var55 * var55);
                  if (var59 > 0.06) {
                     Vec3d var62 = new Vec3d(var52 / var59, 0.0, var55 / var59);
                     double var44 = Math.max(this.horizontalSensorRange(1.0), var59 * 40.0);
                     double var46 = this.corridorClearance(var62, var44);
                     double var48 = 2.0 + var59 * var59 / 0.12;
                     if (var46 <= var48) {
                        this.obstacleDetected = true;
                        var28 = 0.0;
                        var30 = 0.0;
                        if (var46 <= 2.5) {
                           var50 = true;
                        }
                     }
                  }
               }

               double var53 = this.throttle >= 0.0F
                  ? (double)this.throttle * this.modeVerticalSpeed() / 20.0
                  : (double)this.throttle * this.modeDescentSpeed() / 20.0;
               if (Math.abs(this.throttle) < 0.001F && !this.returningHome) {
                  double var56 = MathHelper.clamp((this.holdY - this.getY()) * 0.12, -0.25, 0.25);
                  var53 = var56;
               }

               double var57 = Double.isFinite(var1) ? this.getY() - var1 : Double.POSITIVE_INFINITY;
               if ((double)this.throttle < -0.75 && var57 <= Math.max(0.35, -this.getVelocity().y + 0.1)) {
                  this.landing = true;
               }

               if (this.landing) {
                  this.throttle = -1.0F;
                  this.pilotPitch = 0.0F;
                  this.pilotRoll = 0.0F;
                  var28 = 0.0;
                  var30 = 0.0;
                  var53 = Double.isFinite(var1) ? -Math.min(0.04, this.modeDescentSpeed() / 20.0) : 0.0;
                  this.setVelocity(0.0, var53, 0.0);
               }

               if (var21 < 0.001 && !this.returningHome && !this.landing) {
                  var28 = MathHelper.clamp((this.holdX - this.getX()) * 0.08, -0.12, 0.12);
                  var30 = MathHelper.clamp((this.holdZ - this.getZ()) * 0.08, -0.12, 0.12);
               }

               if (var51 && !this.landing) {
                  var28 *= 0.15;
                  var30 *= 0.15;
                  var53 = Math.min(0.48, this.modeVerticalSpeed() / 20.0 * 0.85);
               }

               if (this.verticalAvoidanceAvailable()
                  && Math.abs(var53) > 0.001
                  && !this.landing
                  && (!(var53 < 0.0) || !((double)this.throttle < -0.75) || !(var57 <= 1.0))) {
                  double var60 = Math.signum(var53);
                  double var63 = this.verticalSensorRange(var60 > 0.0);
                  double var64 = this.rayDistance(new Vec3d(0.0, var60 * var63, 0.0));
                  if (var64 < var63 - 0.1) {
                     this.obstacleDetected = true;
                     var53 = var60 * Math.min(Math.abs(var53), Math.sqrt(Math.max(0.0, var64 - 0.5) * 0.04));
                  }
               }

               this.dataTracker.set(TRACKED_OBSTACLE, this.obstacleDetected);
               this.setVelocity(
                  this.getVelocity().add((var28 - this.getVelocity().x) * 0.22, (var53 - this.getVelocity().y) * 0.16, (var30 - this.getVelocity().z) * 0.22)
               );
               if (var50) {
                  this.setVelocity(new Vec3d(this.getVelocity().x * 0.15, this.getVelocity().y, this.getVelocity().z * 0.15));
               }

               this.setYaw(this.pilotYaw);
               this.bodyYaw = this.pilotYaw;
               this.headYaw = this.pilotYaw;
               this.setNoGravity(true);
               if (this.landing && this.landedOnCurrentSurface(var1)) {
                  this.disarm(false);
                  return;
               }
            }
         }
      }
   }

   private double horizontalHomeDistance() {
      return Math.hypot(this.getX() - this.homeX, this.getZ() - this.homeZ);
   }

   private void tickSmartReturn() {
      this.flightMode = 1;
      this.dataTracker.set(TRACKED_FLIGHT_MODE, 1);
      this.landing = false;
      this.pilotPitch = this.pilotRoll = this.throttle = 0.0F;
      this.setNoGravity(true);
      long var1 = this.getWorld().getTime();
      ReturnRoutePlanner.Point var3 = new ReturnRoutePlanner.Point(this.getX(), this.getY(), this.getZ());
      if (!this.returnPlanValid) {
         this.returnRoute.clear();
         this.nextReturnPlan = var1;
         this.returnPlanValid = true;
      }

      boolean var4 = this.horizontalHomeDistance() < 0.25;
      double var5 = var4 ? this.groundHeight() : this.groundHeightAt(this.homeX, (double)(this.getWorld().getTopY() - 1), this.homeZ);
      if (!Double.isFinite(var5)) {
         this.setVelocity(Vec3d.ZERO);
         this.dataTracker.set(RETURN_PHASE, 5);
      } else {
         double var7 = var5 + 2.5;
         if (var4) {
            double var22 = -Math.min(0.08, Math.max(0.01, (this.getY() - var5) * 0.2));
            ReturnRoutePlanner.Point var24 = new ReturnRoutePlanner.Point(this.getX(), Math.max(var5 + 0.03, this.getY() + var22), this.getZ());
            if (this.landedOnCurrentSurface(var5)) {
               this.disarm(false);
            } else if (!(this.getY() - var5 <= 0.1) && !this.landingCorridorClear(var3, var24)) {
               this.setVelocity(Vec3d.ZERO);
               this.dataTracker.set(RETURN_PHASE, 5);
            } else {
               this.dataTracker.set(RETURN_PHASE, 4);
               this.setVelocity(0.0, var22, 0.0);
            }
         } else {
            while (!this.returnRoute.isEmpty() && var3.distance(this.returnRoute.peekFirst()) < 0.15) {
               this.returnRoute.removeFirst();
            }

            if (!this.returnRoute.isEmpty() && !this.returnCorridorClear(var3, this.returnRoute.peekFirst())) {
               this.returnRoute.clear();
               this.nextReturnPlan = var1 + 10L;
            }

            if (this.returnRoute.isEmpty() && var1 >= this.nextReturnPlan) {
               this.dataTracker.set(RETURN_PHASE, 1);
               this.nextReturnPlan = var1 + 40L;
               double var9 = this.horizontalHomeDistance();
               double var11 = var9 > 24.0 ? 24.0 / var9 : 1.0;
               double var13 = MathHelper.lerp(var11, this.getX(), this.homeX);
               double var15 = MathHelper.lerp(var11, this.getZ(), this.homeZ);
               double var17 = var7;
               if (var11 < 1.0) {
                  BlockPos var19 = BlockPos.ofFloored(var13, this.getY(), var15);
                  if (!this.getWorld().isChunkLoaded(var19)) {
                     this.setVelocity(Vec3d.ZERO);
                     this.dataTracker.set(RETURN_PHASE, 5);
                     return;
                  }

                  var17 = Math.max(
                     this.getY(), (double)this.getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, var19.getX(), var19.getZ()) + 3.0
                  );
                  var17 = Math.min(var17, this.getY() + 24.0);
               }

               var17 = Math.min(var17, (double)((float)this.getWorld().getTopY() - this.getHeight() - 1.0F));
               ReturnRoutePlanner.Point var29 = new ReturnRoutePlanner.Point(var13, var17, var15);
               this.returnRoute.addAll(ReturnRoutePlanner.plan(var3, var29, this::returnCorridorClear, 2.0, 20, 2400));
               if (this.returnRoute.isEmpty()) {
                  ReturnRoutePlanner.Point var20 = new ReturnRoutePlanner.Point(this.getX(), this.getY() + 8.0, this.getZ());
                  if (this.getY() + 8.0 + (double)this.getHeight() < (double)this.getWorld().getTopY() && this.returnCorridorClear(var3, var20)) {
                     this.returnRoute.add(var20);
                  }
               }
            }

            if (this.returnRoute.isEmpty()) {
               this.setVelocity(Vec3d.ZERO);
               this.obstacleDetected = true;
               this.dataTracker.set(TRACKED_OBSTACLE, true);
               this.dataTracker.set(RETURN_PHASE, 5);
            } else {
               ReturnRoutePlanner.Point var21 = this.returnRoute.peekFirst();
               Vec3d var10 = new Vec3d(var21.x() - this.getX(), var21.y() - this.getY(), var21.z() - this.getZ());
               double var23 = var10.length();
               double var25 = Math.min(0.55, Math.max(0.1, var23 * 0.55));
               Vec3d var26 = var10.normalize().multiply(var25);
               ReturnRoutePlanner.Point var16 = new ReturnRoutePlanner.Point(this.getX() + var26.x, this.getY() + var26.y, this.getZ() + var26.z);
               if (!this.returnCorridorClear(var3, var16)) {
                  this.setVelocity(Vec3d.ZERO);
                  this.returnRoute.clear();
                  this.nextReturnPlan = var1 + 10L;
                  this.dataTracker.set(RETURN_PHASE, 5);
               } else {
                  this.setVelocity(var26);
                  if (var26.horizontalLength() > 0.001) {
                     this.pilotYaw = MathHelper.lerpAngleDegrees(0.2F, this.pilotYaw, (float)Math.toDegrees(Math.atan2(-var26.x, var26.z)));
                     this.setYaw(this.pilotYaw);
                     this.bodyYaw = this.pilotYaw;
                     this.headYaw = this.pilotYaw;
                  }

                  this.obstacleDetected = false;
                  this.dataTracker.set(TRACKED_OBSTACLE, false);
                  this.dataTracker.set(RETURN_PHASE, var26.y > 0.02 ? 2 : 3);
               }
            }
         }
      }
   }

   private boolean hasLandingSupport() {
      return !this.getWorld().isSpaceEmpty(this, this.getBoundingBox().offset(0.0, -0.2, 0.0));
   }

   private boolean landingCorridorClear(ReturnRoutePlanner.Point var1, ReturnRoutePlanner.Point var2) {
      return this.sweptCorridorClear(var1, var2, 0.02);
   }

   private boolean returnCorridorClear(ReturnRoutePlanner.Point var1, ReturnRoutePlanner.Point var2) {
      return this.sweptCorridorClear(var1, var2, 0.5);
   }

   private boolean sweptCorridorClear(ReturnRoutePlanner.Point var1, ReturnRoutePlanner.Point var2, double var3) {
      int var5 = Math.max(1, (int)Math.ceil(var1.distance(var2) / 0.5));
      Box var6 = null;

      for (int var7 = 0; var7 <= var5; var7++) {
         double var8 = (double)var7 / (double)var5;
         double var10 = MathHelper.lerp(var8, var1.x(), var2.x());
         double var12 = MathHelper.lerp(var8, var1.y(), var2.y());
         double var14 = MathHelper.lerp(var8, var1.z(), var2.z());
         double var16 = (double)this.getWidth() / 2.0 + var3;
         Box var18 = new Box(var10 - var16, var12 - var3, var14 - var16, var10 + var16, var12 + (double)this.getHeight() + var3, var14 + var16);
         if (var6 != null) {
            var18 = var18.union(var6);
         }

         if (var18.minY < (double)this.getWorld().getBottomY()
            || var18.maxY >= (double)this.getWorld().getTopY()
            || !this.getWorld().getWorldBorder().contains(var18)) {
            return false;
         }

         for (int var19 = MathHelper.floor(var18.minX) >> 4; var19 <= MathHelper.floor(var18.maxX) >> 4; var19++) {
            for (int var20 = MathHelper.floor(var18.minZ) >> 4; var20 <= MathHelper.floor(var18.maxZ) >> 4; var20++) {
               if (!this.getWorld().isChunkLoaded(var19, var20)) {
                  return false;
               }
            }
         }

         if (!this.getWorld().isSpaceEmpty(this, var18)) {
            return false;
         }

         var6 = new Box(var10 - var16, var12 - var3, var14 - var16, var10 + var16, var12 + (double)this.getHeight() + var3, var14 + var16);
      }

      return true;
   }

   private double rayDistance(Vec3d var1) {
      Vec3d var2 = this.getPos().add(0.0, (double)this.getHeight() * 0.55, 0.0);
      Vec3d var3 = var2.add(var1);
      BlockHitResult var4 = this.getWorld().raycast(new RaycastContext(var2, var3, ShapeType.COLLIDER, FluidHandling.NONE, this));
      return var4.getType() == Type.MISS ? var1.length() : var4.getPos().distanceTo(var2);
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      return false;
   }

   public static Builder createAttributes() {
      return MobEntity.createMobAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
         .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(CAMERA_YAW, Float.NaN);
      this.dataTracker.startTracking(ACCIDENT, 0);
      this.dataTracker.startTracking(FOUR_G, false);
      this.dataTracker.startTracking(MODE_360, false);
      this.dataTracker.startTracking(TRACKED_ARMED, false);
      this.dataTracker.startTracking(AVATA_BATTERY, 100.0F);
      this.dataTracker.startTracking(TRACKED_RETURNING, false);
      this.dataTracker.startTracking(TRACKED_OBSTACLE, false);
      this.dataTracker.startTracking(RETURN_PHASE, 0);
      this.dataTracker.startTracking(OBSTACLE_FRONT, 20.0F);
      this.dataTracker.startTracking(OBSTACLE_BACK, 20.0F);
      this.dataTracker.startTracking(OBSTACLE_LEFT, 20.0F);
      this.dataTracker.startTracking(OBSTACLE_RIGHT, 20.0F);
      this.dataTracker.startTracking(TRACKED_FLIGHT_MODE, 1);
      this.dataTracker.startTracking(TRACKED_GROUND_DISTANCE, 0.0F);
      this.dataTracker.startTracking(TRACKED_HOME, new Vector3f());
      this.dataTracker.startTracking(HAS_BATTERY, false);
      this.dataTracker.startTracking(BATTERY_CHANGED_AT, -1000L);
   }

   public boolean isMode360() {
      return this.dataTracker.get(MODE_360);
   }

   public void setMode360(boolean var1) {
      this.dataTracker.set(MODE_360, var1);
   }

   @Override
   protected void initGoals() {
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   public boolean cannotDespawn() {
      return true;
   }

   public ActionResult collectIntoInventory(PlayerEntity var1) {
      if (this.getWorld().isClient) {
         return ActionResult.SUCCESS;
      } else if (this.isAlive() && !this.isRemoved() && !var1.isSpectator()) {
         if (!this.isCableConnected() && !this.areMotorsArmed() && this.isOnGround() && !(this.getVelocity().lengthSquared() > 0.01)) {
            int var2 = var1.getInventory().getEmptySlot();
            if (var2 < 0) {
               var1.sendMessage(Text.literal("背包已满，请留出一个空格存放无人机"), true);
               return ActionResult.CONSUME;
            } else {
               Item var3 = this instanceof M400Entity
                  ? AvataDrone.M400_ITEM
                  : (this instanceof Mini4ProEntity ? AvataDrone.MINI4PRO_ITEM : AvataDrone.AVATA360_ITEM);
               ItemStack var4 = new ItemStack(var3);
               NbtCompound var5 = new NbtCompound();
               this.writeCustomDataToNbt(var5);
               var4.getOrCreateNbt().put("AircraftData", var5);
               var4.getOrCreateNbt().putUuid("AircraftUuid", this.getUuid());
               var4.getOrCreateNbt().putBoolean("Powered", this.hasBattery());
               DroneBatteryStorage.write(var4.getOrCreateNbt(), this.getInstalledBattery());
               if (this.hasCustomName()) {
                  var4.setCustomName(this.getCustomName());
               }

               var1.getInventory().setStack(var2, var4);
               var1.getInventory().markDirty();
               var1.currentScreenHandler.sendContentUpdates();
               this.discard();
               var1.sendMessage(Text.literal("无人机已收入背包，电池保留在机内"), true);
               return ActionResult.CONSUME;
            }
         } else {
            var1.sendMessage(Text.literal("请先降落并锁定电机，再收起无人机"), true);
            return ActionResult.CONSUME;
         }
      } else {
         return ActionResult.FAIL;
      }
   }

   @Override
   protected ActionResult interactMob(PlayerEntity player, Hand hand) {
      ItemStack var3 = player.getStackInHand(hand);
      if (player.isSneaking()) {
         return this.collectIntoInventory(player);
      } else if (this.usesBattery() && DroneBatteryStorage.accepts(var3, this.batteryItem())) {
         if (this.hasBattery()) {
            return ActionResult.PASS;
         } else {
            if (!this.getWorld().isClient) {
               this.installBattery(var3);
               var3.decrement(1);
               this.triggerAnim("battery", "insert");
               this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.NEUTRAL, 0.6F, 1.6F);
               player.sendMessage(Text.translatable("message.djimod.battery.inserted"), true);
            }

            return ActionResult.success(this.getWorld().isClient);
         }
      } else if (hand == Hand.MAIN_HAND && var3.isEmpty()) {
         if (!this.getWorld().isClient) {
            this.setMode360(!this.isMode360());
         }

         return ActionResult.success(this.getWorld().isClient);
      } else {
         return super.interactMob(player, hand);
      }
   }

   @Override
   public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
      return false;
   }

   @Override
   protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putInt("AccidentReason", this.accidentReason());
      nbt.putDouble("AccidentX", this.accidentPosition.x);
      nbt.putDouble("AccidentY", this.accidentPosition.y);
      nbt.putDouble("AccidentZ", this.accidentPosition.z);
      nbt.putBoolean("Mode360", this.isMode360());
      nbt.putBoolean("HasBattery", this.hasBattery());
      DroneBatteryStorage.write(nbt, this.getInstalledBattery());
      nbt.putFloat("PilotYaw", this.pilotYaw);
      nbt.putInt("FlightMode", this.getFlightMode());
      nbt.putBoolean("MotorsArmed", this.motorsArmed);
      nbt.putDouble("HomeX", this.homeX);
      nbt.putDouble("HomeY", this.homeY);
      nbt.putDouble("HomeZ", this.homeZ);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      this.setMode360(nbt.getBoolean("Mode360"));
      this.dataTracker.set(HAS_BATTERY, nbt.getBoolean("HasBattery"));
      this.installedBattery = this.hasBattery() ? DroneBatteryStorage.read(nbt, this.batteryItem()) : ItemStack.EMPTY;
      if (this.isAvata360() && !this.installedBattery.isEmpty()) {
         this.avataCharge = MathHelper.clamp(DeviceCharging.getCharge(this.installedBattery), 0.0, 1.0);
         this.dataTracker.set(AVATA_BATTERY, (float)(this.avataCharge * 100.0));
      }

      this.pilotYaw = nbt.getFloat("PilotYaw");
      this.flightMode = nbt.contains("FlightMode") ? MathHelper.clamp(nbt.getInt("FlightMode"), 0, 2) : 1;
      this.dataTracker.set(TRACKED_FLIGHT_MODE, this.flightMode);
      this.motorsArmed = nbt.getBoolean("MotorsArmed");
      this.homeX = nbt.getDouble("HomeX");
      this.homeY = nbt.getDouble("HomeY");
      this.homeZ = nbt.getDouble("HomeZ");
      if (this.motorsArmed) {
         this.motorsArmed = false;
         this.setNoGravity(false);
      }

      this.dataTracker.set(ACCIDENT, MathHelper.clamp(nbt.getInt("AccidentReason"), 0, 2));
      this.accidentPosition = new Vec3d(nbt.getDouble("AccidentX"), nbt.getDouble("AccidentY"), nbt.getDouble("AccidentZ"));
      if (this.hasAccident()) {
         this.disarm(true);
      }
   }

   public void registerControllers(ControllerRegistrar var1) {
      var1.add(
         new AnimationController[]{
            new AnimationController(this, "flight", 4, var1x -> !this.areMotorsArmed() ? var1x.setAndContinue(IDLE) : var1x.setAndContinue(FLY))
         }
      );
      var1.add(new AnimationController[]{new AnimationController(this, "camera", 0, var1x -> {
         if (this.isMode360()) {
            this.cameraWasRotated = true;
            return var1x.setAndContinue(CAM_360);
         } else {
            return this.cameraWasRotated ? var1x.setAndContinue(CAM_SINGLE) : PlayState.STOP;
         }
      })});
      var1.add(
         new AnimationController[]{
            new AnimationController(
                  this, "battery", 0, var1x -> !this.usesBattery() ? PlayState.STOP : var1x.setAndContinue(this.hasBattery() ? BAT_INSTALLED : BAT_EMPTY)
               )
               .triggerableAnim("insert", BAT_INSERT)
               .triggerableAnim("remove", BAT_REMOVE)
         }
      );
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
