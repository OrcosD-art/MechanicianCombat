package com.tk.gun.client;

import com.tk.gun.net.CombatBackpackStatusPayload;

/** Chooses the authored first-person prop and its current animation phase. */
public final class CombatPropFpaClientState {
	public enum Prop {
		NONE,
		HUNTER_ANCHOR,
		PORTABLE_TURRET,
		AUTO_TURRET
	}

	public static Prop prop(int playerId) {
		return switch (CombatBackpackClientState.launcherType(playerId)) {
			case PORTABLE -> Prop.PORTABLE_TURRET;
			case AUTO -> Prop.AUTO_TURRET;
			default -> Prop.NONE;
		};
	}

	public static double turretSeconds(int playerId, float tickDelta) {
		int start;
		switch (turretMode(playerId)) {
			case READY -> start =
					CombatBackpackClientState.turretActivationAt(playerId);
			case CD -> start =
					CombatBackpackClientState.turretCdStartedAt(playerId);
			default -> {
				return 0.0D;
			}
		}
		if (start < 0) {
			start = CombatBackpackClientState.clientTicks();
		}
		return Math.min(0.25D, Math.max(0.0D,
				CombatBackpackClientState.clientTicks() - start
						+ tickDelta) / 20.0D);
	}

	public static long turretStartedAt(int playerId) {
		com.tk.gun.item.CombatPropFpaRenderState.Mode mode =
				turretMode(playerId);
		int start = switch (mode) {
			case READY -> CombatBackpackClientState
					.turretActivationAt(playerId);
			case CD -> CombatBackpackClientState
					.turretCdStartedAt(playerId);
			default -> (int) CombatBackpackClientState
					.animationStartedAt(playerId);
		};
		return ((long) start << 8) | mode.ordinal();
	}

	public static com.tk.gun.item.CombatPropFpaRenderState.Mode turretMode(
			int playerId) {
		if (CombatBackpackClientState.turretCdLatched(playerId)
				|| CombatBackpackClientState.statusPhase(playerId)
					== CombatBackpackStatusPayload.PHASE_COOLDOWN) {
			return com.tk.gun.item.CombatPropFpaRenderState.Mode.CD;
		}
		return com.tk.gun.item.CombatPropFpaRenderState.Mode.READY;
	}

	private CombatPropFpaClientState() {
	}
}
