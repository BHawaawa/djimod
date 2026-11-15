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
 * 瞄准后 Ctrl+1/2/3 选择槽位：手持对应电池插入，空手取出。Shift+右键插拔充电线。
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
        } * (16.0 / ChargingHubType.WORLD_SCALE);
        double half = type.slotPitch / 2;
        return mx < -half ? 0 : mx > half ? 2 : 1;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof ChargingHubBlockEntity hub)) return ActionResult.PASS;
        if (player.isSneaking() && hand == Hand.MAIN_HAND) {
            if (!world.isClient) com.example.avatadrone.network.ChargingNetwork.toggleHub(player, hub);
            return ActionResult.success(world.isClient);
        }
        if (!world.isClient) player.sendMessage(net.minecraft.text.Text.literal("瞄准管家按 Ctrl+1/2/3：手持电池插入，空手取出；Shift+右键连接充电线"), true);
        return ActionResult.success(world.isClient);
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
