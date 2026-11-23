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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Six-frame backpack lock marker, holding its final frame until unlock. */
public final class BackpackLockOnParticleRenderer {
	private static final int FRAME_COUNT = 6;
	private static final int TICKS_PER_FRAME = 2;
	private static final float SIZE = 4.0F;
	private static final RenderLayer[] LAYERS = new RenderLayer[FRAME_COUNT];
	private static final Map<Integer, Long> STARTED_AT = new HashMap<>();

	static {
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			LAYERS[frame] = AnchorLockRenderLayers.create(
					TkGun.id("textures/particle/backpack_lock_on_"
							+ frame + ".png"),
					"mechaniciancombat_backpack_lock_on_" + frame);
		}
	}

	private BackpackLockOnParticleRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null) {
				return;
			}
			List<Integer> targetIds =
					CombatBackpackClientState.lockOnTargetEntityIds();
			long now = context.world().getTime();
			Set<Integer> live = new HashSet<>(targetIds);
			STARTED_AT.keySet().removeIf(id -> !live.contains(id));
			if (targetIds.isEmpty()) {
				return;
			}
			for (int targetId : targetIds) {
				STARTED_AT.putIfAbsent(targetId, now);
			}
			VertexConsumerProvider consumers = context.consumers();
			Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
			Vec3d camera = context.camera().getPos();
			Vector3f right = context.camera().getRotation()
					.transform(new Vector3f(1.0F, 0.0F, 0.0F));
			Vector3f up = context.camera().getRotation()
					.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			for (int frame = 0; frame < FRAME_COUNT; frame++) {
				RenderLayer layer = LAYERS[frame];
				VertexConsumer buffer = consumers.getBuffer(layer);
				boolean drew = false;
				for (int targetId : targetIds) {
					Entity target = context.world().getEntityById(targetId);
					if (target == null || !target.isAlive()) {
						continue;
					}
					long age = Math.max(0L, now - STARTED_AT.get(targetId));
					int targetFrame = Math.min(FRAME_COUNT - 1,
							(int) (age / TICKS_PER_FRAME));
					if (targetFrame != frame) {
						continue;
					}
					Vec3d center = target.getBoundingBox().getCenter()
							.subtract(camera);
					drawBillboard(matrix, buffer, center, right, up);
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

	public static void clear() {
		STARTED_AT.clear();
	}
}
