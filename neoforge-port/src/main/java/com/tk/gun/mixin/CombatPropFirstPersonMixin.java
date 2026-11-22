package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.CombatPropFirstPersonRenderer;
import com.tk.gun.client.SoulAnchorFirstPersonRenderer;
import com.tk.gun.client.SoulAnchorSkillClientState;
import com.tk.gun.client.WarBannerFirstPersonRenderer;
import com.tk.gun.client.WarBannerSkillClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla hand with authored hunter-anchor/turret FPA props. */
@Mixin(HeldItemRenderer.class)
public abstract class CombatPropFirstPersonMixin {
	@Inject(method = "method_22976", at = @At("RETURN"))
	private void mechaniciancombat$renderCombatPropFpa(float tickDelta,
			MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (TkGun.isKineticSword(player)) {
			return;
		}
		int id = player.getId();
		if (SoulAnchorSkillClientState.firstPersonActive(id)) {
			SoulAnchorFirstPersonRenderer.render(
					player, tickDelta, matrices, consumers, light);
		}
		CombatPropFirstPersonRenderer.renderHunterAnchor(
				player, tickDelta, matrices, consumers, light);
		if (WarBannerSkillClientState.isActive(id)) {
			WarBannerFirstPersonRenderer.render(
					player, tickDelta, matrices, consumers, light);
		}
		CombatPropFirstPersonRenderer.renderTurret(
				player, tickDelta, matrices, consumers, light);
	}
}
