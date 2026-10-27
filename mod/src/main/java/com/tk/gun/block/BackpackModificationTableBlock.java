package com.tk.gun.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.lang.reflect.Proxy;

/**
 * Author-supplied backpack modification table.
 *
 * <p>The workstation reuses vanilla smithing recipes, but uses an authored
 * vertical slot layout and a draggable armor preview.</p>
 */
public final class BackpackModificationTableBlock extends SmithingTableBlock
		implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final VoxelShape NORTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(1.5D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),
			Block.createCuboidShape(2.0D, 2.0D, 5.0D, 14.0D, 16.0D, 14.0D));
	private static final VoxelShape EAST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(2.0D, 0.0D, 1.5D, 14.0D, 2.0D, 14.0D),
			Block.createCuboidShape(2.0D, 2.0D, 2.0D, 11.0D, 16.0D, 14.0D));
	private static final VoxelShape SOUTH_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(2.0D, 0.0D, 2.0D, 14.5D, 2.0D, 14.0D),
			Block.createCuboidShape(2.0D, 2.0D, 2.0D, 14.0D, 16.0D, 11.0D));
	private static final VoxelShape WEST_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.5D),
			Block.createCuboidShape(5.0D, 2.0D, 2.0D, 14.0D, 16.0D, 14.0D));

	public BackpackModificationTableBlock(Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BackpackModificationTableBlockEntity(pos, state);
	}

	@Override
	protected void onStateReplaced(
			BlockState state,
			World world,
			BlockPos pos,
			BlockState newState,
			boolean moved) {
		if (!state.isOf(newState.getBlock())
				&& world.getBlockEntity(pos) instanceof BackpackModificationTableBlockEntity table) {
			table.dropInventory(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		// The model's north face is its front. Point it toward the placing player.
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

	/**
	 * Supply the screen position to the custom smithing handler. The handler keeps
	 * vanilla recipe calculations while allowing this non-vanilla block.
	 */
	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(
			BlockState state,
			World world,
			BlockPos pos) {
		final BlockPos screenPos = pos.toImmutable();
		Object factory = Proxy.newProxyInstance(
				BackpackModificationTableBlock.class.getClassLoader(),
				new Class<?>[]{ExtendedScreenHandlerFactory.class},
				(proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return switch (method.getName()) {
							case "toString" -> "tkgun:backpack_modification_table_factory";
							case "hashCode" -> System.identityHashCode(proxy);
							case "equals" -> proxy == args[0];
							default -> null;
						};
					}
					if (method.getReturnType() == boolean.class) {
						// Fabric ScreenHandlerFactory#shouldCloseCurrentScreen.
						return true;
					}
					if (method.getParameterCount() == 0
							&& Text.class.isAssignableFrom(method.getReturnType())) {
						return Text.translatable("block.tkgun.backpack_modification_table");
					}
					if (method.getParameterCount() == 1
							&& args[0] instanceof ServerPlayerEntity) {
						return screenPos;
					}
					if (method.getParameterCount() == 3) {
						return new com.tk.gun.screen.BackpackModificationTableScreenHandler(
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
