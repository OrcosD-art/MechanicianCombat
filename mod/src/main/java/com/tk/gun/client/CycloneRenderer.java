package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.AnchorLockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Y-yaw-only cyclone billboards. The back copy is depth-tested against the
 * player; the front copy deliberately renders over the player.
 */
public final class CycloneRenderer {
	private static final float SIZE = 8.0F;
	private static final RenderLayer[] FRONT_LAYERS =
			new RenderLayer[CycloneClientState.FRAME_COUNT];
	private static final RenderLayer[] BACK_LAYERS =
			new RenderLayer[CycloneClientState.FRAME_COUNT];

	static {
		for (int frame = 0; frame < CycloneClientState.FRAME_COUNT; frame++) {
			FRONT_LAYERS[frame] = AnchorLockRenderLayers.create(
					TkGun.id("textures/particle/cyclone_front_" + frame + ".png"),
					"mechaniciancombat_cyclone_front_" + frame);
			BACK_LAYERS[frame] = AnchorLockRenderLayers.createDepthTested(
					TkGun.id("textures/particle/cyclone_back_" + frame + ".png"),
					"mechaniciancombat_cyclone_back_" + frame);
		}
	}

	private CycloneRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null
					|| CycloneClientState.effects().isEmpty()) {
				return;
			}
			VertexConsumerProvider consumers = context.consumers();
			MatrixStack matrices = context.matrixStack();
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			Vec3d camera = context.camera().getPos();
			long now = context.world().getTime();
			for (int frame = 0; frame < CycloneClientState.FRAME_COUNT; frame++) {
				drawFrame(consumers, matrix, camera, now, frame, false);
				drawFrame(consumers, matrix, camera, now, frame, true);
			}
		});
	}

	private static void drawFrame(VertexConsumerProvider consumers,
			Matrix4f matrix, Vec3d camera, long now, int frame,
			boolean front) {
		RenderLayer layer = (front ? FRONT_LAYERS : BACK_LAYERS)[frame];
		VertexConsumer buffer = consumers.getBuffer(layer);
		boolean drew = false;
		for (CycloneClientState.Effect effect
				: CycloneClientState.effects()) {
			int effectFrame = Math.min(CycloneClientState.FRAME_COUNT - 1,
					effect.age(now) / CycloneClientState.FRAME_STEP_TICKS);
			if (effectFrame != frame) {
				continue;
			}
			drawBillboard(matrix, buffer,
					effect.position().subtract(camera));
			drew = true;
		}
		if (drew && consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(layer);
		}
	}

	/**
	 * Upright camera-facing billboard. Only its horizontal direction follows
	 * the camera; pitch and roll are deliberately excluded.
	 */
	private static void drawBillboard(Matrix4f matrix, VertexConsumer buffer,
			Vec3d center) {
		Vec3d towardCamera = new Vec3d(-center.x, 0.0D, -center.z);
		Vec3d right;
		if (towardCamera.lengthSquared() < 1.0E-8D) {
			right = new Vec3d(1.0D, 0.0D, 0.0D);
		} else {
			towardCamera = towardCamera.normalize();
			right = new Vec3d(-towardCamera.z, 0.0D, towardCamera.x);
		}
		float half = SIZE * 0.5F;
		Vec3d x = right.multiply(half);
		Vec3d y = new Vec3d(0.0D, half, 0.0D);
		Vec3d bottomLeft = center.subtract(x).subtract(y);
		Vec3d bottomRight = center.add(x).subtract(y);
		Vec3d topRight = center.add(x).add(y);
		Vec3d topLeft = center.subtract(x).add(y);
		vertex(matrix, buffer, bottomLeft, 0.0F, 1.0F);
		vertex(matrix, buffer, bottomRight, 1.0F, 1.0F);
		vertex(matrix, buffer, topRight, 1.0F, 0.0F);
		vertex(matrix, buffer, topLeft, 0.0F, 0.0F);
	}

	private static void vertex(Matrix4f matrix, VertexConsumer buffer,
			Vec3d point, float u, float v) {
		buffer.vertex(matrix, (float) point.x, (float) point.y,
						(float) point.z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}
}
