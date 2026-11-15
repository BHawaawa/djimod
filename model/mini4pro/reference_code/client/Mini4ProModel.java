package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.Mini4ProEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class Mini4ProModel extends GeoModel<Mini4ProEntity> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/mini4pro.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/entity/mini4pro.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/mini4pro.animation.json");

    @Override
    public Identifier getModelResource(Mini4ProEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(Mini4ProEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(Mini4ProEntity animatable) {
        return ANIMATION;
    }

    /** 按速度倾斜 root 骨骼（动画只作用于 drone 骨骼，互不覆盖）。方向反了就对调正负号。 */
    @Override
    public void setCustomAnimations(Mini4ProEntity animatable, long instanceId, AnimationState<Mini4ProEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null || animatable.isOnGround()) return;
        Vec3d v = animatable.getVelocity();
        double yaw = Math.toRadians(animatable.bodyYaw);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
        double strafe = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
        root.setRotX((float) MathHelper.clamp(-forward * 1.6, -0.4, 0.4));
        root.setRotZ((float) MathHelper.clamp(strafe * 1.6, -0.3, 0.3));
    }
}
