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
    public Identifier getModelResource(M400Entity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(M400Entity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(M400Entity animatable) {
        return ANIMATION;
    }

    /** 按速度倾斜 root 骨骼（动画只作用于 drone 骨骼，互不覆盖）。方向反了就对调正负号。 */
    @Override
    public void setCustomAnimations(M400Entity animatable, long instanceId, AnimationState<M400Entity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) return;
        if (!animatable.areMotorsArmed()) { root.setRotX(0); root.setRotZ(0); return; }
        Vec3d v = animatable.getVelocity();
        DroneAttitude attitude = DroneAttitude.calculate(v.x, v.z, animatable.bodyYaw,
                animatable.areMotorsArmed(), false);
        root.setRotX(attitude.pitch());
        root.setRotZ(attitude.roll());
    }
}
