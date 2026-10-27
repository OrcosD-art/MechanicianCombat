package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Combat Roll from physically rolling while the gun state machine owns the dodge sequence. */
@Pseudo
@Mixin(targets = "net.combat_roll.internals.RollManager")
public abstract class CombatRollInputGuardMixin {
	@Inject(method = "isRollAvailable", at = @At("HEAD"), cancellable = true)
	private void tkgun$guardDodgeSequence(PlayerEntity player,
										  CallbackInfoReturnable<Boolean> cir) {
		if (player instanceof ClientPlayerEntity && TkGun.holdingGun(player)
				&& GunClientState.combatRollSuppressed(player.getId())) {
			cir.setReturnValue(false);
		}
	}
}
