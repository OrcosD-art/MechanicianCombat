package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.SoulAnchorSkillClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks vanilla sprint initiation while a gun is held. The post-roll dash
 * window remains allowed because it is driven by GunClientState rather than
 * the double-tap or sprint-key input.
 */
@Mixin(Entity.class)
public abstract class ClientPlayerSprintGuardMixin {
	@Inject(method = "method_5728", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$blockReadySprint(boolean sprinting,
			CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (sprinting && entity instanceof ClientPlayerEntity player) {
			boolean anchorReady =
					SoulAnchorSkillClientState.ready(player.getId());
			boolean nonGunAnchor =
					SoulAnchorSkillClientState.nonGunAnchorActive(
							player.getId());
			boolean vanillaSprintBlocked = TkGun.holdingGun(player)
					&& !TkGun.isMeleeWeapon(player)
					&& !GunClientState.dashing(player.getId());
			if (anchorReady || nonGunAnchor || vanillaSprintBlocked) {
				ci.cancel();
			}
		}
	}
}
