package com.tk.gun.mixin;

import com.tk.gun.item.MechanicianUndersuitItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents a plain helmet-slot item icon being drawn on the player's head. */
@Mixin(HeadFeatureRenderer.class)
public abstract class MechanicianUndersuitHeadFeatureMixin {
	@Inject(method = "method_17159(Lnet/minecraft/class_4587;"
			+ "Lnet/minecraft/class_4597;ILnet/minecraft/class_1309;"
			+ "FFFFFF)V", at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$hideUndersuitItemIcon(
			MatrixStack matrices, VertexConsumerProvider consumers,
			int light, LivingEntity entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		if (entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD)
				.getItem() instanceof MechanicianUndersuitItem) {
			ci.cancel();
		}
	}
}
