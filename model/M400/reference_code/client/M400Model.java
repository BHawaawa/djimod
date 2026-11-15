package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.M400Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class M400Model extends GeoModel<M400Entity> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/m400.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/entity/m400.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/m400.animation.json");

    @Override
    public Identifier getModelResource(M400Entity animatable) { return MODEL; }

    @Override
    public Identifier getTextureResource(M400Entity animatable) { return TEXTURE; }

    @Override
    public Identifier getAnimationResource(M400Entity animatable) { return ANIMATION; }

    /** 按速度倾斜 root 骨骼（动画只作用于 drone / prop_* 骨骼，互不覆盖）。方向反了就对调正负号。 */
    @Override
    public void setCustomAnimations(M400Entity animatable, long instanceId, AnimationState<M400Entity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null || animatable.isOnGround()) return;
        Vec3d v = animatable.getVelocity();
        double yaw = Math.toRadians(animatable.bodyYaw);
        double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
        double strafe = v.x * Math.cos(yaw) + v.z * Math.sin(yaw);
        // 大机型，倾角比小无人机小一些
        root.setRotX((float) MathHelper.clamp(-forward * 1.0, -0.3, 0.3));
        root.setRotZ((float) MathHelper.clamp(strafe * 1.0, -0.25, 0.25));
    }
}
