package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While holding a gun, the vanilla Pick Block key belongs to the ammo ring. */
@Mixin(MinecraftClient.class)
public abstract class MiddleMouseReloadMixin {
	@Inject(method = "method_1511", at = @At("HEAD"), cancellable = true)
	private void tkgun$pickItemOpensAmmoRing(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.currentScreen == null
				&& TkGun.holdingGun(client.player)) {
			// TkGunClient polls pickItemKey directly and opens the ring once.
			ci.cancel();
		}
	}
}
