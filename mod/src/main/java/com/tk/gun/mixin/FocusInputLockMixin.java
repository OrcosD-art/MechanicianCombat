package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 专注期间屏蔽原版热键处理；保留玩家背包与战斗背包动作，并放行翻滚按下帧。
 */
@Mixin(MinecraftClient.class)
public abstract class FocusInputLockMixin {
	@Inject(method = "method_1508()V", at = @At("HEAD"), cancellable = true)
	private void tkgun$lockFocusInput(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.currentScreen == null
				&& TkGun.holdingGun(client.player)
				&& GunClientState.focusish(client.player.getId())
				&& !TkGunClient.isRollTriggerTick(client)) {
			TkGunClient.handleBackpackActionKey(client);
			if (client.options.inventoryKey.wasPressed()) {
				client.setScreen(new InventoryScreen(client.player));
			}
			ci.cancel();
		}
	}
}
