package com.tk.gun.mixin;

import com.tk.gun.item.BackpackAmmoInventory;
import com.tk.gun.item.BackpackAmmoSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the backpack ammunition slot without changing the persistent handler id. */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerBackpackAmmoSlotMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mechaniciancombat$addBackpackAmmoSlot(
			PlayerInventory inventory, boolean onServer, PlayerEntity owner,
			CallbackInfo ci) {
		((ScreenHandlerAddSlotAccessor) (Object) this).mechaniciancombat$addSlot(
				new BackpackAmmoSlot(
				new BackpackAmmoInventory(owner), owner, 77, 44));
	}

	@Inject(method = "method_7601", at = @At("HEAD"))
	private void mechaniciancombat$quickMoveIntoAmmoSlot(
			PlayerEntity player, int slotIndex,
			CallbackInfoReturnable<ItemStack> cir) {
		ScreenHandler handler = (ScreenHandler) (Object) this;
		if (slotIndex < 0 || slotIndex >= handler.slots.size()) {
			return;
		}
		Slot clicked = handler.getSlot(slotIndex);
		if (clicked.inventory instanceof BackpackAmmoInventory) {
			return;
		}
		for (Slot candidate : handler.slots) {
			if (!(candidate instanceof BackpackAmmoSlot)
					|| candidate.getStack().isEmpty()) {
				continue;
			}
			ItemStack source = clicked.getStack();
			if (!source.isEmpty() && candidate.canInsert(source)) {
				candidate.insertStack(source);
				if (source.isEmpty()) {
					clicked.setStack(ItemStack.EMPTY);
				} else {
					clicked.markDirty();
				}
			}
			return;
		}
		for (Slot candidate : handler.slots) {
			if (candidate instanceof BackpackAmmoSlot) {
				ItemStack source = clicked.getStack();
				if (!source.isEmpty() && candidate.canInsert(source)) {
					candidate.insertStack(source);
					if (source.isEmpty()) {
						clicked.setStack(ItemStack.EMPTY);
					} else {
						clicked.markDirty();
					}
				}
				return;
			}
		}
	}
}
