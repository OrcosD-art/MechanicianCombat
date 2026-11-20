package com.tk.gun.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/class_1708$class_1710")
public abstract class BrewingStandIngredientSlotMixin {
	@Inject(method = "method_7680", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$allowCustomInput(ItemStack stack,
			CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
