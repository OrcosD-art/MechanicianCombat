package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.TkGunClient;
import com.tk.gun.net.GunReloadPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Middle mouse behaves as reload while carrying the gun. */
@Mixin(Mouse.class)
public abstract class MiddleMouseReloadMixin {
	@Inject(method = "method_1601(JIII)V", at = @At("HEAD"), cancellable = true)
	private void tkgun$middleMouseReload(long window, int button, int action, int modifiers,
									  CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || action != GLFW.GLFW_PRESS
				|| client.player == null || client.currentScreen != null
				|| !TkGun.holdingGun(client.player)) {
			return;
		}
		// 显式绑定优先：中键若被玩家设为装填键或弹药环键，
		// 交给 TkGunClient 的统一轮询处理，避免这里再发一次造成双触发。
		boolean reloadOnMiddle = TkGunClient.isBoundToMouse(TkGunClient.RELOAD_KEY, button);
		boolean ammoRingOnMiddle = TkGunClient.isBoundToMouse(TkGunClient.AMMO_UI_KEY, button);
		ci.cancel();
		if (reloadOnMiddle || ammoRingOnMiddle) {
			return;
		}
		GunClientState.Entry entry = GunClientState.get(client.player.getId());
		boolean rolling = entry != null && (entry.state == TkGun.State.ROLL
				|| entry.state == TkGun.State.ROLL_TO_IDLE);
		if (!GunClientState.focusish(client.player.getId()) && !rolling) {
			ClientPlayNetworking.send(new GunReloadPayload());
		}
	}
}
