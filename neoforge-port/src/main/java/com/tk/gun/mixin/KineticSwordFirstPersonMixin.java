package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.CombatBackpackClientState;
import com.tk.gun.client.HunterSpearSkillClientState;
import com.tk.gun.client.KineticSwordFirstPersonRenderer;
import com.tk.gun.client.SoulAnchorSkillClientState;
import com.tk.gun.client.WarBannerSkillClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the Kinetic Sword held-item pass with its animated rig. */
@Mixin(HeldItemRenderer.class)
public abstract class KineticSwordFirstPersonMixin {
	@Inject(method = "method_22976", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$renderKineticSwordFpa(float tickDelta,
			MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (!TkGun.isKineticSword(player)) {
			return;
		}
		int id = player.getId();
		if (SoulAnchorSkillClientState.firstPersonActive(id)
				|| WarBannerSkillClientState.isActive(id)
				|| HunterSpearSkillClientState.isActive(id)
				|| CombatBackpackClientState.turretActivated(id)) {
			return;
		}
		KineticSwordFirstPersonRenderer.render(
				player, tickDelta, matrices, consumers, light);
		ci.cancel();
	}
}
