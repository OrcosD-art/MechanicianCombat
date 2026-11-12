package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.client.WarBannerGeoModel;
import com.tk.gun.entity.WarBannerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class WarBannerEntityRenderer
		extends GeoEntityRenderer<WarBannerEntity> {
	private static final Identifier RANGE_TEXTURE =
			TkGun.id("textures/entity/war_banner_effect.png");
	private static final RenderLayer RANGE_LAYER =
			RenderLayer.getEntityTranslucentEmissive(RANGE_TEXTURE, false);
	private static final int RANGE_FRAME_COUNT = 8;
	private static final int RANGE_FRAME_TICKS = 2;
	private static final float RANGE_HALF_SIZE = (float) WarBannerEntity.AURA_RADIUS;
	private static final float RANGE_HEIGHT = 0.25F / 16.0F;

	public WarBannerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new WarBannerGeoModel());
	}

	@Override
	public void preRender(MatrixStack matrices, WarBannerEntity animatable,
			BakedGeoModel model, VertexConsumerProvider bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		super.preRender(matrices, animatable, model, bufferSource, buffer,
				isReRender, partialTick, packedLight, packedOverlay, colour);
		// The entity root is the model locator height below the ground. Lift
		// only the visual model so its pole rests on the block surface.
		matrices.translate(0.0D, WarBannerEntity.LOCATOR_HEIGHT, 0.0D);
		renderRangeIndicator(matrices, animatable, bufferSource);
	}

	@Override
	public boolean shouldRender(WarBannerEntity entity, Frustum frustum,
			double x, double y, double z) {
		if (!entity.shouldRender(x, y, z)) {
			return false;
		}
		return frustum.isVisible(entity.getBoundingBox()
				.expand(WarBannerEntity.AURA_RADIUS + 1.0D));
	}

	@Override
	protected void applyRotations(WarBannerEntity animatable,
			MatrixStack matrices, float rotationYaw, float rotationPitch,
			float scale, float tickDelta) {
		// The banner is a static entity. Use the exact synced visual yaw rather
		// than a quantized entity angle, preserving every intermediate heading.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
				180.0F - animatable.visualYaw()));
	}

	@Override
	protected float getShadowRadius(WarBannerEntity entity) {
		return 0.0F;
	}

	@Override
	protected int getSkyLight(WarBannerEntity entity, BlockPos pos) {
		return super.getSkyLight(entity, bannerLightPos(entity));
	}

	@Override
	protected int getBlockLight(WarBannerEntity entity, BlockPos pos) {
		return super.getBlockLight(entity, bannerLightPos(entity));
	}

	@Override
	public void renderCube(MatrixStack matrices, GeoCube cube,
			VertexConsumer buffer, int packedLight, int packedOverlay,
			int colour) {
		super.renderCube(matrices, cube, buffer,
				LightmapTextureManager.MAX_LIGHT_COORDINATE,
				packedOverlay, colour);
	}

	private static void renderRangeIndicator(MatrixStack matrices,
			WarBannerEntity entity, VertexConsumerProvider consumers) {
		int frame = (entity.age / RANGE_FRAME_TICKS) % RANGE_FRAME_COUNT;
		float v0 = (float) frame / RANGE_FRAME_COUNT;
		float v1 = (float) (frame + 1) / RANGE_FRAME_COUNT;
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = consumers.getBuffer(RANGE_LAYER);
		rangeVertex(buffer, matrix, -RANGE_HALF_SIZE, RANGE_HEIGHT,
				-RANGE_HALF_SIZE, 0.0F, v0);
		rangeVertex(buffer, matrix, -RANGE_HALF_SIZE, RANGE_HEIGHT,
				RANGE_HALF_SIZE, 0.0F, v1);
		rangeVertex(buffer, matrix, RANGE_HALF_SIZE, RANGE_HEIGHT,
				RANGE_HALF_SIZE, 1.0F, v1);
		rangeVertex(buffer, matrix, RANGE_HALF_SIZE, RANGE_HEIGHT,
				-RANGE_HALF_SIZE, 1.0F, v0);
	}

	private static void rangeVertex(VertexConsumer buffer, Matrix4f matrix,
			float x, float y, float z, float u, float v) {
		buffer.vertex(matrix, x, y, z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
	}

	private static BlockPos bannerLightPos(WarBannerEntity entity) {
		return BlockPos.ofFloored(
				entity.getX(),
				entity.getY() + WarBannerEntity.LOCATOR_HEIGHT + 1.5D,
				entity.getZ());
	}
}
