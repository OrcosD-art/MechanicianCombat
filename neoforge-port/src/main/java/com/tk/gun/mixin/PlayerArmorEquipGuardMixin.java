package com.tk.gun.mixin;

import com.tk.gun.ArmorEquipmentTransaction;
import com.tk.gun.item.MechanicianArmorItem;
import com.tk.gun.item.MechanicianUndersuitItem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerArmorEquipGuardMixin {
	@Inject(method = "method_5673", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$guardPlayerArmorEquip(EquipmentSlot slot,
			ItemStack stack, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!isArmorSlot(slot)
				|| ArmorEquipmentTransaction.authorized(player)
				|| !isMechanicianEquipment(stack)
						&& !isMechanicianEquipment(
								player.getEquippedStack(slot))) {
			return;
		}
		ci.cancel();
	}

	private static boolean isArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
				|| slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}

	private static boolean isMechanicianEquipment(ItemStack stack) {
		return stack.getItem() instanceof MechanicianArmorItem
				|| stack.getItem() instanceof MechanicianUndersuitItem;
	}
}
