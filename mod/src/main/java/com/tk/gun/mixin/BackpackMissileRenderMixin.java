package com.tk.gun.mixin;

import com.tk.gun.item.BackpackFireworkData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FireworkRocketEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ShulkerBulletEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws only missile-box rockets with the vanilla shulker bullet model. */
@Mixin(FireworkRocketEntityRenderer.class)
public abstract class BackpackMissileRenderMixin {
	@Unique
	private static final Identifier MECHANICIANCOMBAT$MISSILE_TEXTURE =
			Identifier.ofVanilla("textures/entity/shulker/spark.png");

	@Unique
	private ShulkerBulletEntityModel<FireworkRocketEntity> mechaniciancombat$missileModel;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mechaniciancombat$createMissileModel(
			EntityRendererFactory.Context context, CallbackInfo ci) {
		mechaniciancombat$missileModel = new ShulkerBulletEntityModel<>(
				context.getPart(EntityModelLayers.SHULKER_BULLET));
	}

	@Inject(
			method = "method_3968(Lnet/minecraft/class_1671;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("HEAD"),
			cancellable = true)
	private void mechaniciancombat$renderMissileBoxRocket(
			FireworkRocketEntity rocket, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light,
			CallbackInfo ci) {
		if (!BackpackFireworkData.usesMissileVisual(rocket.getStack())) {
			return;
		}

		float interpolatedYaw = MathHelper.lerpAngleDegrees(
				tickDelta, rocket.prevYaw, rocket.getYaw());
		float interpolatedPitch = MathHelper.lerp(
				tickDelta, rocket.prevPitch, rocket.getPitch());
		float age = rocket.age + tickDelta;

		matrices.push();
		matrices.translate(0.0F, 0.15F, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
				MathHelper.sin(age * 0.1F) * 180.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
				MathHelper.cos(age * 0.1F) * 180.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
				MathHelper.sin(age * 0.15F) * 360.0F));
		matrices.scale(-0.5F, -0.5F, 0.5F);

		mechaniciancombat$missileModel.setAngles(
				rocket, 0.0F, 0.0F, 0.0F, interpolatedYaw, interpolatedPitch);
		VertexConsumer body = consumers.getBuffer(
				mechaniciancombat$missileModel.getLayer(
						MECHANICIANCOMBAT$MISSILE_TEXTURE));
		mechaniciancombat$missileModel.render(
				matrices, body, light, OverlayTexture.DEFAULT_UV);

		matrices.scale(1.5F, 1.5F, 1.5F);
		VertexConsumer glow = consumers.getBuffer(
				net.minecraft.client.render.RenderLayer.getEntityTranslucent(
						MECHANICIANCOMBAT$MISSILE_TEXTURE));
		mechaniciancombat$missileModel.render(
				matrices, glow, light, OverlayTexture.DEFAULT_UV, 0x26FFFFFF);
		matrices.pop();
		ci.cancel();
	}
}
