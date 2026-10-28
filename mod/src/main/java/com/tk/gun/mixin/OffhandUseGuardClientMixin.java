package com.tk.gun.mixin;

import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The launcher's offhand slot stores ammunition and must never run vanilla item use. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class OffhandUseGuardClientMixin {
	@Inject(method = "method_2919(Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;)Lnet/minecraft/class_1269;",
			at = @At("HEAD"), cancellable = true)
	private void tkgun$rejectStoredOffhandUse(PlayerEntity player, Hand hand,
											  CallbackInfoReturnable<ActionResult> cir) {
		if (hand == Hand.OFF_HAND && BackpackLauncherRules.hasActiveLauncher(player)) {
			cir.setReturnValue(ActionResult.FAIL);
		}
	}
}
