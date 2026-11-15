package com.example.avatadrone.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class NoDroneSignBlock
extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty STANDING = BooleanProperty.of("standing");
    private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(Block.createCuboidShape(1.0, 2.0, 14.0, 15.0, 16.0, 16.0), Block.createCuboidShape(3.0, 6.0, 16.0, 5.0, 8.0, 17.0), Block.createCuboidShape(11.0, 6.0, 16.0, 13.0, 8.0, 17.0));
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.union(Block.createCuboidShape(1.0, 2.0, 0.0, 15.0, 16.0, 2.0), Block.createCuboidShape(11.0, 6.0, -1.0, 13.0, 8.0, 0.0), Block.createCuboidShape(3.0, 6.0, -1.0, 5.0, 8.0, 0.0));
    private static final VoxelShape SHAPE_EAST = VoxelShapes.union(Block.createCuboidShape(0.0, 2.0, 1.0, 2.0, 16.0, 15.0), Block.createCuboidShape(-1.0, 6.0, 3.0, 0.0, 8.0, 5.0), Block.createCuboidShape(-1.0, 6.0, 11.0, 0.0, 8.0, 13.0));
    private static final VoxelShape SHAPE_WEST = VoxelShapes.union(Block.createCuboidShape(14.0, 2.0, 1.0, 16.0, 16.0, 15.0), Block.createCuboidShape(16.0, 6.0, 11.0, 17.0, 8.0, 13.0), Block.createCuboidShape(16.0, 6.0, 3.0, 17.0, 8.0, 5.0));
    private static final VoxelShape SHAPE_STANDING_Z = VoxelShapes.union(Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 1.0, 12.0), Block.createCuboidShape(7.0, 0.0, 7.0, 9.0, 12.0, 9.0), Block.createCuboidShape(1.0, 4.0, 7.0, 15.0, 16.0, 9.0));
    private static final VoxelShape SHAPE_STANDING_X = VoxelShapes.union(Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 1.0, 12.0), Block.createCuboidShape(7.0, 0.0, 7.0, 9.0, 12.0, 9.0), Block.createCuboidShape(7.0, 4.0, 1.0, 9.0, 16.0, 15.0));

    public NoDroneSignBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState((BlockState)((BlockState)this.getStateManager().getDefaultState().with(FACING, Direction.NORTH)).with(STANDING, Boolean.FALSE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STANDING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState blockState;
        Direction direction = ctx.getSide();
        if (direction == Direction.UP) {
            blockState = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(STANDING, true);
        } else if (direction != Direction.DOWN) {
            blockState = (BlockState)((BlockState)this.getDefaultState().with(FACING, direction)).with(STANDING, Boolean.FALSE);
        } else {
            return null;
        }
        return blockState.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? blockState : null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction direction = state.get(FACING);
        if (state.get(STANDING).booleanValue()) {
            BlockPos blockPos = pos.offset(Direction.DOWN);
            return world.getBlockState(blockPos).isSideSolidFullSquare(world, blockPos, Direction.UP);
        }
        BlockPos blockPos = pos.offset(direction.getOpposite());
        return world.getBlockState(blockPos).isSideSolidFullSquare(world, blockPos, direction);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(STANDING) != false ? direction == Direction.DOWN && !state.canPlaceAt(world, pos) : direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction direction = state.get(FACING);
        if (state.get(STANDING).booleanValue()) {
            return direction == Direction.EAST || direction == Direction.WEST ? SHAPE_STANDING_X : SHAPE_STANDING_Z;
        }
        if (direction == Direction.SOUTH) {
            return SHAPE_SOUTH;
        }
        if (direction == Direction.EAST) {
            return SHAPE_EAST;
        }
        if (direction == Direction.WEST) {
            return SHAPE_WEST;
        }
        return SHAPE_NORTH;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return this.getOutlineShape(state, world, pos, context);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return (BlockState)state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return this.rotate(state, mirror.getRotation(state.get(FACING)));
    }
}

