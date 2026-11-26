package com.tk.gun.mixin;

import com.tk.gun.client.LockedArmorGuiOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class LockedArmorInventoryGuiMixin {
	@Inject(method = "method_2389(Lnet/minecraft/class_332;FII)V",
			at = @At("TAIL"))
	private void mechaniciancombat$drawLockedArmorGui(
			DrawContext context, float delta, int mouseX, int mouseY,
			CallbackInfo ci) {
		LockedArmorGuiOverlay.render(context, this);
	}
}
