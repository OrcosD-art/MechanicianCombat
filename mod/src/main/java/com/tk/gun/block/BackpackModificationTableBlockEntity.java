package com.tk.gun.block;

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
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.getStack(slot);
				if (!stack.isEmpty()) {
					Block.dropStack(world, pos, stack);
				}
			}
			inventory.clear();
			markDirty();
		}
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
