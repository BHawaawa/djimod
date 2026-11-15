package com.example.avatadrone.signal;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/** Per-dimension persistent transmitter index; coverage does not depend on loaded station chunks. */
public final class FourGNetwork extends PersistentState {
    private final Map<BlockPos, Direction> stations = new HashMap<>();
    public Map<BlockPos, Direction> snapshot() { return Map.copyOf(stations); }
    public static FourGNetwork get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(FourGNetwork::read, FourGNetwork::new, "avatadrone_4g");
    }
    public void put(BlockPos pos, Direction facing) {
        if (stations.put(pos.toImmutable(), facing) != facing) markDirty();
    }
    public void remove(BlockPos pos) { if (stations.remove(pos) != null) markDirty(); }
    public boolean covers(Vec3d target) {
        for (var entry : stations.entrySet()) {
            BlockPos pos = entry.getKey();
            Direction facing = entry.getValue();
            if (Coverage.contains(target.x - (pos.getX() + .5), target.y - (pos.getY() + .5),
                    target.z - (pos.getZ() + .5), facing.getOffsetX(), facing.getOffsetZ())) return true;
        }
        return false;
    }
    private static FourGNetwork read(NbtCompound nbt) {
        FourGNetwork network = new FourGNetwork();
        NbtList list = nbt.getList("Stations", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound station = list.getCompound(i);
            Direction facing = Direction.byName(station.getString("Facing"));
            if (facing != null && facing.getAxis().isHorizontal())
                network.stations.put(BlockPos.fromLong(station.getLong("Pos")), facing);
        }
        return network;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        stations.forEach((pos, facing) -> {
            NbtCompound station = new NbtCompound();
            station.putLong("Pos", pos.asLong()); station.putString("Facing", facing.getName()); list.add(station);
        });
        nbt.put("Stations", list); return nbt;
    }
}
