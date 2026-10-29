package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gun animations own the pose; the sneak key remains available for focus input. */
@Mixin(Entity.class)
public abstract class GunCrouchPoseGuardMixin {
	@Inject(method = "method_18276()Z", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressVanillaCrouch(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof PlayerEntity player && TkGun.holdingGun(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "method_5660(Z)V", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressCrouchFlag(boolean sneaking, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (sneaking && self instanceof PlayerEntity player && TkGun.holdingGun(player)) {
			ci.cancel();
		}
	}
}
