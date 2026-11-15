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
        double hw = width / 2, hd = depth / 2, h = Math.min(16, height);
        VoxelShape ns = VoxelShapes.cuboid((8 - hw) / 16, 0, (8 - hd) / 16, (8 + hw) / 16, h / 16, (8 + hd) / 16);
        VoxelShape ew = VoxelShapes.cuboid((8 - hd) / 16, 0, (8 - hw) / 16, (8 + hd) / 16, h / 16, (8 + hw) / 16);
        for (Direction d : Direction.Type.HORIZONTAL) shapes[d.getHorizontal()] = d.getAxis() == Direction.Axis.Z ? ns : ew;
    }

    public VoxelShape shape(Direction facing) {
        return shapes[facing.getHorizontal()];
    }

    public String anim(String name) {
        return "animation." + model + "." + name;
    }
}
