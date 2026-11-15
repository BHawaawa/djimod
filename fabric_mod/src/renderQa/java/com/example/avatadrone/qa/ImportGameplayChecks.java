package com.example.avatadrone.qa;

import com.example.avatadrone.*;
import com.example.avatadrone.block.NoDroneSignBlock;
import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;

/** Server-side checks using real registered items and entities, outside the release source set. */
final class ImportGameplayChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static void run(ServerPlayerEntity player) {
        V201GameplayChecks.run(player);
        var world = player.getServerWorld();
        var battery = new ItemStack(AvataDrone.MINI4PRO_BATTERY_LONG);
        battery.getOrCreateNbt().putString("ImportQa", "retained");
        DeviceCharging.setCharge(battery, .375);
        var mini = AvataDrone.MINI4PRO.create(world);
        mini.installBattery(battery);
        check(mini.hasBattery() && mini.hasLongRangeBattery(), "Long battery installation");
        check(Math.abs(mini.getRemainingFlightSeconds() - 2940 * .375) <= 1, "Long battery endurance");
        var saved = new NbtCompound(); mini.writeCustomDataToNbt(saved);
        var restored = AvataDrone.MINI4PRO.create(world); restored.readCustomDataFromNbt(saved);
        var out = restored.getInstalledBattery();
        check(out.isOf(AvataDrone.MINI4PRO_BATTERY_LONG) && restored.hasLongRangeBattery(), "Battery identity after save/load");
        check("retained".equals(out.getNbt().getString("ImportQa")), "Battery custom NBT retained");
        check(Math.abs(DeviceCharging.getCharge(out) - .375) < 1e-6, "Battery charge retained");
        var legacy = new ItemStack(AvataDrone.MINI4PRO_BATTERY);
        legacy.getOrCreateNbt().putBoolean("LongRangeBattery", true);
        check(DroneTuning.isLongRangeBattery(legacy), "Legacy long battery recognized");
        check(!DroneBatteryStorage.accepts(battery, AvataDrone.AVATA360_BATTERY), "Wrong-model battery rejected");
        var packed = new ItemStack(AvataDrone.MINI4PRO_ITEM);
        DroneBatteryStorage.write(packed.getOrCreateNbt(), out);
        check(Math.abs(DeviceCharging.capacityWh(packed) - 28.41) < 1e-6, "Installed long battery charging capacity");
        var hubPos = new BlockPos(-58, 200, -30);
        world.setBlockState(hubPos, ModChargers.MINI4PRO_HUB.getDefaultState());
        var hub = (ChargingHubBlockEntity)world.getBlockEntity(hubPos);
        check(hub.accepts(battery), "Charging hub accepts long battery");
        hub.insert(0, battery.copy(), player);
        check(hub.makeStack(0).isOf(AvataDrone.MINI4PRO_BATTERY_LONG), "Charging hub preserves long battery item");
        System.out.println("IMPORT_GAMEPLAY_QA PASS long battery identity, NBT, endurance, legacy tags and charger");

        var pos = new BlockPos(-62, 200, -30);
        var standing = AvataDrone.NODRONE_SIGN.getDefaultState().with(NoDroneSignBlock.STANDING, true);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        check(standing.canPlaceAt(world, pos), "Standing sign support");
        check(standing.rotate(BlockRotation.CLOCKWISE_90).get(NoDroneSignBlock.FACING) == Direction.EAST, "Sign rotation");
        check(standing.mirror(BlockMirror.LEFT_RIGHT).get(NoDroneSignBlock.FACING) == Direction.SOUTH, "Sign mirror");
        world.setBlockState(pos, standing);
        world.setBlockState(pos.down(), Blocks.AIR.getDefaultState());
        check(world.getBlockState(pos).isAir(), "Standing sign removed when support breaks");
        check(!AvataDrone.NODRONE_SIGN.getDefaultState().get(NoDroneSignBlock.STANDING), "Legacy wall sign default");

        var settings = DroneSettings.get(world);
        double previous = settings.range();
        settings.setRange(750);
        var far = AvataDrone.AVATA360.create(world);
        far.refreshPositionAndAngles(player.getX()+700, player.getY(), player.getZ(), 0, 0);
        check(far.hasControlSignal(player), "Server uses configured RC range");
        far.setPosition(player.getX()+751, player.getY(), player.getZ());
        check(!far.hasControlSignal(player), "RC range boundary");
        check(settings.writeNbt(new NbtCompound()).getDouble("RcRange") == 750, "Range serialized");
        settings.setRange(-1); check(settings.range() == 500, "Range lower bound");
        settings.setRange(Double.NaN); check(Double.isFinite(settings.range()), "Range NaN rejected");
        settings.setRange(previous);
        System.out.println("IMPORT_GAMEPLAY_QA PASS standing sign, rotation, support, server RC range and validation");
    }
}
