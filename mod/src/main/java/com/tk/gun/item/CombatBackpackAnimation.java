package com.tk.gun.item;

import java.util.HashMap;
import java.util.Map;

public final class CombatBackpackAnimation {
	public enum Phase {
		AIM, CD, READY, MAINTENANCE
	}

	private static final int PREVIEW_RENDER_ID = -1;
	private static final Map<Integer, Phase> PHASES = new HashMap<>();
	private static int renderingPlayer = -1;

	public static void setPhase(int playerId, Phase phase) {
		PHASES.put(playerId, phase);
	}

	public static Phase phase(int playerId) {
		return PHASES.getOrDefault(playerId, Phase.AIM);
	}

	public static void clear(int playerId) {
		PHASES.remove(playerId);
	}

	public static void setRenderingPlayer(int playerId) {
		renderingPlayer = playerId;
	}

	public static int renderingPlayerId() {
		return renderingPlayer;
	}

	public static Phase renderingPhase() {
		return phase(renderingPlayer);
	}

	public static void clearRenderingPlayer() {
		renderingPlayer = -1;
	}

	public static void beginModificationPreview() {
		setPhase(PREVIEW_RENDER_ID, Phase.MAINTENANCE);
		setRenderingPlayer(PREVIEW_RENDER_ID);
	}

	public static void endModificationPreview() {
		clear(PREVIEW_RENDER_ID);
		clearRenderingPlayer();
	}

	private CombatBackpackAnimation() {
	}
}
