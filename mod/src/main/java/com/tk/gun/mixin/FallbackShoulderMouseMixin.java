package com.tk.gun.mixin;

import com.tk.gun.client.FallbackShoulderCamera;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FallbackShoulderMouseMixin {
	@Inject(method = "method_5872(DD)V", at = @At("HEAD"), cancellable = true)
	private void tkgun$turnFallbackCamera(double deltaX, double deltaY, CallbackInfo ci) {
		if ((Object) this instanceof ClientPlayerEntity player
				&& FallbackShoulderCamera.turn(player, deltaX, deltaY)) {
			ci.cancel();
		}
	}
}
