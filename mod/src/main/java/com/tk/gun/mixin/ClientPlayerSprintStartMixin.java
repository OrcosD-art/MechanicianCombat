package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.SoulAnchorSkillClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the vanilla sprint predicate from starting sprint while armed. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerSprintStartMixin {
	@Inject(method = "method_48300", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$blockArmedSprintStart(
			CallbackInfoReturnable<Boolean> cir) {
		ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
		if ((TkGun.holdingGun(player)
				&& (!TkGun.isMeleeWeapon(player)
						|| TkGun.isKineticSword(player))
				&& !GunClientState.dashing(player.getId()))
				|| SoulAnchorSkillClientState.nonGunAnchorActive(
						player.getId())) {
			cir.setReturnValue(false);
		}
	}
}
