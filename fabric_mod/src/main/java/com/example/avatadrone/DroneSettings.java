package com.example.avatadrone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Server-owned, world-persistent RC range; 4G retains its directional 500 m rule. */
public final class DroneSettings extends PersistentState {
    public static final double DEFAULT_RC_RANGE = 10000;
    public static final double MIN_RC_RANGE = 500;
    public static final double MAX_RC_RANGE = 10000;
    private double rcRange = DEFAULT_RC_RANGE;

    public static double validatedRange(double value) {
        return Double.isFinite(value) ? Math.max(MIN_RC_RANGE, Math.min(MAX_RC_RANGE, value)) : DEFAULT_RC_RANGE;
    }
    public static DroneSettings get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                DroneSettings::read, DroneSettings::new, "djimod_flight_settings");
    }
    public static double range(World world) {
        return world instanceof ServerWorld serverWorld ? get(serverWorld).rcRange
                : com.example.avatadrone.network.DroneSettingsNetworking.clientRange;
    }
    public double range() { return rcRange; }
    public void setRange(double value) { rcRange = validatedRange(value); markDirty(); }
    private static DroneSettings read(NbtCompound nbt) {
        DroneSettings settings = new DroneSettings();
        if (nbt.contains("RcRange")) settings.rcRange = validatedRange(nbt.getDouble("RcRange"));
        return settings;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putDouble("RcRange", rcRange);
        return nbt;
    }
}
