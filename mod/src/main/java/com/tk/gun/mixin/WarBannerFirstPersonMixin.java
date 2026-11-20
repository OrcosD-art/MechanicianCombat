package com.tk.gun.mixin;

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

/** Replaces the vanilla first-person hand with the authored flag FPA model. */
@Mixin(HeldItemRenderer.class)
public abstract class WarBannerFirstPersonMixin {
	@Inject(method = "method_22976", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$renderWarBannerFpa(float tickDelta,
			MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
			ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (!WarBannerSkillClientState.isActive(player.getId())) {
			return;
		}
		WarBannerFirstPersonRenderer.render(
				player, tickDelta, matrices, consumers, light);
		ci.cancel();
	}
}
