package com.tk.gun.mixin;

import com.tk.gun.item.BackpackAmmoInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the creative survival tab use the same ammunition quick-move rules. */
@Mixin(targets = "net/minecraft/class_481$class_483")
public abstract class CreativeBackpackAmmoQuickMoveMixin {
	@Inject(method = "method_7601", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$quickMoveBackpackAmmo(
			PlayerEntity player, int slotIndex,
			CallbackInfoReturnable<ItemStack> cir) {
		ScreenHandler handler = (ScreenHandler) (Object) this;
		if (slotIndex < 0 || slotIndex >= handler.slots.size()) {
			return;
		}
		Slot clicked = handler.getSlot(slotIndex);
		Slot ammo = null;
		for (Slot candidate : handler.slots) {
			if (candidate.inventory instanceof BackpackAmmoInventory) {
				ammo = candidate;
				break;
			}
		}
		if (ammo == null) {
			return;
		}
		ScreenHandler parent =
				((CreativeScreenHandlerAccessor) (Object) this)
						.mechaniciancombat$getParent();
		if (clicked.inventory instanceof BackpackAmmoInventory) {
			ItemStack moved = parent.quickMove(player, clicked.id);
			parent.sendContentUpdates();
			cir.setReturnValue(moved);
			return;
		}
		ItemStack source = clicked.getStack();
		if (source.isEmpty() || !ammo.canInsert(source)) {
			return;
		}
		ItemStack original = source.copy();
		ammo.insertStack(source);
		if (source.getCount() == original.getCount()) {
			return;
		}
		if (source.isEmpty()) {
			clicked.setStack(ItemStack.EMPTY);
		} else {
			clicked.markDirty();
		}
		ammo.markDirty();
		parent.sendContentUpdates();
		cir.setReturnValue(original);
	}
}
