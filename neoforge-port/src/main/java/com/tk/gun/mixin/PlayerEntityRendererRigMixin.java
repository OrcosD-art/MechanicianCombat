package com.tk.gun.mixin;

import com.tk.gun.TkGun;
import com.tk.gun.client.HelmetClientState;
import com.tk.gun.client.HunterSpearHeldRenderer;
import com.tk.gun.client.WarBannerHeldRenderer;
import com.tk.gun.client.rig.BowgunRigClient;
import com.tk.gun.item.MechanicianUndersuitItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

/**
 * 持枪时先让 GeckoLib 绘制枪械并采集身体姿势，然后继续原版 PlayerRenderer。
 * <ol>
 *   <li>把 {@code EntityRendererFactory.Context} 记下来 —— GeckoLib 的渲染器构造要用；</li>
 *   <li>渲染玩家时，如果手里是破垒重炮 → GeckoLib 绘制枪械，原版继续绘制身体与装备。</li>
 * </ol>
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererRigMixin {
	@Unique
	private boolean[] mechaniciancombat$modelVisibility = new boolean[10];

	@Inject(method = "<init>(Lnet/minecraft/class_5617$class_5618;Z)V", at = @At("TAIL"))
	private void mechaniciancombat$rememberContext(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
		BowgunRigClient.setContext(ctx);
		WarBannerHeldRenderer.setContext(ctx);
		HunterSpearHeldRenderer.setContext(ctx);
	}

	@Inject(method = "method_4215(Lnet/minecraft/class_742;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("HEAD"))
	private void mechaniciancombat$renderRig(AbstractClientPlayerEntity player, float yaw, float tickDelta,
								 MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel<?> model) {
			mechaniciancombat$modelVisibility = new boolean[]{
					model.body.visible,
					model.leftArm.visible,
					model.rightArm.visible,
					model.leftLeg.visible,
					model.rightLeg.visible,
					model.leftSleeve.visible,
					model.rightSleeve.visible,
					model.leftPants.visible,
					model.rightPants.visible,
					model.jacket.visible
			};
		}
		BowgunRigClient.renderIfHolding(player, yaw, tickDelta, matrices, consumers, light);
		WarBannerHeldRenderer.renderIfActive(
				player, yaw, tickDelta, matrices, consumers, light);
		HunterSpearHeldRenderer.renderIfActive(
				player, yaw, tickDelta, matrices, consumers, light);
	}

	@Inject(method = "method_4215(Lnet/minecraft/class_742;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("RETURN"))
	private void mechaniciancombat$restoreUndersuitBody(AbstractClientPlayerEntity player,
											 float yaw, float tickDelta,
											 MatrixStack matrices,
											 VertexConsumerProvider consumers,
											 int light, CallbackInfo ci) {
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel<?> model) {
			boolean[] visible = mechaniciancombat$modelVisibility;
			model.body.visible = visible[0];
			model.leftArm.visible = visible[1];
			model.rightArm.visible = visible[2];
			model.leftLeg.visible = visible[3];
			model.rightLeg.visible = visible[4];
			model.leftSleeve.visible = visible[5];
			model.rightSleeve.visible = visible[6];
			model.leftPants.visible = visible[7];
			model.rightPants.visible = visible[8];
			model.jacket.visible = visible[9];
		}
	}

	/**
	 * The gun rig already contains the authored crouch/focus pose. Letting the
	 * vanilla player model also enable its sneak pose applies two crouch rotations
	 * and body offsets to the same limbs.
	 */
	@Inject(method = "method_4218(Lnet/minecraft/class_742;)V", at = @At("TAIL"))
	private void mechaniciancombat$suppressStackedVanillaCrouch(AbstractClientPlayerEntity player,
													CallbackInfo ci) {
		if (!TkGun.holdingGun(player)) {
			mechaniciancombat$hideUndersuitBody(player);
			mechaniciancombat$updateHelmetHair(player);
			return;
		}
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel model) {
			model.sneaking = false;
		}
		mechaniciancombat$hideUndersuitBody(player);
		mechaniciancombat$updateHelmetHair(player);
	}

	/**
	 * The first-person arm pass renders the vanilla skin directly. Hide those
	 * two model parts so the Accessories undersuit arm can replace them at the
	 * same hand transform.
	 */
	@Inject(method = "method_23205(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;ILnet/minecraft/class_742;Lnet/minecraft/class_630;Lnet/minecraft/class_630;)V",
			at = @At("HEAD"))
	private void mechaniciancombat$replaceUndersuitFirstPersonArm(
			MatrixStack matrices, VertexConsumerProvider consumers, int light,
			AbstractClientPlayerEntity player, ModelPart arm,
			ModelPart sleeve, CallbackInfo ci) {
		if (MechanicianUndersuitItem.isEquipped(player)) {
			arm.visible = false;
			sleeve.visible = false;
		}
	}

	@Unique
	private void mechaniciancombat$hideUndersuitBody(AbstractClientPlayerEntity player) {
		if (!MechanicianUndersuitItem.isEquipped(player)) {
			return;
		}
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel<?> model) {
			// setModelPose() re-enables every body part. Hide the authored player
			// body after that reset so only the Accessories undersuit is visible.
			model.body.visible = false;
			model.leftArm.visible = false;
			model.rightArm.visible = false;
			model.leftLeg.visible = false;
			model.rightLeg.visible = false;
			model.leftSleeve.visible = false;
			model.rightSleeve.visible = false;
			model.leftPants.visible = false;
			model.rightPants.visible = false;
			model.jacket.visible = false;
		}
	}

	@Unique
	private void mechaniciancombat$updateHelmetHair(AbstractClientPlayerEntity player) {
		if (!MechanicianUndersuitItem.isWearingOuterSet(player)) {
			return;
		}
		PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
		if (renderer.getModel() instanceof PlayerEntityModel<?> model) {
			model.hat.visible = !HelmetClientState.shouldHideHair(player);
		}
	}
}
