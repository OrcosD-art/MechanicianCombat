package com.tk.gun.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws animated camera-facing chain ribbons between the three linked entities. */
public final class SoulChainWorldRenderer {
	private static final Identifier TEXTURE =
			Identifier.of("tkgun", "textures/entity/connect_chain.png");
	/** 16x16 source pixels at the standard 16 pixels per block scale. */
	private static final double SEGMENT_LENGTH = 1.0D;
	private static final double HALF_WIDTH = 0.5D;

	private SoulChainWorldRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (SoulChainClientState.effects().isEmpty()
					|| context.world() == null) {
				return;
			}
			long now = context.world().getTime();
			float tickDelta = context.tickCounter().getTickDelta(false);
			Vec3d camera = context.camera().getPos();
			MatrixStack matrices = context.matrixStack();
			VertexConsumer buffer = context.consumers().getBuffer(
					RenderLayer.getEntityTranslucentEmissive(TEXTURE));
			MatrixStack.Entry entry = matrices.peek();
			Matrix4f matrix = entry.getPositionMatrix();
			for (SoulChainClientState.Effect effect :
					SoulChainClientState.effects()) {
				float alpha = effect.alphaAt(now);
				Vec3d a = entityPoint(context.world(), effect.aEntityId(),
						effect.a(), tickDelta);
				Vec3d b = entityPoint(context.world(), effect.bEntityId(),
						effect.b(), tickDelta);
				Vec3d c = entityPoint(context.world(), effect.cEntityId(),
						effect.c(), tickDelta);
				drawEdge(matrix, entry, buffer, a, b,
						camera, alpha);
				if (effect.hasC()) {
					drawEdge(matrix, entry, buffer, b, c,
							camera, alpha * 0.9F);
				}
			}
		});
	}

	private static Vec3d entityPoint(ClientWorld world, int entityId,
									 Vec3d fallback, float tickDelta) {
		if (entityId < 0) {
			return fallback;
		}
		Entity entity = world.getEntityById(entityId);
		if (entity == null) {
			return fallback;
		}
		double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
		double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
		double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
		return new Vec3d(x, y + entity.getHeight() * 0.55D, z);
	}

	private static void drawEdge(Matrix4f matrix, MatrixStack.Entry entry,
								 VertexConsumer buffer, Vec3d worldStart,
								 Vec3d worldEnd, Vec3d camera, float alpha) {
		Vec3d start = worldStart.subtract(camera);
		Vec3d end = worldEnd.subtract(camera);
		Vec3d delta = end.subtract(start);
		double length = delta.length();
		if (length < 1.0E-5D) {
			return;
		}
		Vec3d direction = delta.multiply(1.0D / length);
		Vec3d view = start.add(end).multiply(0.5D).multiply(-1.0D);
		if (view.lengthSquared() < 1.0E-6D) {
			view = new Vec3d(0.0D, 0.0D, 1.0D);
		}
		Vec3d side = direction.crossProduct(view).normalize()
				.multiply(HALF_WIDTH);
		if (side.lengthSquared() < 1.0E-6D) {
			side = new Vec3d(HALF_WIDTH, 0.0D, 0.0D);
		}
		int segments = Math.max(1, (int) Math.ceil(length / SEGMENT_LENGTH));
		for (int index = 0; index < segments; index++) {
			double t0 = index / (double) segments;
			double t1 = (index + 1) / (double) segments;
			Vec3d p0 = start.add(delta.multiply(t0));
			Vec3d p1 = start.add(delta.multiply(t1));
			Vec3d left0 = p0.subtract(side);
			Vec3d right0 = p0.add(side);
			Vec3d left1 = p1.subtract(side);
			Vec3d right1 = p1.add(side);
			vertex(matrix, entry, buffer, left0, 0.0F, 0.0F, alpha);
			vertex(matrix, entry, buffer, right0, 1.0F, 0.0F, alpha);
			vertex(matrix, entry, buffer, right1, 1.0F, 1.0F, alpha);
			vertex(matrix, entry, buffer, left1, 0.0F, 1.0F, alpha);
		}
	}

	private static void vertex(Matrix4f matrix, MatrixStack.Entry entry,
							   VertexConsumer buffer, Vec3d point,
							   float u, float v, float alpha) {
		buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
				.color(1.0F, 1.0F, 1.0F, alpha)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry, 0.0F, 1.0F, 0.0F);
	}
}
