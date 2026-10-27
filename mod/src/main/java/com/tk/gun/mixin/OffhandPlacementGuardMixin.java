package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enforces gun and launcher-storage rules for every inventory click path. */
@Mixin(ScreenHandler.class)
public abstract class OffhandPlacementGuardMixin {
	@Inject(method = "method_7593", at = @At("HEAD"), cancellable = true)
	private void tkgun$guardOffhandInsertion(int slotIndex, int button, SlotActionType actionType,
												  PlayerEntity player, CallbackInfo ci) {
		if (slotIndex < 0 || actionType == SlotActionType.QUICK_MOVE) {
			return;
		}
		ScreenHandler handler = (ScreenHandler) (Object) this;
		if (slotIndex >= handler.slots.size()) {
			return;
		}

		Slot clicked = handler.getSlot(slotIndex);
		boolean clickedOffhand = isOffhand(clicked, player);
		ItemStack staged = switch (actionType) {
			case PICKUP, QUICK_CRAFT -> clickedOffhand
					? handler.getCursorStack() : ItemStack.EMPTY;
			case SWAP -> {
				if (clickedOffhand && button >= 0 && button < 9) {
					yield player.getInventory().getStack(button);
				}
				yield button == 40 ? clicked.getStack() : ItemStack.EMPTY;
			}
			default -> ItemStack.EMPTY;
		};
		boolean targetsOffhand = clickedOffhand
				|| (actionType == SlotActionType.SWAP && button == 40);
		if (!targetsOffhand || staged.isEmpty()) {
			return;
		}
		if (TkGun.isGun(staged)) {
			ci.cancel();
			return;
		}
		if (BackpackLauncherRules.hasActiveLauncher(player)
				&& !BackpackLauncherRules.isLaunchable(staged)
				&& !BackpackLauncherRules.isLaunchable(handler.getCursorStack())) {
			ci.cancel();
		}
	}

	private static boolean isOffhand(Slot slot, PlayerEntity player) {
		return slot.inventory == player.getInventory()
				&& slot.getIndex() == PlayerInventory.OFF_HAND_SLOT;
	}
}
