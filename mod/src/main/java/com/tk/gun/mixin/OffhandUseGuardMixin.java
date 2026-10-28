package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-side guard for direct or modified offhand item-use packets. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class OffhandUseGuardMixin {
	@Shadow
	public ServerPlayerEntity field_14140;

	@Inject(method = "method_12065(Lnet/minecraft/class_2886;)V",
			at = @At("HEAD"), cancellable = true)
	private void tkgun$rejectStoredOffhandUse(PlayerInteractItemC2SPacket packet,
											 CallbackInfo ci) {
		if (packet.getHand() == Hand.OFF_HAND
				&& BackpackLauncherRules.hasActiveLauncher(field_14140)) {
			ci.cancel();
		}
	}
}
