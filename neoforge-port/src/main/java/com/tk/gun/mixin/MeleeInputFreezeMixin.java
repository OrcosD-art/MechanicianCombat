package com.tk.gun.mixin;

import com.tk.gun.client.MeleeImpactClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardInput overrides Input.tick, so a base-class injection never runs for
 * normal keyboard movement. Inject into the concrete implementation instead.
 */
@Mixin(KeyboardInput.class)
public abstract class MeleeInputFreezeMixin {
	@Inject(method = "method_3129(ZF)V", at = @At("RETURN"))
	private void mechaniciancombat$freezeMeleeInput(boolean slowDown, float slowDownFactor,
			CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		Input input = (Input) (Object) this;
		if (client.player != null && client.player.input == input
				&& MeleeImpactClientState.freezesLocalInput()) {
			input.movementForward = 0.0F;
			input.movementSideways = 0.0F;
			return;
		}
		if (client.player != null && client.player.input == input) {
			float scale = com.tk.gun.client.MeleeMovementSlowdown
					.inputMultiplier(client.player.getId());
			input.movementForward *= scale;
			input.movementSideways *= scale;
		}
	}
}
