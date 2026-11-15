package com.example.avatadrone.block.charger;

import com.example.avatadrone.ModChargers;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 大疆双向充电管家（3 槽）。
 * 手持对应电池右键：插入到点击位置的槽（该槽已占用则插入第一个空槽），播放 slotN_insert；
 * 空手右键：取出点击位置的电池（该槽为空则取最后一块），播放 slotN_remove，电量写回物品 NBT "BatteryCharge"（0..1，与模组 DeviceCharging 相同）。
 * 正面 = 模型 -Z，放置时朝向玩家（FACING = 玩家朝向的反方向），GeoBlockRenderer 按 FACING 自动旋转。
 */
public class ChargingHubBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public final ChargingHubType type;

    public ChargingHubBlock(ChargingHubType type, Settings settings) {
        super(settings);
        this.type = type;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return type.shape(state.get(FACING));
    }

    /** 点击位置 -> 槽位 0/1/2（模型 slot1 在 -X）。 */
    public int slotAt(BlockState state, BlockPos pos, Vec3d hit) {
        Vec3d local = hit.subtract(pos.getX() + 0.5, 0, pos.getZ() + 0.5);
        // 模型 +X 在世界中的方向：NORTH +X / SOUTH -X / WEST -Z / EAST +Z（与 GeoBlockRenderer.rotateBlock 一致）
        double mx = switch (state.get(FACING)) {
            case SOUTH -> -local.x;
            case WEST -> -local.z;
            case EAST -> local.z;
            default -> local.x;
        } * 16.0;
        double half = type.slotPitch / 2;
        return mx < -half ? 0 : mx > half ? 2 : 1;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ChargingHubBlockEntity hub)) return ActionResult.PASS;
        int slot = slotAt(state, pos, hit.getPos());
        var stack = player.getStackInHand(hand);
        if (hub.accepts(stack)) {
            if (!world.isClient) hub.insert(slot, stack, player);
            return ActionResult.success(world.isClient);
        }
        if (stack.isEmpty() && hub.hasAny()) {
            if (!world.isClient) hub.remove(slot, player, hand);
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof ChargingHubBlockEntity hub) {
            for (int i = 0; i < 3; i++)
                if (hub.has(i)) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, hub.makeStack(i));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingHubBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> beType) {
        return world.isClient ? null : checkType(beType, ModChargers.CHARGING_HUB_BE, ChargingHubBlockEntity::serverTick);
    }
}
