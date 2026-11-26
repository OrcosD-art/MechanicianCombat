package com.tk.gun.mixin;

import com.tk.gun.client.TkGunClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the mouse press before vanilla input state and focus locks run. */
@Mixin(Mouse.class)
public abstract class AmmoRingMouseInputMixin {
	@Inject(method = "method_22686", at = @At("HEAD"))
	private void mechaniciancombat$captureAmmoRingMouse(
			long window, int button, int action, int modifiers,
			CallbackInfo ci) {
		if (action == GLFW.GLFW_PRESS) {
			TkGunClient.handleAmmoRingRawInput(
					InputUtil.Type.MOUSE.createFromCode(button));
		}
	}
}
