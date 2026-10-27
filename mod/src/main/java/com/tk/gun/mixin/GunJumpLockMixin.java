package com.tk.gun.mixin;

import com.tk.gun.client.GunJumpRules;
import com.tk.gun.client.GunWarningHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While carrying the gun, only normal walking states may consume the jump key. */
@Mixin(KeyboardInput.class)
public abstract class GunJumpLockMixin {
	private static boolean tkgun$jumpHeldWhileBlocked;

	@Inject(method = "method_3129(ZF)V", at = @At("TAIL"))
	private void tkgun$lockJump(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean jumpPressed = client.player != null && client.currentScreen == null
				&& client.options.jumpKey.isPressed();
		if (!jumpPressed || GunJumpRules.canJump(client)) {
			tkgun$jumpHeldWhileBlocked = false;
			return;
		}
		KeyboardInput self = (KeyboardInput) (Object) this;
		self.jumping = false;
		if (!tkgun$jumpHeldWhileBlocked) {
			tkgun$jumpHeldWhileBlocked = true;
			GunWarningHud.showOverweight();
		}
	}
}
