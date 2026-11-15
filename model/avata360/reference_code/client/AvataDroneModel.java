package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class AvataDroneModel extends GeoModel<AvataDroneEntity> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/avata360.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/entity/avata360.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/avata360.animation.json");

    @Override
    public Identifier getModelResource(AvataDroneEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(AvataDroneEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AvataDroneEntity animatable) {
        return ANIMATION;
    }

    /**
     * 按速度让机身前倾 / 侧倾（真实穿越机飞行姿态）。
     * 动画写在 "drone" 骨骼上，这里只改 "root"，两者互不覆盖。
     */
    @Override
    public void setCustomAnimations(AvataDroneEntity animatable, long instanceId, AnimationState<AvataDroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        // 没插电池时隐藏 battery 骨骼（取出动画播完后才隐藏）
        CoreGeoBone battery = getAnimationProcessor().getBone("battery");
        if (battery != null) {
            long since = animatable.getWorld().getTime() - animatable.getBatteryChangedAt();
            battery.setHidden(!animatable.hasBattery() && since > AvataDroneEntity.BATTERY_REMOVE_TICKS);
        }
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) return;

        Vec3d v = animatable.getVelocity();
        double yaw = Math.toRadians(animatable.bodyYaw);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
        double strafe = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);

        // 若在游戏里发现倾斜方向相反，把下面的正负号对调即可
        root.setRotX((float) MathHelper.clamp(-forward * 1.8, -0.45, 0.45));
        root.setRotZ((float) MathHelper.clamp(strafe * 1.8, -0.35, 0.35));
    }
}
