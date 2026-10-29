package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevent vanilla doAttack from starting a hand swing before the gun melee
 * packet is handled. Injecting at attackEntity is too late for the animation.
 */
@Mixin(MinecraftClient.class)
public abstract class GunVanillaAttackGuardMixin {
	@Inject(method = "method_1536()Z", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressVanillaAttack(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && TkGun.holdingGun(client.player)) {
			cir.setReturnValue(false);
		}
	}

	/** Continuous left-click block breaking bypasses doAttack and swings the hand. */
	@Inject(method = "method_1590(Z)V", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressBlockBreaking(boolean breaking, CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && TkGun.holdingGun(client.player)) {
			ci.cancel();
		}
	}
}
