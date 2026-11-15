package com.example.avatadrone.block;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import com.example.avatadrone.signal.FourGNetwork;

public class FourGBaseStationBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // Model bounds: X -0.5..16.5, Z 3..13.5; requested full two-block height.
    private static final net.minecraft.util.shape.VoxelShape NORTH = Block.createCuboidShape(-0.5, 0, 3, 16.5, 32, 13.5);
    private static final net.minecraft.util.shape.VoxelShape EAST = Block.createCuboidShape(2.5, 0, -0.5, 13, 32, 16.5);
    private static final net.minecraft.util.shape.VoxelShape SOUTH = Block.createCuboidShape(-0.5, 0, 2.5, 16.5, 32, 13);
    private static final net.minecraft.util.shape.VoxelShape WEST = Block.createCuboidShape(3, 0, -0.5, 13.5, 32, 16.5);

    @Override public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state,
            net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST; default -> NORTH;
        };
    }

    @Override public net.minecraft.util.shape.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }
    public FourGBaseStationBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return rotate(state, mirror.getRotation(state.get(FACING))); }
    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (world instanceof ServerWorld server) FourGNetwork.get(server).put(pos, state.get(FACING));
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (world instanceof ServerWorld server) {
            if (next.isOf(this)) FourGNetwork.get(server).put(pos, next.get(FACING));
            else FourGNetwork.get(server).remove(pos);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }
}
