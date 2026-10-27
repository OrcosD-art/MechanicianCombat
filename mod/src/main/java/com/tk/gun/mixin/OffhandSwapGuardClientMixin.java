package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops the offhand-swap packet locally so the gun never visually enters the offhand. */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class OffhandSwapGuardClientMixin {
	@Inject(method = "method_52787", at = @At("HEAD"), cancellable = true)
	private void tkgun$rejectGunOffhandSwap(Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof PlayerActionC2SPacket action
				&& action.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null
					&& (TkGun.isGun(client.player.getMainHandStack())
					|| (BackpackLauncherRules.hasActiveLauncher(client.player)
					&& !BackpackLauncherRules.isLaunchable(client.player.getMainHandStack())))) {
				ci.cancel();
			}
		}
	}
}
