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
import org.joml.Vector3f;

import java.util.List;

/** 2x2-block foreground flipbook for the Kinetic Sword's exclusive spark. */
public final class KineticSwordSparkForegroundRenderer {
	private static final float SIZE = 2.0F;
	private static final RenderLayer[] LAYERS =
			new RenderLayer[KineticSwordSparkClientState.FRAME_COUNT];

	static {
		for (int frame = 0;
				frame < KineticSwordSparkClientState.FRAME_COUNT; frame++) {
			LAYERS[frame] = AnchorLockRenderLayers.create(
					TkGun.id("textures/particle/"
							+ "kinetic_sword_skill_spark_" + frame + ".png"),
					"mechaniciancombat_kinetic_sword_skill_spark_" + frame);
		}
	}

	private KineticSwordSparkForegroundRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null) {
				return;
			}
			List<KineticSwordSparkClientState.Effect> effects =
					KineticSwordSparkClientState.effects();
			if (effects.isEmpty()) {
				return;
			}
			VertexConsumerProvider consumers = context.consumers();
			MatrixStack matrices = context.matrixStack();
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			Vec3d camera = context.camera().getPos();
			Vector3f right = context.camera().getRotation()
					.transform(new Vector3f(1.0F, 0.0F, 0.0F));
			Vector3f up = context.camera().getRotation()
					.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			long now = context.world().getTime();
			float tickDelta = context.tickCounter().getTickDelta(false);
			for (int frameIndex = 0;
					frameIndex < KineticSwordSparkClientState.FRAME_COUNT;
					frameIndex++) {
				RenderLayer layer = LAYERS[frameIndex];
				VertexConsumer buffer = consumers.getBuffer(layer);
				boolean drew = false;
				for (KineticSwordSparkClientState.Effect effect : effects) {
					float elapsed = effect.age(now) + tickDelta;
					if (elapsed >= KineticSwordSparkClientState.LIFETIME_TICKS) {
						continue;
					}
					int frame = Math.min(
							KineticSwordSparkClientState.FRAME_COUNT - 1,
							(int) (elapsed * KineticSwordSparkClientState.FRAME_COUNT
									/ KineticSwordSparkClientState.LIFETIME_TICKS));
					if (frame != frameIndex) {
						continue;
					}
					drawBillboard(matrix, buffer,
							KineticSwordSparkClientState.position(effect)
									.subtract(camera),
							right, up);
					drew = true;
				}
				if (drew && consumers
						instanceof VertexConsumerProvider.Immediate immediate) {
					immediate.draw(layer);
				}
			}
		});
	}

	private static void drawBillboard(Matrix4f matrix, VertexConsumer buffer,
			Vec3d center, Vector3f right, Vector3f up) {
		float half = SIZE * 0.5F;
		Vector3f left = new Vector3f(right).mul(-half);
		Vector3f rightSide = new Vector3f(right).mul(half);
		Vector3f down = new Vector3f(up).mul(-half);
		Vector3f top = new Vector3f(up).mul(half);
		Vec3d bottomLeft = center.add(left.x + down.x,
				left.y + down.y, left.z + down.z);
		Vec3d bottomRight = center.add(rightSide.x + down.x,
				rightSide.y + down.y, rightSide.z + down.z);
		Vec3d topRight = center.add(rightSide.x + top.x,
				rightSide.y + top.y, rightSide.z + top.z);
		Vec3d topLeft = center.add(left.x + top.x,
				left.y + top.y, left.z + top.z);
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
