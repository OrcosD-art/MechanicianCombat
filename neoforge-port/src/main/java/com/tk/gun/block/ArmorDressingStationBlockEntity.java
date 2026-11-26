package com.tk.gun.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/** Animated armor-dressing workstation and its backpack modification staging. */
public final class ArmorDressingStationBlockEntity extends BlockEntity
		implements GeoBlockEntity, BackpackWorkstationStorage {
	public static BlockEntityType<ArmorDressingStationBlockEntity> TYPE;
	private static final RawAnimation DRESSING =
			RawAnimation.begin().thenPlayAndHold("盔甲着装");
	private static final RawAnimation ACTIVE =
			RawAnimation.begin().thenPlayAndHold("激活");
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);
	private final SimpleInventory backpackInventory =
			new SimpleInventory(BackpackWorkstationStorage.SIZE) {
				@Override
				public void markDirty() {
					ArmorDressingStationBlockEntity.this.markDirty();
				}
			};
	private UUID dressingPlayer;
	private long dressingEndTick;
	private UUID seatEntityUuid;
	private long animationStartTick;
	private String dressingAnimation = "盔甲着装";
	private UUID backpackOwner;
	private UUID backpackBindingId;
	private BackpackBindingState backpackBindingState =
			BackpackBindingState.UNBOUND;

	public ArmorDressingStationBlockEntity(BlockPos pos, BlockState state) {
		super(TYPE, pos, state);
	}

	public ArmorDressingStationBlockEntity.Phase phase() {
		BlockState state = world == null
				? getCachedState() : world.getBlockState(pos);
		if (!state.contains(ArmorDressingStationBlock.PHASE)) {
			return ArmorDressingStationBlockEntity.Phase.INACTIVE;
		}
		return ArmorDressingStationBlockEntity.Phase.from(
				state.get(ArmorDressingStationBlock.PHASE));
	}

	public UUID dressingPlayer() {
		return dressingPlayer;
	}

	public long dressingEndTick() {
		return dressingEndTick;
	}

	public void beginDressing(UUID player, long startTick, long endTick,
			UUID seatUuid, String animationName) {
		dressingPlayer = player;
		dressingEndTick = endTick;
		seatEntityUuid = seatUuid;
		dressingAnimation = animationName == null || animationName.isBlank()
				? "盔甲着装" : animationName;
		setPhase(ArmorDressingStationBlockEntity.Phase.DRESSING,
				startTick);
		markDirty();
	}

	public void finishDressing(UUID seatUuid) {
		dressingPlayer = null;
		dressingEndTick = 0L;
		seatEntityUuid = seatUuid;
		setPhase(ArmorDressingStationBlockEntity.Phase.ACTIVE,
				worldTime());
		markDirty();
	}

	public void cancelDressing() {
		dressingPlayer = null;
		dressingEndTick = 0L;
		discardSeatReference();
		setPhase(ArmorDressingStationBlockEntity.Phase.INACTIVE,
				worldTime());
		markDirty();
	}

	public UUID seatEntityUuid() {
		return seatEntityUuid;
	}

	public UUID backpackOwner() {
		return backpackOwner;
	}

	public UUID backpackBindingId() {
		return backpackBindingId;
	}

	public BackpackBindingState backpackBindingState() {
		return backpackBindingState;
	}

	public UUID bindBackpackOwner(UUID playerUuid) {
		if (playerUuid != null && playerUuid.equals(backpackOwner)) {
			return backpackBindingId;
		}
		backpackOwner = playerUuid;
		backpackBindingId = null;
		backpackBindingState = BackpackBindingState.UNBOUND;
		markDirty();
		return null;
	}

	public void markBackpackEquipped(UUID playerUuid, UUID bindingId) {
		backpackOwner = playerUuid;
		backpackBindingId = bindingId;
		backpackBindingState = BackpackBindingState.EQUIPPED;
		markDirty();
	}

	public void clearBackpackBinding() {
		backpackOwner = null;
		backpackBindingId = null;
		backpackBindingState = BackpackBindingState.UNBOUND;
		markDirty();
	}

	public long animationStartTick() {
		return animationStartTick;
	}

	public String dressingAnimation() {
		return dressingAnimation;
	}

	public double animationSeconds(float partialTick) {
		double elapsedTicks = Math.max(0L,
				worldTime() - animationStartTick()) + partialTick;
		return elapsedTicks / 20.0D;
	}

	public void clearSeat() {
		seatEntityUuid = null;
		markDirty();
	}

	public void discardSeatReference() {
		if (seatEntityUuid != null
				&& world instanceof ServerWorld serverWorld) {
			Entity seat = serverWorld.getEntity(seatEntityUuid);
			if (seat instanceof MinecartEntity minecart) {
				for (Entity passenger :
						List.copyOf(minecart.getPassengerList())) {
					passenger.stopRiding();
				}
				minecart.discard();
			}
		}
		clearSeat();
	}

	public void serverTick() {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (phase() == Phase.DRESSING
				&& !ArmorDressingStationService.isRunning(
						serverWorld, pos)) {
			cancelDressing();
			return;
		}
		if (seatEntityUuid == null) {
			return;
		}
		Entity entity = serverWorld.getEntity(seatEntityUuid);
		if (!(entity instanceof MinecartEntity seat)) {
			clearSeat();
			return;
		}
		boolean tracked = ArmorDressingStationService.isTrackedSeat(
				serverWorld, seatEntityUuid);
		if (phase() == Phase.DRESSING && seat.hasPassengers()) {
			return;
		}
		if (phase() != Phase.ACTIVE || !seat.hasPassengers() || !tracked) {
			if (!tracked || phase() != Phase.ACTIVE) {
				for (Entity passenger :
						List.copyOf(seat.getPassengerList())) {
					passenger.stopRiding();
				}
			}
			seat.discard();
			clearSeat();
		}
	}

	private void setPhase(ArmorDressingStationBlockEntity.Phase phase,
			long startTick) {
		if (world == null || world.isClient() || phase() == phase) {
			return;
		}
		animationStartTick = Math.max(0L, startTick);
		world.setBlockState(pos, world.getBlockState(pos).with(
				ArmorDressingStationBlock.PHASE, phase.ordinal()),
				net.minecraft.block.Block.NOTIFY_ALL);
		if (world instanceof ServerWorld serverWorld) {
			serverWorld.getChunkManager().markForUpdate(pos);
		}
	}

	private long worldTime() {
		return world == null ? animationStartTick() : world.getTime();
	}

	@Override
	public SimpleInventory backpackInventory() {
		return backpackInventory;
	}

	@Override
	public ItemStack backpackForDrops(World world) {
		if (backpackOwner != null && world instanceof ServerWorld serverWorld) {
			net.minecraft.server.network.ServerPlayerEntity player =
					serverWorld.getServer().getPlayerManager()
							.getPlayer(backpackOwner);
			if (player != null) {
				ItemStack equipped =
						BackpackWorkstationBinding.equipped(player);
				if (!equipped.isEmpty()) {
					return equipped;
				}
			}
		}
		return BackpackWorkstationStorage.super.backpackForDrops(world);
	}

	public void dropInventory(World world, BlockPos pos) {
		dropBackpackInventory(world, pos);
		markDirty();
	}

	@Override
	protected void readNbt(NbtCompound nbt,
			RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		dressingPlayer = nbt.contains("DressingPlayer")
				? nbt.getUuid("DressingPlayer") : null;
		dressingEndTick = nbt.getLong("DressingEndTick");
		seatEntityUuid = nbt.contains("SeatEntity")
				? nbt.getUuid("SeatEntity") : null;
		animationStartTick = nbt.getLong("AnimationStartTick");
		dressingAnimation = nbt.contains("DressingAnimation")
				? nbt.getString("DressingAnimation") : "盔甲着装";
		backpackOwner = nbt.contains("BackpackOwner")
				? nbt.getUuid("BackpackOwner") : null;
		backpackBindingId = nbt.contains("BackpackBinding")
				? nbt.getUuid("BackpackBinding") : null;
		backpackBindingState = BackpackBindingState.from(
				nbt.getInt("BackpackBindingState"));
		DefaultedList<ItemStack> loaded = DefaultedList.ofSize(
				backpackInventory.size(), ItemStack.EMPTY);
		Inventories.readNbt(nbt, loaded, registries);
		for (int slot = 0; slot < loaded.size(); slot++) {
			backpackInventory.setStack(slot, loaded.get(slot));
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt,
			RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (dressingPlayer != null) {
			nbt.putUuid("DressingPlayer", dressingPlayer);
		}
		nbt.putLong("DressingEndTick", dressingEndTick);
		if (seatEntityUuid != null) {
			nbt.putUuid("SeatEntity", seatEntityUuid);
		}
		nbt.putLong("AnimationStartTick", animationStartTick);
		nbt.putString("DressingAnimation", dressingAnimation);
		if (backpackOwner != null) {
			nbt.putUuid("BackpackOwner", backpackOwner);
		}
		if (backpackBindingId != null) {
			nbt.putUuid("BackpackBinding", backpackBindingId);
		}
		nbt.putInt("BackpackBindingState",
				backpackBindingState.ordinal());
		Inventories.writeNbt(nbt, backpackInventory.getHeldStacks(),
				registries);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(
			RegistryWrapper.WrapperLookup registries) {
		return createNbt(registries);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "workstation", 0,
				state -> {
					state.getController().transitionLength(0);
					return switch (phase()) {
						case INACTIVE -> PlayState.STOP;
						case DRESSING -> state.setAndContinue(DRESSING);
						case ACTIVE -> state.setAndContinue(ACTIVE);
					};
				}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	public enum Phase {
		INACTIVE,
		DRESSING,
		ACTIVE;

		public static Phase from(int ordinal) {
			Phase[] values = values();
			return values[Math.max(0, Math.min(values.length - 1, ordinal))];
		}
	}

	public enum BackpackBindingState {
		UNBOUND,
		EQUIPPED,
		ROLLBACK;

		public static BackpackBindingState from(int ordinal) {
			BackpackBindingState[] values = values();
			return values[Math.max(0,
					Math.min(values.length - 1, ordinal))];
		}
	}
}
