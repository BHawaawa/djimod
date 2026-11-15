package com.example.avatadrone.item;

import com.example.avatadrone.network.ChargingNetwork;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Redmi K90 (Pro Max, Sound by Bose) handheld phone - GeckoLib item (model/phone/redmi_k90).
 *
 *  Shift + right click : long-press the power key -> boot (REDMI -> Xiaomi HyperOS -> desktop) / shut down
 *  right click         : short press -> screen on / off (system keeps running)
 *  AURO 2000 cable     : with the station's cable pulled out, hold the phone and right-click the station
 *                        (same flow as the RC 2 controller); the screen plays the charging animation.
 *  /redmi app ...      : add / remove desktop apps on the phone in the main hand (see PhoneApps)
 *
 * All state lives in the stack NBT so it is synced to the client automatically:
 *   PowerOn(bool) PowerTime(long world time of the last power toggle) Awake(bool)
 *   Charging(bool) PlugTime(long) ChargeW(int) BatteryCharge(0..1, shared with DeviceCharging)
 *   Apps(list of app ids, desktop grid) Dock(list, max 4) GeckoLibID(long, per-stack animation instance)
 */
public class PhoneItem extends Item implements GeoItem {
    public static final String POWER_ON = "PowerOn", POWER_TIME = "PowerTime", AWAKE = "Awake",
            CHARGING = "Charging", PLUG_TIME = "PlugTime", CHARGE_W = "ChargeW";
    /** lengths (ticks) of animation.redmi_k90.boot / power_off / charge_plug */
    public static final int T_BOOT = 72, T_SHUTDOWN = 36, T_PLUG_FX = 44;
    /** game estimate: 7560 mAh lasts ~4 h of screen-on use and ~48 h asleep (not drained while charging). */
    private static final double DRAIN_AWAKE_PER_S = 1.0 / 14400, DRAIN_SLEEP_PER_S = 1.0 / 172800;

    public enum Screen { OFF, BOOT, HOME, SLEEP, SHUTDOWN, CHARGE_OFF }

    /** world time used by client-side animation predicates (set by the client initializer). */
    public static LongSupplier clientTime = () -> 0L;

    private static final String A = "animation.redmi_k90.";
    private static final RawAnimation OFF = RawAnimation.begin().thenPlayAndHold(A + "screen_off");
    private static final RawAnimation HOME = RawAnimation.begin().thenPlayAndHold(A + "home");
    private static final RawAnimation BOOT = RawAnimation.begin().thenPlayAndHold(A + "boot");
    private static final RawAnimation SHUTDOWN = RawAnimation.begin().thenPlayAndHold(A + "power_off");
    private static final RawAnimation CHARGE_OFF = RawAnimation.begin().thenLoop(A + "charge_off");
    private static final RawAnimation PLUG = RawAnimation.begin().thenPlayAndHold(A + "charge_plug");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public PhoneItem(Settings settings) { super(settings); }

    // ------------------------------------------------------------------ state
    public static boolean isOn(ItemStack s) { return s.hasNbt() && s.getNbt().getBoolean(POWER_ON); }
    public static boolean isCharging(ItemStack s) { return s.hasNbt() && s.getNbt().getBoolean(CHARGING); }
    public static long plugTime(ItemStack s) { return s.hasNbt() ? s.getNbt().getLong(PLUG_TIME) : 0; }
    public static int chargeWatts(ItemStack s) { return s.hasNbt() ? s.getNbt().getInt(CHARGE_W) : 0; }

    public static Screen screen(ItemStack s, long now) {
        NbtCompound n = s.getNbt();
        if (n == null) return Screen.OFF;
        long since = now - n.getLong(POWER_TIME);
        if (!n.getBoolean(POWER_ON)) {
            if (since >= 0 && since < T_SHUTDOWN && n.contains(POWER_TIME)) return Screen.SHUTDOWN;
            return n.getBoolean(CHARGING) ? Screen.CHARGE_OFF : Screen.OFF;
        }
        if (since >= 0 && since < T_BOOT) return Screen.BOOT;
        return n.getBoolean(AWAKE) ? Screen.HOME : Screen.SLEEP;
    }

    /** the HyperOS plug-in pill shows over the desktop for T_PLUG_FX ticks after the plug arrives. */
    public static boolean plugFx(ItemStack s, long now) {
        long t = now - plugTime(s);
        return isCharging(s) && t >= 0 && t < T_PLUG_FX && screen(s, now) == Screen.HOME;
    }

    /** called every tick by PowerStationEntity while its cable feeds this phone (after the plug animation). */
    public static void reportCharging(ItemStack s, int watts, long now) {
        NbtCompound n = s.getOrCreateNbt();
        if (!n.getBoolean(CHARGING)) { n.putBoolean(CHARGING, true); n.putLong(PLUG_TIME, now); }
        if (n.getInt(CHARGE_W) != watts) n.putInt(CHARGE_W, watts);
    }

    private static void setPower(ItemStack s, boolean on, long now) {
        NbtCompound n = s.getOrCreateNbt();
        n.putBoolean(POWER_ON, on);
        n.putLong(POWER_TIME, now);
        n.putBoolean(AWAKE, on);
    }

    // ------------------------------------------------------------------ interaction
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack s = user.getStackInHand(hand);
        if (world.isClient && isOn(s) && !user.isSneaking() && screen(s, clientTime.getAsLong()) == Screen.HOME) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new com.example.avatadrone.client.NasPhoneScreen());
            return TypedActionResult.success(s, true);
        }
        if (!world.isClient) {
            long now = world.getTime();
            Screen st = screen(s, now);
            if (user.isSneaking()) {                               // long press
                if (st == Screen.BOOT || st == Screen.SHUTDOWN) return TypedActionResult.consume(s);
                if (isOn(s)) {
                    setPower(s, false, now);
                    user.sendMessage(Text.translatable("message.djimod.phone.power_off"), true);
                } else if (DeviceCharging.getCharge(s) > 0.005) {
                    setPower(s, true, now);
                    user.sendMessage(Text.translatable("message.djimod.phone.power_on"), true);
                } else {
                    user.sendMessage(Text.translatable("message.djimod.phone.empty"), true);
                    return TypedActionResult.fail(s);
                }
                click(world, user, 0.8f);
            } else if (isOn(s)) {                                  // short press
                if (st == Screen.BOOT) return TypedActionResult.consume(s);
                NbtCompound n = s.getOrCreateNbt();
                n.putBoolean(AWAKE, !n.getBoolean(AWAKE));
                click(world, user, 1.4f);
            } else {
                user.sendMessage(Text.translatable("message.djimod.phone.hint"), true);
            }
        }
        return TypedActionResult.success(s, world.isClient);
    }

    private static void click(World world, PlayerEntity user, float pitch) {
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,
                SoundCategory.PLAYERS, 0.35f, pitch);
    }

    @Override public void inventoryTick(ItemStack s, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(world instanceof ServerWorld server)) return;
        GeoItem.getOrAssignId(s, server);                         // own animation instance per phone
        long now = world.getTime();
        NbtCompound n = s.getOrCreateNbt();
        if (n.getBoolean(CHARGING) && now % 10 == 0) {
            var source = entity instanceof PlayerEntity p ? ChargingNetwork.controllerSource(p, s) : null;
            boolean fed = source != null && source.getEnergyWh() > 0;
            if (!fed) { n.putBoolean(CHARGING, false); n.putInt(CHARGE_W, 0); }
        }
        if (now % 20 != 0 || !n.getBoolean(POWER_ON) || n.getBoolean(CHARGING)) return;
        double c = DeviceCharging.getCharge(s) - (n.getBoolean(AWAKE) ? DRAIN_AWAKE_PER_S : DRAIN_SLEEP_PER_S);
        DeviceCharging.setCharge(s, c);
        if (c <= 0) setPower(s, false, now);                       // battery empty -> shuts down
    }

    @Override public void appendTooltip(ItemStack s, World world, List<Text> tooltip, TooltipContext ctx) {
        tooltip.add(Text.translatable("tooltip.djimod.phone.battery", Math.round(DeviceCharging.getCharge(s) * 100)));
        tooltip.add(Text.translatable("tooltip.djimod.phone.keys"));
        tooltip.add(Text.translatable("tooltip.djimod.phone.charge"));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "screen", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s == null) return state.setAndContinue(HOME);
            return state.setAndContinue(switch (screen(s, clientTime.getAsLong())) {
                case BOOT -> BOOT;
                case HOME -> HOME;
                case SHUTDOWN -> SHUTDOWN;
                case CHARGE_OFF -> CHARGE_OFF;
                default -> OFF;
            });
        }));
        controllers.add(new AnimationController<>(this, "fx", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && plugFx(s, clientTime.getAsLong())) return state.setAndContinue(PLUG);
            return PlayState.STOP;                                 // stopped -> the next plug restarts the pill
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public void createRenderer(java.util.function.Consumer<Object> consumer) {
        consumer.accept(new software.bernie.geckolib.animatable.client.RenderProvider() {
            private com.example.avatadrone.client.PhoneItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.example.avatadrone.client.PhoneItemRenderer();
                return renderer;
            }
        });
    }
}
