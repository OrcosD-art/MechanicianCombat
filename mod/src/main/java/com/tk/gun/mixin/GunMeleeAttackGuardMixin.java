package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gun attacks are server-authoritative. The intermediary target keeps this safe
 * for the offline build, which does not ship a Mixin refMap.
 */
@Mixin(value = ClientPlayerInteractionManager.class, priority = 900)
public abstract class GunMeleeAttackGuardMixin {
	@Inject(method = "method_2918(Lnet/minecraft/class_1657;Lnet/minecraft/class_1297;)V",
			at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressNativeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
		if (TkGun.holdingGun(player)) {
			ci.cancel();
		}
	}
}
