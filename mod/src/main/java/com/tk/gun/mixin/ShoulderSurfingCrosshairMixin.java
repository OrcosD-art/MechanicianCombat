package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide ShoulderSurfing's vanilla-style static crosshair while holding the gun. */
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.CrosshairRenderer")
public abstract class ShoulderSurfingCrosshairMixin {
	@Inject(method = "doRenderCrosshair", at = @At("HEAD"), cancellable = true)
	private void tkgun$hideShoulderCrosshair(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && TkGun.holdingGun(client.player)) {
			cir.setReturnValue(false);
		}
	}
}

