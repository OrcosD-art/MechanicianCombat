package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.HashMap;
import java.util.Map;

/** Six-frame energy texture played on the authored Kinetic Sword energy plane. */
public final class KineticSwordEnergyEffect {
	public static final Identifier TEXTURE =
			TkGun.id("textures/entity/kinetic_sword_energy_effect.png");
	public static final String BONE = "energy_effect";
	private static final int FRAMES = 6;
	private static final int FRAMES_PER_SECOND = 9;
	private static final int HOLD_TICKS = 20;
	private static final int FADE_TICKS = 20;
	private static final int HIDE_TICKS = HOLD_TICKS + FADE_TICKS;
	private static final float FRAME_SIZE = 48.0F;
	private static final float MODEL_TEXTURE_SIZE = 64.0F;
	private static final float MIN_UV_SPAN = 4.0F / MODEL_TEXTURE_SIZE;
	private static final Map<Integer, State> STATES = new HashMap<>();

	public static boolean isEnergyBone(String name) {
		return BONE.equals(name);
	}

	public static int colorFor(Entity entity) {
		if (entity == null || entity.getWorld() == null) {
			return 0;
		}
		int id = entity.getId();
		long now = entity.getWorld().getTime();
		boolean attacking = entity instanceof PlayerEntity player
				&& TkGun.isKineticSword(player)
				&& isAttackState(id);
		State state = STATES.get(id);
		if (attacking) {
			if (state == null) {
				state = new State(now, now);
			} else {
				state = new State(state.startedAt(), now);
			}
			STATES.put(id, state);
			return 0xFFFFFFFF;
		}
		if (state == null) {
			return 0;
		}
		long elapsed = now - state.lastAttackAt();
		if (elapsed >= HIDE_TICKS) {
			STATES.remove(id);
			return 0;
		}
		if (elapsed < HOLD_TICKS) {
			return 0xFFFFFFFF;
		}
		float fade = 1.0F
				- (elapsed - HOLD_TICKS) / (float) FADE_TICKS;
		int alpha = Math.max(0, Math.min(255,
				Math.round(fade * 255.0F)));
		return alpha << 24 | 0x00FFFFFF;
	}

	public static boolean applyVisibility(GeoBone bone, Entity entity) {
		int colour = colorFor(entity);
		boolean visible = (colour >>> 24) > 0;
		float scale = visible ? 1.0F : 1.0E-4F;
		bone.setScaleX(scale);
		bone.setScaleY(scale);
		bone.setScaleZ(scale);
		return visible;
	}

	public static int frameFor(Entity entity) {
		if (entity == null || entity.getWorld() == null) {
			return 0;
		}
		State state = STATES.get(entity.getId());
		if (state == null) {
			return 0;
		}
		long elapsed = entity.getWorld().getTime() - state.startedAt();
		long animationFrame = elapsed * FRAMES_PER_SECOND / 20L;
		return (int) Math.floorMod(animationFrame, (long) FRAMES);
	}

	public static GeoCube remapUv(GeoCube cube, int frame) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] remapped = new GeoQuad[source.length];
		for (int index = 0; index < source.length; index++) {
			GeoQuad quad = source[index];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			float minU = Float.POSITIVE_INFINITY;
			float maxU = Float.NEGATIVE_INFINITY;
			float minV = Float.POSITIVE_INFINITY;
			float maxV = Float.NEGATIVE_INFINITY;
			for (GeoVertex vertex : vertices) {
				minU = Math.min(minU, vertex.texU());
				maxU = Math.max(maxU, vertex.texU());
				minV = Math.min(minV, vertex.texV());
				maxV = Math.max(maxV, vertex.texV());
			}
			if (maxU - minU < MIN_UV_SPAN
					|| maxV - minV < MIN_UV_SPAN) {
				continue;
			}
			GeoVertex[] updated = new GeoVertex[vertices.length];
			for (int vertexIndex = 0;
					vertexIndex < vertices.length; vertexIndex++) {
				GeoVertex vertex = vertices[vertexIndex];
				updated[vertexIndex] = vertex.withUVs(
						vertex.texU() * MODEL_TEXTURE_SIZE / FRAME_SIZE,
						(frame + vertex.texV()
								* MODEL_TEXTURE_SIZE / FRAME_SIZE)
								/ FRAMES);
			}
			remapped[index] = new GeoQuad(updated, quad.normal(),
					quad.direction());
		}
		return new GeoCube(remapped, cube.pivot(), cube.rotation(),
				cube.size(), cube.inflate(), cube.mirror());
	}

	private static boolean isAttackState(int playerId) {
		GunClientState.Entry entry = GunClientState.get(playerId);
		return entry != null && entry.state == TkGun.State.MELEE;
	}

	private record State(long startedAt, long lastAttackAt) {
	}

	private KineticSwordEnergyEffect() {
	}
}
