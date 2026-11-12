package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.entity.SteelArrowImpactEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Renders the former launch flash at the point where a turret bolt hits. */
public final class SteelArrowImpactEntityRenderer
		extends EntityRenderer<SteelArrowImpactEntity> {
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/steel_arrow.png");
	private static final int FRAMES = 5;
	private static final float FPS = 12.0F;
	private static final float SIZE = 2.0F;

	public SteelArrowImpactEntityRenderer(
			EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(SteelArrowImpactEntity entity, float yaw,
			float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		float seconds = (entity.age + tickDelta) / 20.0F;
		int frame = Math.min(FRAMES - 1,
				Math.max(0, (int) (seconds * FPS)));
		float v0 = frame / (float) FRAMES;
		float v1 = (frame + 1) / (float) FRAMES;
		float half = SIZE / 2.0F;

		matrices.push();
		matrices.multiply(dispatcher.getRotation());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer vertices = consumers.getBuffer(
				RenderLayer.getEntityTranslucentEmissive(TEXTURE));
		vertices.vertex(matrix, -half, -half, 0.0F)
				.color(255, 255, 255, 255)
				.texture(0.0F, v1)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vertices.vertex(matrix, half, -half, 0.0F)
				.color(255, 255, 255, 255)
				.texture(1.0F, v1)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vertices.vertex(matrix, half, half, 0.0F)
				.color(255, 255, 255, 255)
				.texture(1.0F, v0)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		vertices.vertex(matrix, -half, half, 0.0F)
				.color(255, 255, 255, 255)
				.texture(0.0F, v0)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(0.0F, 1.0F, 0.0F);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(SteelArrowImpactEntity entity) {
		return TEXTURE;
	}
}
