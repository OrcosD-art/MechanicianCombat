package com.tk.gun.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** One-slot inventory view backed by the equipped combat backpack component. */
public final class BackpackAmmoInventory implements Inventory {
	private final PlayerEntity owner;
	/**
	 * Vanilla slot-merging code mutates the stack returned by {@link #getStack}
	 * and then only calls {@link #markDirty()}. Returning a fresh NBT-decoded
	 * copy on every read therefore silently discarded the added count.
	 */
	private ItemStack backing = ItemStack.EMPTY;
	private ItemStack lastPersisted = ItemStack.EMPTY;
	private boolean initialized;

	public BackpackAmmoInventory(PlayerEntity owner) {
		this.owner = owner;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return backingStack().isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return slot == 0 && BackpackLauncherRules.usesAmmoStorage(owner)
				? backingStack() : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		if (slot != 0 || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stored = backingStack();
		if (stored.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = stored.split(amount);
		persistBacking();
		return removed;
	}

	@Override
	public ItemStack removeStack(int slot) {
		if (slot != 0) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = backingStack().copy();
		backing = ItemStack.EMPTY;
		persistBacking();
		return removed;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		if (slot != 0) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			backing = ItemStack.EMPTY;
			initialized = true;
			persistBacking();
			return;
		}
		if (!BackpackAmmoData.storageAccepts(owner, stack)) {
			return;
		}
		backing = stack.copyWithCount(
				Math.min(stack.getCount(), stack.getMaxCount()));
		initialized = true;
		persistBacking();
	}

	@Override
	public void markDirty() {
		persistBacking();
		if (owner instanceof ServerPlayerEntity serverPlayer) {
			// ScreenHandler already brackets a slot click with
			// disableSyncing()/sendContentUpdates(). Calling syncState() here
			// publishes a half-finished cursor/slot transaction and can restore
			// an emptied slot over the stack being carried by the cursor.
			serverPlayer.getInventory().markDirty();
		}
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return player == owner && owner.isAlive();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return slot == 0 && BackpackAmmoData.storageAccepts(owner, stack);
	}

	@Override
	public int getMaxCountPerStack() {
		return 64;
	}

	@Override
	public void clear() {
		backing = ItemStack.EMPTY;
		initialized = true;
		persistBacking();
	}

	private ItemStack backingStack() {
		if (!initialized) {
			reloadBacking();
		} else {
			ItemStack persisted = BackpackAmmoData.stored(owner);
			if (!ItemStack.areEqual(persisted, lastPersisted)) {
				// A launcher may consume ammunition outside the inventory UI.
				// Reload instead of letting a stale UI mirror restore it.
				reloadBacking();
			}
		}
		return backing;
	}

	private void reloadBacking() {
		backing = BackpackAmmoData.stored(owner).copy();
		lastPersisted = backing.copy();
		initialized = true;
	}

	private void persistBacking() {
		if (!initialized) {
			// markDirty may run before the first getStack call; do not replace
			// persisted ammunition with this inventory's empty initial field.
			return;
		}
		ItemStack backpack = CombatBackpackItem.equippedStack(owner);
		BackpackAmmoData.setStored(backpack, backing,
				owner.getWorld().getRegistryManager());
		lastPersisted = backing.copy();
	}
}
