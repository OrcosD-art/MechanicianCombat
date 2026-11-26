package com.tk.gun.block;

import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Shared bridge between the workstation backpack slot and Accessories. */
public final class BackpackWorkstationBinding {
	public static ItemStack equipped(LivingEntity player) {
		SlotEntryReference entry = reference(player);
		return entry == null ? ItemStack.EMPTY : entry.stack().copy();
	}

	public static boolean equip(ServerPlayerEntity player, ItemStack stack,
			UUID bindingId) {
		if (!(stack.getItem() instanceof CombatBackpackItem)
				|| stack.getCount() != 1
				|| !equipped(player).isEmpty()) {
			return false;
		}
		var capability = AccessoriesCapability.getOptionally(player)
				.orElse(null);
		if (capability == null) {
			return false;
		}
		ItemStack equipped = stack.copyWithCount(1);
		BackpackUpgradeData.setWorkstationBinding(equipped, bindingId);
		if (capability.canEquipAccessory(equipped, false).first() == null) {
			return false;
		}
		return capability.attemptToEquipAccessory(equipped) != null;
	}

	public static boolean update(ServerPlayerEntity player, ItemStack stack) {
		SlotEntryReference entry = reference(player);
		return entry != null && entry.reference().setStack(
				stack.copyWithCount(1));
	}

	public static ItemStack remove(ServerPlayerEntity player) {
		SlotEntryReference entry = reference(player);
		if (entry == null) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = entry.stack().copy();
		if (!entry.reference().setStack(ItemStack.EMPTY)) {
			return ItemStack.EMPTY;
		}
		BackpackUpgradeData.setWorkstationBinding(removed, null);
		return removed;
	}

	private static SlotEntryReference reference(LivingEntity player) {
		return AccessoriesCapability.getOptionally(player)
				.map(capability -> capability.getFirstEquipped(
						stack -> stack.getItem()
								instanceof CombatBackpackItem))
				.orElse(null);
	}

	private BackpackWorkstationBinding() {
	}
}
