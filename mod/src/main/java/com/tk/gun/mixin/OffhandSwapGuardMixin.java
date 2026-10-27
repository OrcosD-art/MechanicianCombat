package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rejects vanilla offhand swaps when the main hand holds this mod's gun. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class OffhandSwapGuardMixin {
	@Shadow
	public ServerPlayerEntity field_14140;

	@Inject(method = "method_12066", at = @At("HEAD"), cancellable = true)
	private void tkgun$rejectGunOffhandSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (packet.getAction() != PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			return;
		}
		var mainHand = field_14140.getMainHandStack();
		if (TkGun.isGun(mainHand)
				|| (BackpackLauncherRules.hasActiveLauncher(field_14140)
				&& !BackpackLauncherRules.isLaunchable(mainHand))) {
			ci.cancel();
		}
	}
}
