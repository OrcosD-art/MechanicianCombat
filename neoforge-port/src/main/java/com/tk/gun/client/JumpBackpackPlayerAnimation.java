package com.tk.gun.client;

import com.tk.gun.TkGun;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native PlayerAnimator playback for the player jump backpack poses. */
public final class JumpBackpackPlayerAnimation {
	private static final int LAYER_PRIORITY = 25;
	private static final Identifier JUMP_ANIMATION =
			TkGun.id("backpack_air_jump");
	private static final Identifier FALLING_ANIMATION =
			TkGun.id("backpack_air_falling");
	private static final Map<UUID, ModifierLayer<IAnimation>> LAYERS = new HashMap<>();
	private static final Map<UUID, Identifier> ACTIVE = new HashMap<>();

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			LAYERS.put(player.getUuid(), layer);
			ACTIVE.remove(player.getUuid());
		});
	}

	public static void apply(AbstractClientPlayerEntity player,
			JumpBackpackClientState.Phase phase) {
		Identifier clip = switch (phase) {
			case READY_TO_FLY, FLYING -> JUMP_ANIMATION;
			case FALLING -> FALLING_ANIMATION;
			default -> null;
		};
		UUID uuid = player.getUuid();
		if (clip == null) {
			LAYERS.computeIfPresent(uuid, (ignored, layer) -> {
				layer.setAnimation(null);
				return layer;
			});
			ACTIVE.remove(uuid);
			return;
		}
		if (clip.equals(ACTIVE.get(uuid))) {
			return;
		}
		ModifierLayer<IAnimation> layer = layerFor(player);
		if (layer == null) {
			TkGun.LOGGER.warn("[mechaniciancombat] PlayerAnimator layer unavailable for {}", uuid);
			return;
		}
		IPlayable playable = PlayerAnimationRegistry.getAnimation(clip);
		if (playable == null) {
			TkGun.LOGGER.warn("[mechaniciancombat] Missing PlayerAnimator animation {}", clip);
			return;
		}
		layer.setAnimation(new HoldAnimation(clip, playable.playAnimation()));
		ACTIVE.put(uuid, clip);
		TkGun.LOGGER.info("[mechaniciancombat] PlayerAnimator play {} for {}", clip, uuid);
	}

	private static ModifierLayer<IAnimation> layerFor(
			AbstractClientPlayerEntity player) {
		ModifierLayer<IAnimation> layer = LAYERS.get(player.getUuid());
		if (layer != null) {
			return layer;
		}
		try {
			layer = new ModifierLayer<>();
			PlayerAnimationAccess.getPlayerAnimLayer(player)
					.addAnimLayer(LAYER_PRIORITY, layer);
			LAYERS.put(player.getUuid(), layer);
			return layer;
		} catch (RuntimeException exception) {
			return null;
		}
	}

	public static void clear(UUID uuid) {
		ModifierLayer<IAnimation> layer = LAYERS.remove(uuid);
		if (layer != null) {
			layer.setAnimation(null);
		}
		ACTIVE.remove(uuid);
	}

	public static void clearAll() {
		for (ModifierLayer<IAnimation> layer : LAYERS.values()) {
			layer.setAnimation(null);
		}
		LAYERS.clear();
		ACTIVE.clear();
	}

	/**
	 * Keeps the final authored frame active after a one-shot clip reaches its
	 * end. PlayerAnimator's normal one-shot player deactivates at that point.
	 */
	private static final class HoldAnimation implements IAnimation {
		private final Identifier clip;
		private final IActualAnimation<?> animation;
		private final Set<String> loggedParts = new HashSet<>();
		private boolean running = true;
		private int ageTicks;

		private HoldAnimation(Identifier clip, IActualAnimation<?> animation) {
			this.clip = clip;
			this.animation = animation;
		}

		@Override
		public void tick() {
			if (running && animation.isActive()) {
				animation.tick();
				ageTicks++;
			} else {
				running = false;
			}
		}

		@Override
		public boolean isActive() {
			return true;
		}

		@Override
		public Vec3f get3DTransform(String part, TransformType type,
				float tickDelta, Vec3f fallback) {
			Vec3f value = animation.get3DTransform(part, type, tickDelta, fallback);
			String diagnosticPart = part + ":" + type + ":" + ageTicks;
			if (Set.of("body", "torso", "leftArm", "rightArm",
					"head", "leftLeg", "rightLeg").contains(part)
					&& (ageTicks == 0 || ageTicks == 3 || ageTicks == 5)
					&& loggedParts.add(diagnosticPart)) {
				TkGun.LOGGER.info(
						"[mechaniciancombat] jump pose transform clip={} tick={} part={} type={} "
								+ "fallback=({},{},{}) result=({},{},{})",
						clip, ageTicks, part, type,
						fallback.getX(), fallback.getY(), fallback.getZ(),
						value.getX(), value.getY(), value.getZ());
			}
			return value;
		}

		@Override
		public void setupAnim(float tickDelta) {
			animation.setupAnim(tickDelta);
		}

		@Override
		public FirstPersonMode getFirstPersonMode(float tickDelta) {
			return animation.getFirstPersonMode(tickDelta);
		}

		@Override
		public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
			return animation.getFirstPersonConfiguration(tickDelta);
		}
	}

	private JumpBackpackPlayerAnimation() {
	}
}
