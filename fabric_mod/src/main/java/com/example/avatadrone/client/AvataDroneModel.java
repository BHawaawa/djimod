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
        // 电池骨骼：没插电池时隐藏（取出动画播完之后才隐藏，保证 battery_remove 可见）
        CoreGeoBone battery = getAnimationProcessor().getBone("battery");
        if (battery != null) {
            long since = animatable.getWorld().getTime() - animatable.getBatteryChangedAt();
            battery.setHidden(!animatable.hasBattery() && since > AvataDroneEntity.BATTERY_REMOVE_TICKS);
        }
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) return;
        if (!animatable.areMotorsArmed()) { root.setRotX(0); root.setRotZ(0); return; }

        Vec3d v = animatable.getVelocity();
        DroneAttitude attitude = DroneAttitude.calculate(v.x, v.z, animatable.bodyYaw,
                animatable.areMotorsArmed(), true);
        root.setRotX(attitude.pitch());
        root.setRotZ(attitude.roll());
    }
}
