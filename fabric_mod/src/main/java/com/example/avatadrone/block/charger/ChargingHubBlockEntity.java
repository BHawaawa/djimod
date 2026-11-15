package com.example.avatadrone.block.charger;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.ModChargers;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 充电管家方块实体：3 个槽位、每槽电量 0..100、像真机一样“按顺序充电”（电量最高的先充满）。
 * GeckoLib：每个槽位一个控制器 "slot1".."slot3"
 *   状态动画：slotN_empty / slotN_installed / slotN_charging（循环，正在充电的槽）
 *   触发动画：insert -> slotN_insert，remove -> slotN_remove（服务端 triggerAnim，GeckoLib 自动同步到客户端）
 */
public class ChargingHubBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final boolean[] has = new boolean[3];
    private final int[] charge = new int[3];                 // 0..100 %
    private final ItemStack[] stacks = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};   // 原物品（保留其它 NBT）
    private int chargingSlot = -1;
    private final long[] busyUntil = new long[3];
    private long lastPower;
    private final java.util.UUID[] removingFor = new java.util.UUID[3];

    public ChargingHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModChargers.CHARGING_HUB_BE, pos, state);
    }

    public ChargingHubType type() {
        return ((ChargingHubBlock) getCachedState().getBlock()).type;
    }

    public boolean has(int i) { return has[i]; }
    public boolean hasAny() { return has[0] || has[1] || has[2]; }
    public int charge(int i) { return (int)Math.floor(chargeFraction(i) * 100); }
    public double chargeFraction(int i) { return has[i] ? com.example.avatadrone.item.DeviceCharging.getCharge(stacks[i]) : 0; }
    public boolean isRemoving(int i) { return removingFor[i] != null; }
    public boolean isCharging(int i) { return has[i] && !isRemoving(i) && chargingSlot == i && chargeFraction(i) < 1; }

    public boolean accepts(ItemStack stack) {
        Item battery = Registries.ITEM.get(new Identifier(AvataDrone.MOD_ID, type().batteryItemId));
        return com.example.avatadrone.item.DroneBatteryStorage.accepts(stack, battery);
    }

    // ------------------------------------------------------------------ server logic
    public void insert(int slot, ItemStack stack, PlayerEntity player) {
        if (slot < 0 || slot > 2 || has[slot] || world.getTime() < busyUntil[slot] || !accepts(stack)) return;
        busyUntil[slot] = world.getTime() + 32;
        has[slot] = true;
        stacks[slot] = com.example.avatadrone.item.DroneBatteryStorage.one(stack);
        // 与模组现有逻辑一致：电量在物品根 NBT "BatteryCharge"（0..1，缺省视为满电），见 item/DeviceCharging
        NbtCompound nbt = stack.getNbt();
        charge[slot] = (int)Math.floor(com.example.avatadrone.item.DeviceCharging.getCharge(stacks[slot]) * 100);
        stack.decrement(1);
        triggerAnim("slot" + (slot + 1), "insert");
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.35f, 1.9f);
        sync();
    }

    public void remove(int slot, PlayerEntity player, Hand hand) {
        if (slot < 0 || slot > 2 || !has[slot] || world.getTime() < busyUntil[slot]) return;
        busyUntil[slot] = world.getTime() + 26;
        removingFor[slot] = player.getUuid();
        if (chargingSlot == slot) chargingSlot = -1;
        triggerAnim("slot" + (slot + 1), "remove");
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.35f, 1.9f);
        sync();
    }

    public ItemStack makeStack(int slot) {
        ItemStack s = stacks[slot].isEmpty()
                ? new ItemStack(Registries.ITEM.get(new Identifier(AvataDrone.MOD_ID, type().batteryItemId)))
                : stacks[slot].copy();
        // Preserve full precision and battery metadata from the stored stack.
        return s;
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, ChargingHubBlockEntity be) {
        for (int i = 0; i < 3; i++) {
            if (be.removingFor[i] == null || world.getTime() < be.busyUntil[i]) continue;
            var player = world.getPlayerByUuid(be.removingFor[i]);
            ItemStack out = be.makeStack(i);
            be.stacks[i] = ItemStack.EMPTY; be.has[i] = false; be.charge[i] = 0; be.removingFor[i] = null;
            if (player != null && player.isAlive() && player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos)) <= 36) {
                if (!player.getInventory().insertStack(out)) player.dropItem(out, false);
            } else net.minecraft.util.ItemScatterer.spawn(world, pos.getX()+0.5, pos.getY()+1, pos.getZ()+0.5, out);
            be.sync();
        }
        if (be.chargingSlot >= 0 && world.getTime() - be.lastPower > 2) {
            be.chargingSlot = -1;
            be.sync();
        }
    }

    /** Returns energy actually stored, in Wh. Only the connected power station calls this. */
    public double receiveEnergy(double availableWh, double maxWatts, double hours) {
        int best = -1;
        for (int i = 0; i < 3; i++) {
            double c = com.example.avatadrone.item.DeviceCharging.getCharge(stacks[i]);
            if (has[i] && removingFor[i] == null && world.getTime() >= busyUntil[i] && c < 1 &&
                    (best < 0 || c > com.example.avatadrone.item.DeviceCharging.getCharge(stacks[best]))) best = i;
        }
        if (best < 0) return 0;
        var stack = stacks[best];
        double c = com.example.avatadrone.item.DeviceCharging.getCharge(stack);
        double cap = com.example.avatadrone.item.DeviceCharging.capacityWh(stack);
        double watts = Math.min(maxWatts, com.example.avatadrone.item.DeviceCharging.maxInputW(stack));
        if (c > 0.9) watts *= Math.max(0.15, (1 - c) / 0.1);
        double wh = Math.min(availableWh, Math.min(watts * hours, (1 - c) * cap));
        if (wh <= 0) return 0;
        com.example.avatadrone.item.DeviceCharging.setCharge(stack, c + wh / cap);
        charge[best] = (int)Math.round((c + wh / cap) * 100);
        boolean changed = chargingSlot != best;
        chargingSlot = best;
        lastPower = world.getTime();
        markDirty();
        if (changed || world.getTime() % 10 == 0) sync();
        return wh;
    }

    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    // ------------------------------------------------------------------ NBT / sync
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        for (int i = 0; i < 3; i++) {
            nbt.putBoolean("Has" + i, has[i]);
            nbt.putLong("BusyUntil" + i, busyUntil[i]);
            if (removingFor[i] != null) nbt.putUuid("RemovingFor" + i, removingFor[i]);
            nbt.putInt("Charge" + i, charge[i]);
            if (!stacks[i].isEmpty()) nbt.put("Stack" + i, stacks[i].writeNbt(new NbtCompound()));
        }
        nbt.putInt("ChargingSlot", chargingSlot);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        for (int i = 0; i < 3; i++) {
            has[i] = nbt.getBoolean("Has" + i);
            busyUntil[i] = nbt.getLong("BusyUntil" + i);
            removingFor[i] = nbt.containsUuid("RemovingFor" + i) ? nbt.getUuid("RemovingFor" + i) : null;
            charge[i] = nbt.getInt("Charge" + i);
            stacks[i] = nbt.contains("Stack" + i) ? ItemStack.fromNbt(nbt.getCompound("Stack" + i)) : ItemStack.EMPTY;
        }
        chargingSlot = nbt.contains("ChargingSlot") ? nbt.getInt("ChargingSlot") : -1;
    }

    @Nullable @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        ChargingHubType t = type();               // BlockEntity 构造时已带 cachedState
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            String n = "slot" + (i + 1);
            RawAnimation empty = RawAnimation.begin().thenPlayAndHold(t.anim(n + "_empty"));
            RawAnimation installed = RawAnimation.begin().thenPlayAndHold(t.anim(n + "_installed"));
            RawAnimation charging = RawAnimation.begin().thenLoop(t.anim(n + "_charging"));
            // 触发动画必须 PLAY_ONCE：json 里是 hold_on_last_frame，若按 hold 播放控制器会停在 PAUSED，
            // 触发动画永远不结束、状态动画接不上。PLAY_ONCE 结束后最后一帧 = installed / empty 姿态，无缝衔接。
            RawAnimation insert = RawAnimation.begin().then(t.anim(n + "_insert"), Animation.LoopType.PLAY_ONCE);
            RawAnimation remove = RawAnimation.begin().then(t.anim(n + "_remove"), Animation.LoopType.PLAY_ONCE);
            controllers.add(new AnimationController<>(this, n, 0, state -> state.setAndContinue(
                    !has[slot] ? empty : chargingSlot == slot ? charging : installed))
                    .triggerableAnim("insert", insert)
                    .triggerableAnim("remove", remove));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
