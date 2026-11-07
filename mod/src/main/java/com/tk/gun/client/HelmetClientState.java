package com.tk.gun.client;

import com.tk.gun.item.MechanicianUndersuitItem;
import com.tk.gun.net.HelmetStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client mirror used by both the vanilla head layer and the gun rig skin layer. */
public final class HelmetClientState {
	public record Pose(String animation, float seconds) {
	}

	private record State(boolean expanded, long startedAt, int durationTicks) {
	}

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private HelmetClientState() {
	}

	public static void apply(HelmetStatePayload payload) {
		STATES.put(payload.playerUuid(), new State(
				payload.expanded(),
				payload.transitionStartedAt(),
				payload.transitionDurationTicks()));
	}

	public static void reset() {
		STATES.clear();
	}

	public static Pose pose(LivingEntity entity) {
		State state = STATES.get(entity.getUuid());
		if (state == null) {
			return new Pose("收起", 0.625F);
		}
		if (state.expanded()) {
			return state.durationTicks() > 0
					? new Pose("展开", elapsedSeconds(state))
					: new Pose("展开", 0.5F);
		}
		return state.durationTicks() > 0
				? new Pose("收起", elapsedSeconds(state))
				: new Pose("收起", 0.625F);
	}

	/**
	 * The head's second skin layer stays visible while folded, disappears as
	 * soon as expansion starts, and returns only after the fold tail frame.
	 */
	public static boolean shouldHideHair(LivingEntity entity) {
		if (!MechanicianUndersuitItem.isWearingOuterSet(entity)) {
			return false;
		}
		State state = STATES.get(entity.getUuid());
		if (state == null) {
			return false;
		}
		if (state.expanded()) {
			return true;
		}
		return state.durationTicks() > 0 && transitionActive(state);
	}

	private static float elapsedSeconds(State state) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return state.durationTicks() / 20.0F;
		}
		float tickDelta = client.getRenderTickCounter().getTickDelta(false);
		float elapsedTicks = client.world.getTime() - state.startedAt()
				+ tickDelta;
		return Math.max(0.0F,
				Math.min(state.durationTicks(), elapsedTicks)) / 20.0F;
	}

	private static boolean transitionActive(State state) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || state.durationTicks() <= 0) {
			return false;
		}
		float tickDelta = client.getRenderTickCounter().getTickDelta(false);
		return client.world.getTime() + tickDelta
				< state.startedAt() + state.durationTicks();
	}
}
