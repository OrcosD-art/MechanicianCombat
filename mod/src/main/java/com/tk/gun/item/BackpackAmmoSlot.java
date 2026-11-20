package com.tk.gun.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/** Player-inventory ammunition slot shown above the occupied offhand. */
public final class BackpackAmmoSlot extends Slot {
	private final PlayerEntity owner;

	public BackpackAmmoSlot(Inventory inventory, PlayerEntity owner, int x, int y) {
		super(inventory, 0, x, y);
		this.owner = owner;
	}

	@Override
	public boolean canInsert(ItemStack stack) {
		return BackpackAmmoData.storageAccepts(owner, stack);
	}

	@Override
	public boolean canTakeItems(PlayerEntity playerEntity) {
		return BackpackLauncherRules.usesAmmoStorage(owner);
	}

	@Override
	public boolean isEnabled() {
		return BackpackLauncherRules.usesAmmoStorage(owner);
	}

	@Override
	public boolean canBeHighlighted() {
		return isEnabled();
	}

	@Override
	public int getMaxItemCount(ItemStack stack) {
		return stack == null ? 0 : stack.getMaxCount();
	}
}
