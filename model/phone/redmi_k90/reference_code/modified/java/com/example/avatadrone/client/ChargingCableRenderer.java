package com.example.avatadrone.client;

import com.example.avatadrone.block.charger.ChargingHubBlock;
import com.example.avatadrone.block.charger.ChargingHubType;
import com.example.avatadrone.entity.PowerStationEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A world-space cable follows either real port, including approach and withdrawal. */
public final class ChargingCableRenderer {
    private ChargingCableRenderer() {}
    public static boolean visible(PowerStationEntity e) {
        return e.getTargetKind() != 0;
    }
    private static Vec3d rotate(Vec3d v, float yaw) { return v.rotateY((float)Math.toRadians(yaw)); }
    private static Vec3d stationLocal(PowerStationEntity e, Vec3d p) {
        return rotate(p, 180 - e.getYaw());
    }
    public static void render(PowerStationEntity e, float delta, MatrixStack matrices, VertexConsumerProvider buffers) {
        if (!visible(e)) return;
        Vec3d start = stationLocal(e, new Vec3d(-3.3, 50, -27).multiply(PowerStationRenderer.SCALE / 16));
        Vec3d resting = stationLocal(e, new Vec3d(8, 0.8, -71.2).multiply(PowerStationRenderer.SCALE / 16));
        Vec3d port, normal;
        if (e.getTargetKind() == 1) {
            var pos = e.getTargetBlock(); var state = e.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof ChargingHubBlock hub)) return;
            var facing = state.get(ChargingHubBlock.FACING);
            port = Vec3d.ofBottomCenter(pos).add(hub.type.portOffset(facing)).subtract(e.getPos());
            normal = hub.type.portNormal(facing);
        } else {
            var target = e.getWorld().getEntityById(e.getTargetEntityId());
            if (target == null) return;
            if (e.getTargetKind() == 3 && target instanceof net.minecraft.entity.player.PlayerEntity holder
                    && holder.getMainHandStack().getItem() instanceof com.example.avatadrone.item.PhoneItem) {
                // Redmi K90 held upright in the right hand: USB-C port at the bottom edge, plug comes from below
                normal = new Vec3d(0, -1, 0);
                Vec3d side = rotate(new Vec3d(-0.36, 0, 0.40), -target.getYaw(delta));
                port = target.getLerpedPos(delta).subtract(e.getPos()).add(side).add(0, target.getHeight() * 0.50, 0);
            } else if (e.getTargetKind() == 3) {
                normal = rotate(new Vec3d(0, 0, -1), -target.getYaw(delta));
                Vec3d side = rotate(new Vec3d(-0.28, 0, 0), -target.getYaw(delta));
                port = target.getLerpedPos(delta).subtract(e.getPos()).add(side)
                        .add(0, target.getHeight() * 0.55, 0).add(normal.multiply(0.35));
            } else {
            // Rear service connector; dimensions scale with each aircraft model.
            normal = rotate(new Vec3d(0, 0, -1), -target.getYaw());
            port = target.getPos().subtract(e.getPos()).add(0, target.getHeight() * 0.48, 0)
                    .add(normal.multiply(target.getWidth() * 0.42));
            }
        }
        double elapsed = e.getWorld().getTime() - e.getConnectionTime() + delta;
        boolean plugged = e.getCableState() == PowerStationEntity.PLUGGED;
        double t = Math.max(0, Math.min(1, elapsed / (plugged ? PowerStationEntity.T_PLUG : PowerStationEntity.T_UNPLUG)));
        t = t * t * (3 - 2 * t);
        Vec3d approach = port.add(normal.multiply(0.18));
        Vec3d tip;
        if (plugged) tip = t < 0.7 ? resting.lerp(approach, t / 0.7) : approach.lerp(port, (t - 0.7) / 0.3);
        else tip = t < 0.3 ? port.lerp(approach, t / 0.3) : approach.lerp(resting, (t - 0.3) / 0.7);
        if (e.getCableState() == PowerStationEntity.STOWED && elapsed > PowerStationEntity.T_UNPLUG) {
            double retract = Math.min(1, (elapsed - PowerStationEntity.T_UNPLUG) / PowerStationEntity.T_RETRACT);
            tip = tip.lerp(start, retract * retract * (3 - 2 * retract));
        }
        Vec3d tail = tip.add(normal.multiply(0.11));
        VertexConsumer v = buffers.getBuffer(RenderLayer.getTextBackground());
        Matrix4f m = matrices.peek().getPositionMatrix();
        Vec3d previous = start;
        double sag = Math.min(0.55, start.distanceTo(tail) * 0.16);
        for (int i = 1; i <= 40; i++) {
            double f = i / 40.0;
            Vec3d point = start.lerp(tail, f).add(0, -4 * sag * f * (1 - f), 0);
            point = new Vec3d(point.x, Math.max(0.065, point.y), point.z);
            tube(v, m, previous, point, 0.016, 0xFF24272B);
            previous = point;
        }
        tube(v, m, tail, tip.add(normal.multiply(0.025)), 0.03, 0xFF363B40);
        tube(v, m, tip.add(normal.multiply(0.025)), tip, 0.02, 0xFFD6DCE0);
    }
    private static void tube(VertexConsumer v, Matrix4f m, Vec3d a, Vec3d b, double radius, int color) {
        Vec3d axis = b.subtract(a).normalize();
        Vec3d side = axis.crossProduct(Math.abs(axis.y) > 0.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0)).normalize().multiply(radius);
        Vec3d up = axis.crossProduct(side).normalize().multiply(radius);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4, next = (i + 1) * Math.PI / 4;
            Vec3d u = side.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle)));
            Vec3d w = side.multiply(Math.cos(next)).add(up.multiply(Math.sin(next)));
            vertex(v,m,a.add(u),color); vertex(v,m,b.add(u),color); vertex(v,m,b.add(w),color); vertex(v,m,a.add(w),color);
        }
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3d p, int color) {
        v.vertex(m,(float)p.x,(float)p.y,(float)p.z).color((color>>16)&255,(color>>8)&255,color&255,255)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next();
    }
}
