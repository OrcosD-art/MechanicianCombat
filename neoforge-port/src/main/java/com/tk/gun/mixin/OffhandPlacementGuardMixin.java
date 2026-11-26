package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackLauncherRules;
import com.tk.gun.item.MechanicianArmorItem;
import com.tk.gun.item.MechanicianUndersuitItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
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
	private void mechaniciancombat$guardOffhandInsertion(int slotIndex, int button, SlotActionType actionType,
												  PlayerEntity player, CallbackInfo ci) {
		if (slotIndex < 0) {
			return;
		}
		ScreenHandler handler = (ScreenHandler) (Object) this;
		if (slotIndex >= handler.slots.size()) {
			return;
		}

		Slot clicked = handler.getSlot(slotIndex);
		if (actionType == SlotActionType.QUICK_MOVE
				&& handler instanceof PlayerScreenHandler playerHandler
				&& isMechanicianEquipment(clicked.getStack())) {
			if (!isPlayerArmorSlot(clicked, player)) {
				quickMoveWithinPlayerInventory(
						playerHandler, clicked, player);
			}
			ci.cancel();
			return;
		}
		ItemStack hotbarSwap = actionType == SlotActionType.SWAP
				&& button >= 0 && button < 9
						? player.getInventory().getStack(button)
						: ItemStack.EMPTY;
		if (isPlayerArmorSlot(clicked, player)
				&& (isMechanicianEquipment(clicked.getStack())
						|| isMechanicianEquipment(
								handler.getCursorStack())
						|| isMechanicianEquipment(hotbarSwap))
				) {
			ci.cancel();
			return;
		}
		boolean clickedOffhand = isOffhand(clicked, player);
		boolean bannerInstalled =
				BackpackLauncherRules.isBattleBannerInstalled(player);
		if (bannerInstalled && (clickedOffhand
				|| (actionType == SlotActionType.SWAP && button == 40))) {
			ci.cancel();
			return;
		}
		if (clickedOffhand
				&& BackpackLauncherRules.isVirtualBattleBanner(clicked.getStack())) {
			ci.cancel();
			return;
		}
		if (actionType == SlotActionType.SWAP && button == 40
				&& hasVirtualBattleBannerOffhand(handler, player)) {
			ci.cancel();
			return;
		}
		if (BackpackLauncherRules.usesAmmoStorage(player)
				&& (clickedOffhand
						|| (actionType == SlotActionType.SWAP && button == 40))) {
			// Ammunition belongs to the dedicated inventory slot at 94,62.
			ci.cancel();
			return;
		}
		if (actionType == SlotActionType.QUICK_MOVE) {
			return;
		}
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

	private static boolean isPlayerArmorSlot(Slot slot,
			PlayerEntity player) {
		return slot.inventory == player.getInventory()
				&& slot.getIndex() >= PlayerInventory.MAIN_SIZE
				&& slot.getIndex() < PlayerInventory.MAIN_SIZE + 4;
	}

	private static boolean isMechanicianEquipment(ItemStack stack) {
		return stack.getItem() instanceof MechanicianArmorItem
				|| stack.getItem() instanceof MechanicianUndersuitItem;
	}

	private static void quickMoveWithinPlayerInventory(
			PlayerScreenHandler handler, Slot source,
			PlayerEntity player) {
		int sourceIndex = handler.slots.indexOf(source);
		int targetStart;
		int targetEnd;
		if (sourceIndex >= 9 && sourceIndex < 36) {
			targetStart = 36;
			targetEnd = Math.min(45, handler.slots.size());
		} else if (sourceIndex >= 36 && sourceIndex < 45) {
			targetStart = 9;
			targetEnd = 36;
		} else {
			return;
		}
		ItemStack moving = source.getStack().copy();
		for (int index = targetStart; index < targetEnd
				&& !moving.isEmpty(); index++) {
			Slot target = handler.slots.get(index);
			if (target.inventory == player.getInventory()
					&& target.canInsert(moving)) {
				target.insertStack(moving);
			}
		}
		source.setStack(moving);
		source.markDirty();
		handler.sendContentUpdates();
	}

	private static boolean hasVirtualBattleBannerOffhand(
			ScreenHandler handler, PlayerEntity player) {
		for (Slot slot : handler.slots) {
			if (isOffhand(slot, player)
					&& BackpackLauncherRules.isVirtualBattleBanner(
							slot.getStack())) {
				return true;
			}
		}
		return false;
	}

}
