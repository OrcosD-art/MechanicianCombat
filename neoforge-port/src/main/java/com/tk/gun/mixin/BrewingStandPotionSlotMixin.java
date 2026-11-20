package com.tk.gun.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/class_1708$class_1711")
public abstract class BrewingStandPotionSlotMixin {
	@Inject(method = "method_7680", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$allowCustomReagent(ItemStack stack,
			CallbackInfoReturnable<Boolean> cir) {
		Slot slot = (Slot) (Object) this;
		if (slot.id == 0) {
			cir.setReturnValue(true);
		}
	}
}
