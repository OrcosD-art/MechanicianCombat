package com.tk.gun.mixin;

import com.tk.gun.client.TkGunClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures a rebound keyboard ammo-ring key before vanilla input locks run. */
@Mixin(Keyboard.class)
public abstract class AmmoRingKeyboardInputMixin {
	@Inject(method = "method_22678", at = @At("HEAD"))
	private void mechaniciancombat$captureAmmoRingKey(
			long window, int key, int scancode, int action, int modifiers,
			CallbackInfo ci) {
		if (action == GLFW.GLFW_PRESS) {
			TkGunClient.handleAmmoRingRawInput(
					InputUtil.Type.KEYSYM.createFromCode(key));
		}
	}
}
