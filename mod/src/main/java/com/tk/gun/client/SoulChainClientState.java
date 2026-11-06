package com.tk.gun.client;

import com.tk.gun.net.SoulChainPayload;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Short-lived world-space A -> B -> C chain visuals. */
public final class SoulChainClientState {
	public record Effect(int aEntityId, int bEntityId, int cEntityId,
						 Vec3d a, Vec3d b, Vec3d c,
						 boolean hasC, long expiresAt, long startedAt) {
		public float alphaAt(long now) {
			long elapsed = Math.max(0L, now - startedAt);
			if (elapsed <= 10L) {
				return 1.0F;
			}
			return Math.max(0.0F,
					1.0F - (elapsed - 10L) / 20.0F);
		}
	}

	private static final List<Effect> EFFECTS = new ArrayList<>();

	public static void apply(SoulChainPayload payload, long now) {
		EFFECTS.add(new Effect(
				payload.aEntityId(), payload.bEntityId(), payload.cEntityId(),
				new Vec3d(payload.ax(), payload.ay(), payload.az()),
				new Vec3d(payload.bx(), payload.by(), payload.bz()),
				new Vec3d(payload.cx(), payload.cy(), payload.cz()),
				payload.hasC(), now + payload.durationTicks(), now));
	}

	public static void tick(long now) {
		Iterator<Effect> iterator = EFFECTS.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().expiresAt() <= now) {
				iterator.remove();
			}
		}
	}

	public static List<Effect> effects() {
		return EFFECTS;
	}

	public static void clear() {
		EFFECTS.clear();
	}

	private SoulChainClientState() {
	}
}
