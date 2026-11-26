package com.tk.gun.client.entity;

import com.tk.gun.client.ArmorDressingPerformerGeoModel;
import com.tk.gun.entity.ArmorDressingPerformerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.util.RenderUtil;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ArmorDressingPerformerRenderer
		extends GeoEntityRenderer<ArmorDressingPerformerEntity> {
	public ArmorDressingPerformerRenderer(
			EntityRendererFactory.Context context) {
		super(context, new ArmorDressingPerformerGeoModel());
		addRenderLayer(new com.tk.gun.client
				.ArmorDressingPerformerHeadSkinLayer(this));
	}

	@Override
	public boolean shouldRender(ArmorDressingPerformerEntity entity,
			Frustum frustum, double x, double y, double z) {
		return !entity.isRemoved();
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			ArmorDressingPerformerEntity animatable,
			software.bernie.geckolib.cache.object.GeoBone bone,
			net.minecraft.client.render.RenderLayer renderType,
			VertexConsumerProvider consumers, VertexConsumer buffer,
			boolean isReRender, float tickDelta, int packedLight,
			int packedOverlay, int packedColor) {
		if (bone.getParent() == null) {
			com.tk.gun.client.ArmorStationCreateAnimationRuntime
					.applyPerformerRoot(animatable, bone, tickDelta);
		}
		matrices.push();
		RenderUtil.prepMatrixForBone(matrices, bone);
		VertexConsumer activeBuffer = checkAndRefreshBuffer(isReRender,
				buffer, consumers, renderType);
		renderCubesOfBone(matrices, bone, activeBuffer, packedLight,
				packedOverlay, packedColor);
		if (!isReRender) {
			applyRenderLayersForBone(matrices, animatable, bone, renderType,
					consumers, activeBuffer, tickDelta, packedLight,
					packedOverlay);
		}
		renderChildBones(matrices, animatable, bone, renderType, consumers,
				activeBuffer, isReRender, tickDelta, packedLight,
				packedOverlay, packedColor);
		matrices.pop();
	}

	@Override
	protected void applyRotations(ArmorDressingPerformerEntity animatable,
			MatrixStack matrices, float rotationYaw, float rotationPitch,
			float scale, float tickDelta) {
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
				180.0F - animatable.getYaw()));
	}

	@Override
	protected float getShadowRadius(ArmorDressingPerformerEntity entity) {
		return 0.0F;
	}
}
