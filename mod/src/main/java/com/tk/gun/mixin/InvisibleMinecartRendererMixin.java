package com.tk.gun.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla minecarts ignore the invisible flag in their dedicated renderer. */
@Mixin(MinecartEntityRenderer.class)
public abstract class InvisibleMinecartRendererMixin {
	@Inject(method = "method_4063(Lnet/minecraft/class_1688;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$hideInvisibleMinecart(
			AbstractMinecartEntity entity, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light,
			CallbackInfo ci) {
		if (entity.isInvisible()) {
			ci.cancel();
		}
	}
}
