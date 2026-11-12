package com.tk.gun.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.lang.reflect.Proxy;

/**
 * Armor maintenance workstation using the same interaction and block
 * properties as the backpack modification table.
 */
public final class ArmorModificationTableBlock extends SmithingTableBlock {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	private static final VoxelShape NORTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(3.0D, 0.0D, 0.0D, 13.0D, 2.0D, 16.0D),
			Block.createCuboidShape(7.0D, 1.0D, 0.0D, 9.0D, 12.0D, 16.0D),
			Block.createCuboidShape(3.0D, 12.0D, 0.0D, 13.0D, 14.0D, 16.0D),
			Block.createCuboidShape(3.5D, 12.5D, 0.5D, 12.5D, 15.5D, 4.5D),
			Block.createCuboidShape(3.5D, 12.5D, 11.5D, 12.5D, 15.5D, 15.5D));
	private static final VoxelShape EAST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0D, 0.0D, 3.0D, 16.0D, 2.0D, 13.0D),
			Block.createCuboidShape(0.0D, 1.0D, 7.0D, 16.0D, 12.0D, 9.0D),
			Block.createCuboidShape(0.0D, 12.0D, 3.0D, 16.0D, 14.0D, 13.0D),
			Block.createCuboidShape(0.5D, 12.5D, 3.5D, 4.5D, 15.5D, 12.5D),
			Block.createCuboidShape(11.5D, 12.5D, 3.5D, 15.5D, 15.5D, 12.5D));
	private static final VoxelShape SOUTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(3.0D, 0.0D, 0.0D, 13.0D, 2.0D, 16.0D),
			Block.createCuboidShape(7.0D, 1.0D, 0.0D, 9.0D, 12.0D, 16.0D),
			Block.createCuboidShape(3.0D, 12.0D, 0.0D, 13.0D, 14.0D, 16.0D),
			Block.createCuboidShape(3.5D, 12.5D, 11.5D, 12.5D, 15.5D, 15.5D),
			Block.createCuboidShape(3.5D, 12.5D, 0.5D, 12.5D, 15.5D, 4.5D));
	private static final VoxelShape WEST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.0D, 0.0D, 3.0D, 16.0D, 2.0D, 13.0D),
			Block.createCuboidShape(0.0D, 1.0D, 7.0D, 16.0D, 12.0D, 9.0D),
			Block.createCuboidShape(0.0D, 12.0D, 3.0D, 16.0D, 14.0D, 13.0D),
			Block.createCuboidShape(11.5D, 12.5D, 3.5D, 15.5D, 15.5D, 12.5D),
			Block.createCuboidShape(0.5D, 12.5D, 3.5D, 4.5D, 15.5D, 12.5D));

	public ArmorModificationTableBlock(Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return getDefaultState().with(
				FACING,
				context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(
			BlockState state,
			World world,
			BlockPos pos) {
		final BlockPos screenPos = pos.toImmutable();
		Object factory = Proxy.newProxyInstance(
				ArmorModificationTableBlock.class.getClassLoader(),
				new Class<?>[]{ExtendedScreenHandlerFactory.class},
				(proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return switch (method.getName()) {
							case "toString" -> "mechaniciancombat:armor_modification_table_factory";
							case "hashCode" -> System.identityHashCode(proxy);
							case "equals" -> proxy == args[0];
							default -> null;
						};
					}
					if (method.getReturnType() == boolean.class) {
						return true;
					}
					if (method.getParameterCount() == 0
							&& Text.class.isAssignableFrom(method.getReturnType())) {
						return Text.translatable("block.mechaniciancombat.armor_modification_table");
					}
					if (method.getParameterCount() == 1
							&& args[0] instanceof ServerPlayerEntity) {
						return screenPos;
					}
					if (method.getParameterCount() == 3) {
						return new com.tk.gun.screen.ArmorModificationTableScreenHandler(
								(int) args[0],
								(PlayerInventory) args[1],
								screenPos);
					}
					return null;
				});
		return (NamedScreenHandlerFactory) factory;
	}

	@Override
	protected VoxelShape getOutlineShape(
			BlockState state,
			BlockView world,
			BlockPos pos,
			ShapeContext context) {
		return shapeFor(state);
	}

	@Override
	protected VoxelShape getCollisionShape(
			BlockState state,
			BlockView world,
			BlockPos pos,
			ShapeContext context) {
		return shapeFor(state);
	}

	private static VoxelShape shapeFor(BlockState state) {
		return switch (state.get(FACING)) {
			case EAST -> EAST_SHAPE;
			case SOUTH -> SOUTH_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}
}
