package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.AnchorLockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Camera-facing anchor-lock marker rendered without depth testing. */
public final class AnchorLockParticleRenderer {
	private static final int FRAME_COUNT = 6;
	private static final int TICKS_PER_FRAME = 2;
	private static final RenderLayer[] LAYERS = new RenderLayer[FRAME_COUNT];

	static {
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			LAYERS[frame] = createLayer(
					TkGun.id("textures/particle/anchor_lock_" + frame + ".png"),
					frame);
		}
	}

	private AnchorLockParticleRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null
					|| SoulAnchorMarkClientState.readyMarks().isEmpty()) {
				return;
			}
			int frame = Math.floorMod(
					(int) (context.world().getTime() / TICKS_PER_FRAME),
					FRAME_COUNT);
			RenderLayer layer = LAYERS[frame];
			VertexConsumerProvider consumers = context.consumers();
			VertexConsumer buffer = consumers.getBuffer(layer);
			MatrixStack matrices = context.matrixStack();
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			Vec3d camera = context.camera().getPos();
			Vector3f right = context.camera().getRotation()
					.transform(new Vector3f(1.0F, 0.0F, 0.0F));
			Vector3f up = context.camera().getRotation()
					.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			for (SoulAnchorMarkClientState.ReadyMark mark
					: SoulAnchorMarkClientState.readyMarks()) {
				Entity entity = context.world()
						.getEntityById(mark.targetEntityId());
				if (entity == null || !entity.isAlive()) {
					continue;
				}
				double centerY = entity.getY() + entity.getHeight() * 0.55D;
				Vec3d center = new Vec3d(
						entity.getX(), centerY, entity.getZ()).subtract(camera);
				float size = MathHelper.clamp(
						Math.max(entity.getWidth(), entity.getHeight()) * 1.35F,
						1.8F, 3.4F);
				drawBillboard(matrix, buffer, center, right, up, size);
			}
			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw(layer);
			}
		});
	}

	private static RenderLayer createLayer(Identifier texture, int frame) {
		return AnchorLockRenderLayers.create(texture,
				"mechaniciancombat_anchor_lock_" + frame);
	}

	private static void drawBillboard(Matrix4f matrix, VertexConsumer buffer,
			Vec3d center, Vector3f right, Vector3f up, float size) {
		float half = size * 0.5F;
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
