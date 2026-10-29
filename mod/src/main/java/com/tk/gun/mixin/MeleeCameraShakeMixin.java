package com.tk.gun.mixin;

import com.tk.gun.client.MeleeImpactClientState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MeleeCameraShakeMixin {
	@Inject(method = "method_3198(Lnet/minecraft/class_4587;F)V", at = @At("HEAD"))
	private void tkgun$applyMeleeCameraShake(MatrixStack matrices, float tickDelta,
			CallbackInfo ci) {
		MeleeImpactClientState.applyCameraShake(matrices, tickDelta);
	}
}
