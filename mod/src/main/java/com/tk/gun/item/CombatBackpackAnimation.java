package com.tk.gun.item;

import java.util.HashMap;
import java.util.Map;

public final class CombatBackpackAnimation {
	public enum Phase {
		AIM, CD, READY, MAINTENANCE, READY_TO_FLY, FLYING, CLOSE,
		MISSILE_AIM, MISSILE_CD
	}

	private static final int PREVIEW_RENDER_ID = -1;
	private static final Map<Integer, Phase> PHASES = new HashMap<>();
	private static final Map<Integer, Phase> MISSILE_PHASES = new HashMap<>();
	private static int renderingPlayer = -1;
	private static boolean manualJumpRendering;

	public static void setPhase(int playerId, Phase phase) {
		PHASES.put(playerId, phase);
	}

	public static Phase phase(int playerId) {
		return PHASES.getOrDefault(playerId, Phase.AIM);
	}

	public static void clear(int playerId) {
		PHASES.remove(playerId);
		MISSILE_PHASES.remove(playerId);
	}

	public static void clearAll() {
		PHASES.clear();
		MISSILE_PHASES.clear();
		renderingPlayer = -1;
		manualJumpRendering = false;
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

	public static void setMissilePhase(int playerId, Phase phase) {
		if (phase == Phase.MISSILE_AIM || phase == Phase.MISSILE_CD) {
			MISSILE_PHASES.put(playerId, phase);
		} else {
			MISSILE_PHASES.remove(playerId);
		}
	}

	public static Phase missilePhase(int playerId) {
		return MISSILE_PHASES.getOrDefault(playerId, Phase.READY);
	}

	public static void clearRenderingPlayer() {
		renderingPlayer = -1;
		manualJumpRendering = false;
	}

	public static void setManualJumpRendering(boolean manual) {
		manualJumpRendering = manual;
	}

	public static boolean manualJumpRendering() {
		return manualJumpRendering;
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
