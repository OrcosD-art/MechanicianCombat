package com.tk.gun.client.rig;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Evaluates the hidden normal-player rig and captures its evaluated hierarchy. */
public final class PlayerLocomotionRigRenderer
		extends GeoReplacedEntityRenderer<AbstractClientPlayerEntity,
				PlayerLocomotionRig> {
	private static final Set<String> POSE_BONES = Set.of(
			"body", "chest", "head", "arm_left", "arm_right",
			"leg_left", "leg_right");
	private final Map<String, BowgunPlayerAnimatorPose.BonePose> pose =
			new HashMap<>();

	public PlayerLocomotionRigRenderer(EntityRendererFactory.Context context,
			PlayerLocomotionRig animatable) {
		super(context, new PlayerLocomotionRigModel(), animatable);
	}

	@Override
	public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone,
			VertexConsumer buffer, int packedLight, int packedOverlay,
			int colour) {
		// The ordinary player path continues to render the vanilla body.
	}

	@Override
	public void renderRecursively(MatrixStack poseStack,
			PlayerLocomotionRig animatable, GeoBone bone, RenderLayer type,
			VertexConsumerProvider bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		super.renderRecursively(poseStack, animatable, bone, type,
				bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
		Entity entity = getCurrentEntity();
		boolean currentFrame = !isReRender && entity != null;
		if (currentFrame && POSE_BONES.contains(bone.getName())) {
			pose.put(bone.getName(), new BowgunPlayerAnimatorPose.BonePose(
					bone.getPosX(), bone.getPosY(), bone.getPosZ(),
					bone.getRotX(), bone.getRotY(), bone.getRotZ(),
					bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
		}
		if (currentFrame && "body".equals(bone.getName())) {
			PlayerLocomotionPoseBridge.capture(entity.getId(), pose);
		}
	}
}
