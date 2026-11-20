package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.AnchorLockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Horizontal ten-frame ATK3 ground effect attached to E1. */
public final class RoundAttackRenderer {
	private static final int FRAME_COUNT = 11;
	private static final int FRAMES_PER_SECOND = 13;
	private static final float HALF_SIZE = 4.0F;
	private static final RenderLayer[] LAYERS = new RenderLayer[FRAME_COUNT];

	static {
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			LAYERS[frame] = createLayer(
					TkGun.id("textures/particle/round_attack_"
							+ frame + ".png"), frame);
		}
	}

	private RoundAttackRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null
					|| RoundAttackClientState.effects().isEmpty()) {
				return;
			}
			VertexConsumerProvider consumers = context.consumers();
			MatrixStack matrices = context.matrixStack();
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			Vec3d camera = context.camera().getPos();
			long now = context.world().getTime();
			for (int frameIndex = 0;
					frameIndex < FRAME_COUNT; frameIndex++) {
				RenderLayer layer = LAYERS[frameIndex];
				VertexConsumer buffer = consumers.getBuffer(layer);
				boolean drew = false;
				for (RoundAttackClientState.Effect effect
						: RoundAttackClientState.effects()) {
					int frame = Math.min(FRAME_COUNT - 1,
							effect.age(now) * FRAMES_PER_SECOND / 20);
					if (frame != frameIndex) {
						continue;
					}
					drawGroundQuad(matrix, buffer,
							effect.position().subtract(camera));
					drew = true;
				}
				if (drew && consumers
						instanceof VertexConsumerProvider.Immediate immediate) {
					immediate.draw(layer);
				}
			}
		});
	}

	private static RenderLayer createLayer(Identifier texture, int frame) {
		return AnchorLockRenderLayers.createDepthTested(texture,
				"mechaniciancombat_round_attack_" + frame);
	}

	private static void drawGroundQuad(Matrix4f matrix,
			VertexConsumer buffer, Vec3d center) {
		float y = (float) center.y + 0.025F;
		vertex(matrix, buffer, (float) center.x - HALF_SIZE, y,
				(float) center.z - HALF_SIZE, 0.0F, 0.0F);
		vertex(matrix, buffer, (float) center.x + HALF_SIZE, y,
				(float) center.z - HALF_SIZE, 1.0F, 0.0F);
		vertex(matrix, buffer, (float) center.x + HALF_SIZE, y,
				(float) center.z + HALF_SIZE, 1.0F, 1.0F);
		vertex(matrix, buffer, (float) center.x - HALF_SIZE, y,
				(float) center.z + HALF_SIZE, 0.0F, 1.0F);
	}

	private static void vertex(Matrix4f matrix, VertexConsumer buffer,
			float x, float y, float z, float u, float v) {
		buffer.vertex(matrix, x, y, z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}
}
