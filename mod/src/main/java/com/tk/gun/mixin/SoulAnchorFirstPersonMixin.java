package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.SoulAnchorFirstPersonRenderer;
import com.tk.gun.client.SoulAnchorSkillClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla first-person hand during anchor ready/throw. */
@Mixin(HeldItemRenderer.class)
public abstract class SoulAnchorFirstPersonMixin {
	private static long lastLoggedAt = -1L;

	@Inject(method = "method_22976", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$renderSoulAnchorFpa(float tickDelta,
			MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (TkGun.isKineticSword(player)
				|| !SoulAnchorSkillClientState.firstPersonActive(
						player.getId())) {
			return;
		}
		long startedAt = SoulAnchorSkillClientState.startedAt(player.getId());
		if (startedAt != lastLoggedAt) {
			lastLoggedAt = startedAt;
			TkGun.LOGGER.info(
					"[mechaniciancombat] Soul Anchor FPA render start: phaseReady={} throwing={} elapsed={}",
					SoulAnchorSkillClientState.ready(player.getId()),
					SoulAnchorSkillClientState.firstPersonThrowing(
							player.getId()),
					SoulAnchorSkillClientState.elapsedTicks(
							player.getId()));
		}
		SoulAnchorFirstPersonRenderer.render(
				player, tickDelta, matrices, consumers, light);
		ci.cancel();
	}
}
