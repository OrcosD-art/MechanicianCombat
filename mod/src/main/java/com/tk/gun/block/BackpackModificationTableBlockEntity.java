package com.tk.gun.block;

import com.tk.gun.item.BackpackPartItem;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Persistent backpack and component inventory for the modification table. */
public final class BackpackModificationTableBlockEntity extends BlockEntity {
	public static final int BACKPACK_SLOT = 0;
	public static final int PART_SLOT_START = 1;
	public static final int PART_SLOT_COUNT = 4;
	public static final int SIZE = PART_SLOT_START + PART_SLOT_COUNT;
	public static BlockEntityType<BackpackModificationTableBlockEntity> TYPE;

	private final SimpleInventory inventory = new SimpleInventory(SIZE) {
		@Override
		public void markDirty() {
			BackpackModificationTableBlockEntity.this.markDirty();
		}
	};

	public BackpackModificationTableBlockEntity(BlockPos pos, BlockState state) {
		super(TYPE, pos, state);
	}

	public SimpleInventory inventory() {
		return inventory;
	}

	public void dropInventory(World world, BlockPos pos) {
		if (world instanceof ServerWorld) {
			ItemStack backpack = inventory.getStack(BACKPACK_SLOT);
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.getStack(slot);
				if (!stack.isEmpty()
						&& !isInstalledPartAlreadyStored(backpack, stack, slot)) {
					Block.dropStack(world, pos, stack);
				}
			}
			inventory.clear();
			markDirty();
		}
	}

	private static boolean isInstalledPartAlreadyStored(ItemStack backpack,
														ItemStack stack,
														int inventorySlot) {
		if (!(backpack.getItem() instanceof CombatBackpackItem)
				|| !(stack.getItem() instanceof BackpackPartItem part)) {
			return false;
		}
		int partSlot = inventorySlot - PART_SLOT_START;
		return switch (part.kind()) {
			case CORE -> BackpackUpgradeData.hasCore(backpack)
					&& partSlot == storedSlot(
							BackpackUpgradeData.coreSlot(backpack), 0);
			case LAUNCHER, AUTO_LAUNCHER, SUMMON_ANTENNA, JUMP_THRUSTER,
					BATTLE_BANNER ->
					BackpackUpgradeData.hasLauncher(backpack)
							&& partSlot == storedSlot(
									BackpackUpgradeData.launcherSlot(backpack), 1)
							&& part.kind() == kindFor(
									BackpackUpgradeData.launcherType(backpack));
			case MISSILE_BOX -> BackpackUpgradeData.hasMissileBox(backpack)
					&& partSlot == storedSlot(
							BackpackUpgradeData.missileBoxSlot(backpack), 3);
		};
	}

	private static int storedSlot(int stored, int fallback) {
		return stored >= 0 && stored < PART_SLOT_COUNT ? stored : fallback;
	}

	private static BackpackPartItem.Kind kindFor(
			BackpackUpgradeData.LauncherType type) {
		return switch (type) {
			case AUTO -> BackpackPartItem.Kind.AUTO_LAUNCHER;
			case SUMMON_ANTENNA -> BackpackPartItem.Kind.SUMMON_ANTENNA;
			case JUMP_THRUSTER -> BackpackPartItem.Kind.JUMP_THRUSTER;
			case BATTLE_BANNER -> BackpackPartItem.Kind.BATTLE_BANNER;
			case PORTABLE, NONE -> BackpackPartItem.Kind.LAUNCHER;
		};
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		DefaultedList<ItemStack> loaded = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
		Inventories.readNbt(nbt, loaded, registries);
		for (int slot = 0; slot < SIZE; slot++) {
			inventory.setStack(slot, loaded.get(slot));
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		Inventories.writeNbt(nbt, inventory.getHeldStacks(), registries);
	}
}
