package com.tk.gun.client;

import com.tk.gun.net.SoulAnchorStatusPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client mirror for the ready marker and colored anchor-hit glow. */
public final class SoulAnchorMarkClientState {
	public record ReadyMark(int ownerEntityId, int targetEntityId,
							long startedAt, long expiresAt) {
	}

	private record GlowMark(int color, long expiresAt) {
	}

	private static final Map<Integer, ReadyMark> READY = new HashMap<>();
	private static final Map<Integer, GlowMark> GLOW = new HashMap<>();
	private static long ticks;

	private SoulAnchorMarkClientState() {
	}

	public static void apply(SoulAnchorStatusPayload payload) {
		switch (payload.action()) {
			case SoulAnchorStatusPayload.READY_TARGET ->
					READY.put(payload.ownerEntityId(), new ReadyMark(
							payload.ownerEntityId(), payload.targetEntityId(),
							ticks, ticks + payload.durationTicks()));
			case SoulAnchorStatusPayload.CLEAR_READY_TARGET ->
					READY.remove(payload.ownerEntityId());
			case SoulAnchorStatusPayload.GLOW_TARGET ->
					GLOW.put(payload.targetEntityId(), new GlowMark(
							payload.color(),
							ticks + payload.durationTicks()));
			case SoulAnchorStatusPayload.CLEAR_GLOW_TARGET ->
					GLOW.remove(payload.targetEntityId());
			default -> {
			}
		}
	}

	public static void tick() {
		ticks++;
		READY.entrySet().removeIf(entry ->
				entry.getValue().expiresAt() <= ticks);
		GLOW.entrySet().removeIf(entry ->
				entry.getValue().expiresAt() <= ticks);
	}

	public static List<ReadyMark> readyMarks() {
		return new ArrayList<>(READY.values());
	}

	public static int glowColor(int entityId) {
		GlowMark mark = GLOW.get(entityId);
		return mark == null || mark.expiresAt() <= ticks
				? -1 : mark.color();
	}

	public static void clear() {
		READY.clear();
		GLOW.clear();
	}
}
