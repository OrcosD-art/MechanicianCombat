package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only the stored offhand stack while retaining the main-hand item render. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class OffhandStorageThirdPersonMixin {
	@Inject(method = "method_17162", at = @At("HEAD"), cancellable = true)
	private void tkgun$hideHeldItemsWhileHoldingGun(MatrixStack matrices,
			VertexConsumerProvider consumers, int light, LivingEntity entity,
			float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch,
			CallbackInfo ci) {
		if (entity instanceof PlayerEntity player && TkGun.holdingGun(player)) {
			ci.cancel();
		}
	}

	@Redirect(method = "method_17162",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/class_1309;method_6079()Lnet/minecraft/class_1799;"))
	private ItemStack tkgun$hideStoredOffhandThirdPerson(LivingEntity entity) {
		ItemStack offhand = entity.getStackInHand(net.minecraft.util.Hand.OFF_HAND);
		return BackpackLauncherRules.hasActiveLauncher(entity)
				? ItemStack.EMPTY : offhand;
	}
}
