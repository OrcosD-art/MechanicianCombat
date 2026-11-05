package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackUpgradeData;
import com.tk.gun.item.CombatBackpackItem;
import com.tk.gun.net.CombatBackpackStatusPayload;
import com.tk.gun.net.JumpBackpackPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Client mirror and player-pose sampler for the jump backpack.
 *
 * <p>The backpack itself is a GeckoLib item animation. These samples drive the
 * authored player {@code jump}/{@code falling} flipbook through the existing
 * PlayerAnimator bridge, which also works when no gun is held.</p>
 */
public final class JumpBackpackClientState {
	public enum Phase {
		IDLE,
		READY_TO_FLY,
		FLYING,
		FALLING,
		CLOSE,
		COOLDOWN
	}

	private static final int OPTIMISTIC_TIMEOUT_TICKS = 12;
	private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
	private static int ticks;
	private static boolean sentJumpPressed;
	private static boolean previousJumpKey;

	private static final class Entry {
		Phase phase = Phase.IDLE;
		boolean optimistic;
		int optimisticUntil;
	}

	public static void tick(MinecraftClient client) {
		ticks++;
		ENTRIES.values().removeIf(entry -> entry.optimistic
				&& ticks > entry.optimisticUntil);
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			if (sentJumpPressed) {
				ClientPlayNetworking.send(new JumpBackpackPayload(false));
				sentJumpPressed = false;
			}
			previousJumpKey = false;
			return;
		}

		handleJumpKey(client, client.currentScreen == null
				&& client.options.jumpKey.isPressed());
		if (TkGun.holdingGun(player)) {
			// Gun rigs own every player body channel. Keep only the backpack item
			// animation active while the gun animation is rendering the player.
			JumpBackpackPlayerAnimation.clear(player.getUuid());
		} else {
			JumpBackpackPlayerAnimation.apply(player, phase(player.getId()));
		}
	}

	/**
	 * Input is forwarded from KeyboardInput before vanilla movement so the
	 * server does not receive a jump packet after the player has already been
	 * marked airborne. Calling it again at end-of-tick is harmless because the
	 * edge detector is shared.
	 */
	public static void handleJumpKey(MinecraftClient client, boolean jumpPressed) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		boolean equipped = BackpackUpgradeData.canUseJumpThruster(
				CombatBackpackItem.equippedStack(player));
		boolean rawPressed = client.currentScreen == null
				&& jumpPressed;
		boolean risingEdge = rawPressed && !previousJumpKey;
		previousJumpKey = rawPressed;
		boolean eligible = equipped && !TkGun.holdingGun(player);
		if (eligible && rawPressed && risingEdge && !sentJumpPressed) {
			ClientPlayNetworking.send(new JumpBackpackPayload(true));
			sentJumpPressed = true;
		} else if (eligible && !rawPressed && sentJumpPressed) {
			ClientPlayNetworking.send(new JumpBackpackPayload(false));
			sentJumpPressed = false;
		} else if (!eligible && sentJumpPressed) {
			ClientPlayNetworking.send(new JumpBackpackPayload(false));
			sentJumpPressed = false;
		}
	}

	public static void applyStatus(int playerId, int statusPhase, int remainingTicks) {
		Entry entry = ENTRIES.computeIfAbsent(playerId, ignored -> new Entry());
		Phase next = switch (statusPhase) {
			case CombatBackpackStatusPayload.PHASE_READY_TO_FLY -> Phase.READY_TO_FLY;
			case CombatBackpackStatusPayload.PHASE_FLYING -> Phase.FLYING;
			case CombatBackpackStatusPayload.PHASE_FALLING -> Phase.FALLING;
			case CombatBackpackStatusPayload.PHASE_CLOSE -> Phase.CLOSE;
			case CombatBackpackStatusPayload.PHASE_COOLDOWN -> Phase.COOLDOWN;
			default -> Phase.IDLE;
		};
		if (entry.phase != next) {
			entry.phase = next;
		}
		entry.optimistic = false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null
				&& client.world.getEntityById(playerId)
				instanceof AbstractClientPlayerEntity player) {
			if (TkGun.holdingGun(player)) {
				JumpBackpackPlayerAnimation.clear(player.getUuid());
			} else {
				JumpBackpackPlayerAnimation.apply(player, next);
			}
		}
		if (next == Phase.IDLE) {
			ENTRIES.remove(playerId);
		}
	}

	public static boolean isActive(int playerId) {
		Phase phase = phase(playerId);
		return phase == Phase.READY_TO_FLY || phase == Phase.FLYING
				|| phase == Phase.FALLING;
	}

	public static Phase phase(int playerId) {
		Entry entry = ENTRIES.get(playerId);
		return entry == null ? Phase.IDLE : entry.phase;
	}

	public static void reset(int playerId) {
		ENTRIES.remove(playerId);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null
				&& client.world.getEntityById(playerId)
				instanceof AbstractClientPlayerEntity player) {
			JumpBackpackPlayerAnimation.clear(player.getUuid());
		}
	}

	public static void resetAll() {
		ENTRIES.clear();
		JumpBackpackPlayerAnimation.clearAll();
		sentJumpPressed = false;
		previousJumpKey = false;
	}

	private JumpBackpackClientState() {
	}
}
