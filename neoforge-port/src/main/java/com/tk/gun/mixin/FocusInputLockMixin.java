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
	private void mechaniciancombat$lockFocusInput(CallbackInfo ci) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		if (client.player != null && client.currentScreen == null
				&& com.tk.gun.client.ArmorDressingClientState.locksInput(
						client.player.getId())) {
			for (var hotbarKey : client.options.hotbarKeys) {
				while (hotbarKey.wasPressed()) {
					// Consume queued hotbar input during the dressing sequence.
				}
			}
			ci.cancel();
			return;
		}
		if (client.player != null && client.currentScreen == null
				&& com.tk.gun.client.ArmorDressingClientState
						.mountedStationPos(client.player.getId()) != null) {
			for (var hotbarKey : client.options.hotbarKeys) {
				while (hotbarKey.wasPressed()) {
					// The mounted workstation owns vanilla input actions.
				}
			}
			while (client.options.inventoryKey.wasPressed()) {
				// TkGunClient reads the physical key and opens the armor page.
				// Consume vanilla's queued press so the player inventory does
				// not open over the workstation.
			}
			ci.cancel();
			return;
		}
		if (client.player != null && client.currentScreen == null
				&& TkGun.holdingGun(client.player)
				&& GunClientState.focusish(client.player.getId())
				&& !TkGunClient.isRollTriggerTick(client)) {
			// Cancelling handleInputEvents() prevents the slot change, but vanilla
			// keeps the press queued in KeyBinding.timesPressed. Consume every
			// queued hotbar press so it cannot execute after leaving focus.
			for (var hotbarKey : client.options.hotbarKeys) {
				while (hotbarKey.wasPressed()) {
					// Drain the queued press.
				}
			}
			TkGunClient.handleBackpackActionKey(client);
			if (client.options.inventoryKey.wasPressed()) {
				client.setScreen(new InventoryScreen(client.player));
			}
			ci.cancel();
		}
	}
}
