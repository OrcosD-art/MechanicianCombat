package com.tk.gun.client.entity;

import com.tk.gun.client.WarBannerGeoModel;
import com.tk.gun.entity.WarBannerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class WarBannerEntityRenderer
		extends GeoEntityRenderer<WarBannerEntity> {
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

	private static BlockPos bannerLightPos(WarBannerEntity entity) {
		return BlockPos.ofFloored(
				entity.getX(),
				entity.getY() + WarBannerEntity.LOCATOR_HEIGHT + 1.5D,
				entity.getZ());
	}
}
