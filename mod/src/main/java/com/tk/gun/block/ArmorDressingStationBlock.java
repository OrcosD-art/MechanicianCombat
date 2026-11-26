package com.tk.gun.block;

import com.tk.gun.item.MechanicianUndersuitItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Authored block-form dressing station and combined workstation entry point. */
public final class ArmorDressingStationBlock extends SmithingTableBlock
		implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final IntProperty PHASE = IntProperty.of(
			"phase", 0, ArmorDressingStationBlockEntity.Phase.values().length - 1);

	private static final VoxelShape BASE = Block.createCuboidShape(
			0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

	public ArmorDressingStationBlock(Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(PHASE, ArmorDressingStationBlockEntity.Phase.INACTIVE.ordinal()));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FACING, PHASE);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return getDefaultState().with(
				FACING,
				context.getHorizontalPlayerFacing());
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
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ArmorDressingStationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
			World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return (tickWorld, tickPos, tickState, blockEntity) -> {
			if (blockEntity instanceof ArmorDressingStationBlockEntity station) {
				station.serverTick();
			}
		};
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos,
			PlayerEntity player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		return interact(serverPlayer, world, pos, 0);
	}

	public static void interactExtended(ServerPlayerEntity player, BlockPos pos,
			int page) {
		interact(player, player.getServerWorld(), pos, page);
	}

	private static ActionResult interact(ServerPlayerEntity player, World world,
			BlockPos pos, int page) {
		if (!(world.getBlockEntity(pos)
				instanceof ArmorDressingStationBlockEntity station)) {
			return ActionResult.PASS;
		}
		if (player.squaredDistanceTo(pos.getX() + 0.5D,
				pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
			return ActionResult.PASS;
		}
		if (station.phase()
				== ArmorDressingStationBlockEntity.Phase.DRESSING) {
			if (!ArmorDressingStationService.isRunning(
					(net.minecraft.server.world.ServerWorld) world, pos)) {
				station.cancelDressing();
			}
			return ActionResult.CONSUME;
		}
		if (station.phase()
				== ArmorDressingStationBlockEntity.Phase.INACTIVE) {
			if (MechanicianUndersuitItem.isWearingFullSet(player)) {
				ArmorDressingStationService.startArmoredEntry(
						player, station);
			} else {
				ArmorDressingStationService.start(player, station);
			}
			return ActionResult.CONSUME;
		}
		if (!ArmorDressingStationService.isMounted(player)) {
			ArmorDressingStationService.mountExisting(player, station);
			return ActionResult.CONSUME;
		}
		openPage(player, pos, page == 1 ? 1 : 0);
		return ActionResult.CONSUME;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos,
			BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
				ArmorDressingStationService.cancelAt(serverWorld, pos);
				if (world.getBlockEntity(pos)
						instanceof ArmorDressingStationBlockEntity station) {
					station.discardSeatReference();
					station.dropInventory(world, pos);
				}
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	public static void openPage(ServerPlayerEntity player, BlockPos pos,
			int page) {
		if (!(player.getWorld().getBlockState(pos)
				.getBlock() instanceof ArmorDressingStationBlock)
				|| player.squaredDistanceTo(
						pos.getX() + 0.5D, pos.getY() + 0.5D,
						pos.getZ() + 0.5D) > 64.0D) {
			return;
		}
		if (!(player.getWorld().getBlockEntity(pos)
				instanceof ArmorDressingStationBlockEntity station)
				|| station.phase()
						!= ArmorDressingStationBlockEntity.Phase.ACTIVE) {
			return;
		}
		if (!ArmorDressingStationService.isMounted(player)) {
			return;
		}
		station.bindBackpackOwner(player.getUuid());
		player.closeHandledScreen();
		player.openHandledScreen(new ExtendedScreenHandlerFactory<BlockPos>() {
			@Override
			public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
				return pos;
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable(
						"block.mechaniciancombat.armor_dressing_station");
			}

			@Override
			public ScreenHandler createMenu(int syncId,
					PlayerInventory inventory, PlayerEntity menuPlayer) {
				return page == 1
						? new com.tk.gun.screen
								.BackpackModificationTableScreenHandler(
										syncId, inventory, pos)
						: new com.tk.gun.screen
								.ArmorModificationTableScreenHandler(
										syncId, inventory, pos);
			}
		});
	}

	public static float displayYaw(BlockState state) {
		return state.get(FACING).asRotation();
	}

	public static boolean isStation(BlockState state) {
		return state.isOf(com.tk.gun.TkGunServer.ARMOR_DRESSING_STATION);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world,
			BlockPos pos, ShapeContext context) {
		return BASE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world,
			BlockPos pos, ShapeContext context) {
		return BASE;
	}

}
