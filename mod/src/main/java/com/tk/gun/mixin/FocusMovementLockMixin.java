package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 专注期间清零移动输入；翻滚按下帧保留方向供 Combat Roll 计算翻滚向量。 */
@Mixin(KeyboardInput.class)
public abstract class FocusMovementLockMixin {
	@Inject(method = "method_3129(ZF)V", at = @At("TAIL"))
	private void tkgun$lockFocusMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !TkGun.holdingGun(client.player)
				|| !GunClientState.focusish(client.player.getId())) {
			return;
		}
		KeyboardInput self = (KeyboardInput) (Object) this;
		if (!TkGunClient.isRollTriggerTick(client)) {
			self.movementForward = 0.0F;
			self.movementSideways = 0.0F;
		}
		self.jumping = false;
		self.sneaking = false;
	}
}
