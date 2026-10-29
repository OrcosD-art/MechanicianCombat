package com.tk.gun.mixin;

import com.tk.gun.client.MeleeImpactClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Input.class)
public abstract class MeleeInputFreezeMixin {
	@Inject(method = "method_3129(ZF)V", at = @At("RETURN"))
	private void tkgun$freezeMeleeInput(boolean slowDown, float slowDownFactor,
			CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		Input input = (Input) (Object) this;
		if (client.player != null && client.player.input == input
				&& MeleeImpactClientState.freezesLocalInput()) {
			input.movementForward = 0.0F;
			input.movementSideways = 0.0F;
		}
	}
}
