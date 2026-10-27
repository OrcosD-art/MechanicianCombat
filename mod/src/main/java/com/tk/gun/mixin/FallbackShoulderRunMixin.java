package com.tk.gun.mixin;

import com.tk.gun.client.FallbackShoulderCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FallbackShoulderRunMixin {
	@Inject(method = "method_3129(ZF)V", at = @At("TAIL"))
	private void tkgun$steerDecoupledRunning(boolean slowDown, float slowDownFactor,
											 CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		KeyboardInput input = (KeyboardInput) (Object) this;
		FallbackShoulderCamera.steerRunningInput(client, input);
	}
}
