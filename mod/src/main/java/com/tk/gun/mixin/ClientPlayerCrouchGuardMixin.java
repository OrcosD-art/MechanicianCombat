package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ClientPlayerEntity overrides both sneak queries, so guards on Entity alone
 * never affect local camera eye-height and player-model pose selection.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerCrouchGuardMixin {
	@Inject(method = "method_5715()Z", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressClientSneaking(CallbackInfoReturnable<Boolean> cir) {
		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
		if (TkGun.holdingGun(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "method_18276()Z", at = @At("HEAD"), cancellable = true)
	private void tkgun$suppressClientSneakingPose(CallbackInfoReturnable<Boolean> cir) {
		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
		if (TkGun.holdingGun(player)) {
			cir.setReturnValue(false);
		}
	}
}
