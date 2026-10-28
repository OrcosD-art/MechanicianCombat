package com.tk.gun.mixin;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerFallDamageMixin {
	@Inject(method = "method_23329(FF)I", at = @At("RETURN"), cancellable = true)
	private void tkgun$halveLongAirborneFallDamage(float fallDistance, float damageMultiplier,
												 CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof ServerPlayerEntity player
				&& cir.getReturnValueI() > 0) {
			cir.setReturnValue(TkGunServer.applyAirborneLandingDamageReduction(
					player, cir.getReturnValueI()));
		}
	}
}
