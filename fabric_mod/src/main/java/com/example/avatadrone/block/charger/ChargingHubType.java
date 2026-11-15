package com.example.avatadrone.block.charger;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * 两种充电管家共用一套方块 / 方块实体代码，差别都在这里。
 * 尺寸单位 = 模型像素（1/16 方块），与 tools/gen_charger_*.py 中的常量一致。
 */
public enum ChargingHubType {
    //          模型名                      电池物品 id           槽距   宽     深    外壳高  充满秒数
    AVATA360("avata360_charging_hub", "avata360_battery", 4.28, 13.3, 7.0, 16.0, 90),
    MINI4PRO("mini4pro_charging_hub", "mini4pro_battery", 4.93, 15.67, 7.67, 14.7, 75);

    public static final float WORLD_SCALE = 0.4f;

    public final String model;          // geo / animation / texture 文件名
    public final String batteryItemId;  // 可插入的电池物品（同 modid）
    public final double slotPitch;      // 槽位中心间距（模型 X 方向，slot1 = -X）
    public final double width, depth, height;
    public final int secondsToFull;     // 0 -> 100% 所需秒数（游戏性数值，可自行调整）
    private final VoxelShape[] shapes = new VoxelShape[4];

    ChargingHubType(String model, String batteryItemId, double slotPitch, double width, double depth, double height, int secondsToFull) {
        this.model = model;
        this.batteryItemId = batteryItemId;
        this.slotPitch = slotPitch;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.secondsToFull = secondsToFull;
        double hw = width * WORLD_SCALE / 2, hd = depth * WORLD_SCALE / 2, h = height * WORLD_SCALE;
        VoxelShape ns = VoxelShapes.cuboid((8 - hw) / 16, 0, (8 - hd) / 16, (8 + hw) / 16, h / 16, (8 + hd) / 16);
        VoxelShape ew = VoxelShapes.cuboid((8 - hd) / 16, 0, (8 - hw) / 16, (8 + hd) / 16, h / 16, (8 + hw) / 16);
        for (Direction d : Direction.Type.HORIZONTAL) shapes[d.getHorizontal()] = d.getAxis() == Direction.Axis.Z ? ns : ew;
    }

    /** Model front is -Z. Avata's socket is on the FRONT lower-left, Mini's on the side. */
    public net.minecraft.util.math.Vec3d portOffset(Direction facing) {
        var local = this == AVATA360 ? new net.minecraft.util.math.Vec3d(5.25, 0.78, -3.52)
                : new net.minecraft.util.math.Vec3d(7.855, 1, 0.25);
        return rotate(local.multiply(WORLD_SCALE / 16.0), facing);
    }

    public net.minecraft.util.math.Vec3d portNormal(Direction facing) {
        return rotate(this == AVATA360 ? new net.minecraft.util.math.Vec3d(0, 0, -1)
                : new net.minecraft.util.math.Vec3d(1, 0, 0), facing);
    }

    private static net.minecraft.util.math.Vec3d rotate(net.minecraft.util.math.Vec3d vector, Direction facing) {
        float degrees = switch (facing) {
            case SOUTH -> 180; case WEST -> 90; case EAST -> -90; default -> 0;
        };
        return vector.rotateY((float)Math.toRadians(degrees));
    }

    public VoxelShape shape(Direction facing) {
        return shapes[facing.getHorizontal()];
    }

    public String anim(String name) {
        return "animation." + model + "." + name;
    }
}
