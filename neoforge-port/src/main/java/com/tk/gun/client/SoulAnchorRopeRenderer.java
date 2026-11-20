package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.client.rig.BowgunRigRenderer;
import com.tk.gun.entity.SoulAnchorEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** World-space ribbon connecting the tool user to the anchor's tail locator. */
public final class SoulAnchorRopeRenderer {
	private static final Identifier HOLD_TEXTURE = TkGun.id(
			"textures/entity/connect_chain.png");
	private static final Identifier[] FLIGHT_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_flight_0.png"),
			TkGun.id("textures/entity/soul_anchor_flight_1.png"),
			TkGun.id("textures/entity/soul_anchor_flight_2.png"),
			TkGun.id("textures/entity/soul_anchor_flight_3.png")
	};
	private static final Identifier[] THROW_END_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_hit_0.png"),
			TkGun.id("textures/entity/soul_anchor_hit_1.png"),
			TkGun.id("textures/entity/soul_anchor_hit_2.png")
	};
	private static final Identifier[] HIT_TEXTURES = {
			TkGun.id("textures/entity/soul_anchor_3_0.png"),
			TkGun.id("textures/entity/soul_anchor_3_1.png"),
			TkGun.id("textures/entity/soul_anchor_3_2.png"),
			TkGun.id("textures/entity/soul_anchor_3_3.png"),
			TkGun.id("textures/entity/soul_anchor_3_4.png")
	};
	private static final Map<Integer, Long> HIT_STARTED_AT =
			new HashMap<>();
	private static final Set<Integer> STALL_TRANSITIONED_ANCHORS =
			new HashSet<>();
	private static final double GIF_TEXTURE_BLOCKS = 3.0D;
	private static final double IMPACT_TEXTURE_BLOCKS = 2.0D;
	private static final double CHAIN_TEXTURE_BLOCKS = 1.0D;
	private static final int FLIGHT_FRAME_TICKS = 2;
	private static final int THROW_END_FRAME_TICKS = 2;
	private static final int FLIGHT_CYCLES = 1;
	private static final int HIT_TRANSITION_TICKS = 6;

	private SoulAnchorRopeRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			if (context.world() == null) {
				return;
			}
			Vec3d camera = context.camera().getPos();
			long now = context.world().getTime();
			float tickDelta = context.tickCounter().getTickDelta(false);
			Box search = new Box(camera, camera).expand(192.0D);
			Set<Integer> visibleAnchorIds = new HashSet<>();
			for (SoulAnchorEntity anchor : context.world()
					.getEntitiesByClass(SoulAnchorEntity.class, search,
							entity -> !entity.isRemoved())) {
				visibleAnchorIds.add(anchor.getId());
				Entity owner = anchor.trackedOwner();
				if (owner == null || !owner.isAlive()) {
					HIT_STARTED_AT.remove(anchor.getId());
					continue;
				}
				Vec3d ownerPos = owner.getLerpedPos(tickDelta);
				Vec3d start = SoulAnchorSkillClientState.active(owner.getId())
						? BowgunRigRenderer.armLeftPosition(owner.getId())
						: null;
				if (start == null) {
					start = ownerPos.add(
							0.0D, owner.getHeight() * 0.62D, 0.0D);
				}
				Vec3d end = anchor.getLerpedPos(tickDelta);
				Identifier texture = textureFor(
						anchor, owner.getId(), now);
				double textureBlocks = textureBlocks(texture);
				drawRibbon(context.matrixStack(), context.consumers()
						.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture)),
						start.subtract(camera), end.subtract(camera),
						textureBlocks);
			}
			HIT_STARTED_AT.keySet()
					.removeIf(id -> !visibleAnchorIds.contains(id));
			STALL_TRANSITIONED_ANCHORS
					.removeIf(id -> !visibleAnchorIds.contains(id));
		});
	}

	private static Identifier textureFor(SoulAnchorEntity anchor,
			int ownerId, long now) {
		if (SoulAnchorSkillClientState.anchorStall(ownerId)) {
			STALL_TRANSITIONED_ANCHORS.add(anchor.getId());
			long started = HIT_STARTED_AT.computeIfAbsent(
					anchor.getId(), ignored -> now);
			return hitTransitionAtElapsed(
					Math.max(0L, now - started));
		}
		if (STALL_TRANSITIONED_ANCHORS.contains(anchor.getId())) {
			long started = HIT_STARTED_AT.getOrDefault(
					anchor.getId(), now);
			return hitTransitionAtElapsed(
					Math.max(0L, now - started));
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.FLYING) {
			return throwTransitionAtElapsed(
					SoulAnchorSkillClientState.elapsedTicks(ownerId));
		}
		if (anchor.anchorState() == SoulAnchorEntity.State.RETRACTING) {
			// Entity recall begins immediately after the hit and changes the
			// projectile to RETRACTING on the same tick. Keep the impact GIF
			// timeline alive in that case instead of jumping straight to the
			// static chain texture.
			if (anchor.hookedEntity() == null) {
				return HIT_STARTED_AT.containsKey(anchor.getId())
						? impactTransitionOrHold(anchor, now)
						: HOLD_TEXTURE;
			}
		}
		return impactTransitionOrHold(anchor, now);
	}

	private static Identifier impactTransitionOrHold(
			SoulAnchorEntity anchor, long now) {
		long started = HIT_STARTED_AT.computeIfAbsent(
				anchor.getId(), ignored -> now);
		return hitTransitionAtElapsed(Math.max(0L, now - started));
	}

	private static Identifier throwTransitionAtElapsed(long elapsed) {
		if (elapsed == Long.MAX_VALUE) {
			return HOLD_TEXTURE;
		}
		int cycleLength = FLIGHT_TEXTURES.length * FLIGHT_FRAME_TICKS;
		int flightLength = cycleLength * FLIGHT_CYCLES;
		if (elapsed < flightLength) {
			return FLIGHT_TEXTURES[(int) ((elapsed / FLIGHT_FRAME_TICKS)
					% FLIGHT_TEXTURES.length)];
		}
		long endElapsed = elapsed - flightLength;
		int endLength = THROW_END_TEXTURES.length
				* THROW_END_FRAME_TICKS;
		if (endElapsed < endLength) {
			return THROW_END_TEXTURES[(int) Math.min(
					THROW_END_TEXTURES.length - 1,
					endElapsed / THROW_END_FRAME_TICKS)];
		}
		return HOLD_TEXTURE;
	}

	/** Hit transition contains only soul_anchor_3, compressed to 6 ticks. */
	private static Identifier hitTransitionAtElapsed(long elapsed) {
		if (elapsed < HIT_TRANSITION_TICKS) {
			int frame = (int) Math.min(HIT_TEXTURES.length - 1,
					elapsed * HIT_TEXTURES.length
							/ HIT_TRANSITION_TICKS);
			return HIT_TEXTURES[frame];
		}
		return HOLD_TEXTURE;
	}

	private static void clearTracking(int anchorId) {
		HIT_STARTED_AT.remove(anchorId);
	}

	private static double textureBlocks(Identifier texture) {
		if (texture == HOLD_TEXTURE) {
			return CHAIN_TEXTURE_BLOCKS;
		}
		for (Identifier impact : HIT_TEXTURES) {
			if (texture == impact) {
				return IMPACT_TEXTURE_BLOCKS;
			}
		}
		return GIF_TEXTURE_BLOCKS;
	}

	private static void drawRibbon(MatrixStack matrices,
			VertexConsumer buffer, Vec3d start, Vec3d end,
			double textureBlocks) {
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
		double halfWidth = textureBlocks * 0.5D;
		Vec3d side = direction.crossProduct(view).normalize()
				.multiply(halfWidth);
		if (side.lengthSquared() < 1.0E-6D) {
			side = new Vec3d(halfWidth, 0.0D, 0.0D);
		}
		int segments = Math.max(1,
				(int) Math.ceil(length / textureBlocks));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		MatrixStack.Entry entry = matrices.peek();
		for (int index = 0; index < segments; index++) {
			double offset0 = index * textureBlocks;
			double offset1 = Math.min(length,
					offset0 + textureBlocks);
			double t0 = offset0 / length;
			double t1 = offset1 / length;
			float v1 = (float) Math.min(1.0D,
					(offset1 - offset0) / textureBlocks);
			Vec3d p0 = start.add(delta.multiply(t0));
			Vec3d p1 = start.add(delta.multiply(t1));
			Vec3d left0 = p0.subtract(side);
			Vec3d right0 = p0.add(side);
			Vec3d left1 = p1.subtract(side);
			Vec3d right1 = p1.add(side);
			vertex(matrix, entry, buffer, left0, 0.0F, 0.0F);
			vertex(matrix, entry, buffer, right0, 1.0F, 0.0F);
			vertex(matrix, entry, buffer, right1, 1.0F, v1);
			vertex(matrix, entry, buffer, left1, 0.0F, v1);
		}
	}

	private static void vertex(Matrix4f matrix, MatrixStack.Entry entry,
			VertexConsumer buffer, Vec3d point, float u, float v) {
		buffer.vertex(matrix, (float) point.x, (float) point.y,
						(float) point.z)
				.color(1.0F, 1.0F, 1.0F, 1.0F)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry, 0.0F, 1.0F, 0.0F);
	}
}
