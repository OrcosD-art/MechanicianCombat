package com.tk.gun.client;

import com.tk.gun.GunAirborneRules;
import com.tk.gun.TkGun;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Normal third-person locomotion authored in the player animation BBModel.
 *
 * <p>The layer deliberately has a lower priority than gun, skill and backpack
 * layers. It only emits the head position channel, so the head follows the
 * authored torso while its rotation remains vanilla.
 */
public final class PlayerLocomotionAnimation {
	private static final int LAYER_PRIORITY = 1;
	private static final int LAND_RECOVERY_TICKS = 5;
	private static final Identifier IDLE =
			TkGun.id("player_idle");
	private static final Identifier WALK_FRONT =
			TkGun.id("player_walk_front");
	private static final Identifier WALK_BACK =
			TkGun.id("player_walk_back");
	private static final Identifier WALK_LEFT =
			TkGun.id("player_walk_left");
	private static final Identifier WALK_RIGHT =
			TkGun.id("player_walk_right");
	private static final Identifier RUN =
			TkGun.id("player_run");
	private static final Identifier JUMP =
			TkGun.id("player_jump");
	private static final Identifier FALL =
			TkGun.id("player_fall");
	private static final Identifier FALL_BACK_IDLE =
			TkGun.id("player_fall_back_idle");
	private static final Set<String> UPPER_BODY =
			Set.of("head", "torso", "rightArm", "leftArm");
	private static final Set<String> FULL_BODY =
			Set.of("body", "head", "torso", "rightArm", "leftArm",
					"rightLeg", "leftLeg");
	private static final Set<String> UPPER_BODY_VANILLA =
			Set.of("body", "torso", "rightArm", "leftArm");
	private static final Map<UUID, ModifierLayer<IAnimation>> LAYERS =
			new HashMap<>();
	private static final Map<UUID, State> STATES = new HashMap<>();

	private static final class State {
		Identifier clip;
		LocomotionAnimation animation;
		boolean initialized;
		boolean wasAirborne;
		long fallBackUntil;
	}

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			LAYERS.put(player.getUuid(), layer);
			STATES.remove(player.getUuid());
		});
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null) {
			clearAll();
			return;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			apply(player);
		}
	}

	private static void apply(AbstractClientPlayerEntity player) {
		UUID uuid = player.getUuid();
		State state = STATES.computeIfAbsent(uuid, ignored -> new State());
		if (com.tk.gun.client.rig.PlayerLocomotionRigClient
				.hasActivePose(player)) {
			clearState(uuid, state);
			state.initialized = false;
			state.wasAirborne = false;
			state.fallBackUntil = 0L;
			return;
		}
		if (!isSupported(player)) {
			clearState(uuid, state);
			state.initialized = false;
			state.wasAirborne = false;
			state.fallBackUntil = 0L;
			return;
		}

		long now = player.getWorld().getTime();
		boolean airborne = GunAirborneRules.isOrdinaryAirborne(player);
		if (state.initialized && state.wasAirborne && !airborne
				&& player.isOnGround()) {
			state.fallBackUntil = now + LAND_RECOVERY_TICKS;
		}
		state.initialized = true;
		state.wasAirborne = airborne;

		Identifier selected;
		if (airborne) {
			selected = player.getVelocity().y > 0.015D ? JUMP : FALL;
		} else if (now < state.fallBackUntil) {
			selected = FALL_BACK_IDLE;
		} else {
			String movement = TkGunClient.walkAnimation(player.getId());
			selected = switch (movement == null ? "" : movement) {
				case "run" -> RUN;
				case "walk_front" -> WALK_FRONT;
				case "walk_back" -> WALK_BACK;
				case "walk_left" -> WALK_LEFT;
				case "walk_right" -> WALK_RIGHT;
				default -> IDLE;
			};
		}
		boolean upperBodyVanilla = player.handSwinging
				|| player.isUsingItem();
		int transitionTicks = isGait(state.clip) && isGait(selected)
				? PlayerAnimationTransitions.QUICK_TICKS
				: PlayerAnimationTransitions.NORMAL_TICKS;
		play(player.getId(), uuid, state, selected,
				transitionTicks,
				upperBodyVanilla ? UPPER_BODY_VANILLA : Set.of());
	}

	/**
	 * States not represented by the authored locomotion set intentionally clear
	 * the layer. PlayerAnimator then leaves every limb to vanilla/other layers.
	 */
	private static boolean isSupported(AbstractClientPlayerEntity player) {
		return player.isAlive() && !player.isSpectator()
				&& !player.isSneaking() && !player.hasVehicle()
				&& !player.isFallFlying() && !player.isTouchingWater()
				&& !player.isSwimming() && !player.isInLava()
				&& !player.isClimbing() && !player.isUsingRiptide()
				&& !player.isSleeping() && !player.getAbilities().flying
				&& !player.hasStatusEffect(
						net.minecraft.entity.effect.StatusEffects.LEVITATION);
	}

	private static void play(int entityId, UUID uuid, State state,
			Identifier clip, int transitionTicks,
			Set<String> suppressedParts) {
		if (clip.equals(state.clip)) {
			if (state.animation != null) {
				state.animation.setSuppressedParts(suppressedParts);
			}
			return;
		}
		ModifierLayer<IAnimation> layer = layerFor(uuid);
		if (layer == null) {
			return;
		}
		IPlayable playable = PlayerAnimationRegistry.getAnimation(clip);
		if (playable == null) {
			TkGun.LOGGER.warn(
					"[mechaniciancombat] Missing player locomotion clip {}",
					clip);
			if (state.clip != null) {
				PlayerAnimationTransitions.clear(
						layer, transitionTicks);
				state.clip = null;
				state.animation = null;
			}
			return;
		}
		int startTick = 0;
		IActualAnimation<?> actual;
		if (playable instanceof KeyframeAnimation data) {
			startTick = transitionStartTick(
					entityId, state, clip, data);
			actual = new KeyframeAnimationPlayer(
					data, startTick, true);
		} else {
			actual = playable.playAnimation();
		}
		actual.setFirstPersonMode(FirstPersonMode.NONE);
		actual.setFirstPersonConfiguration(
				new FirstPersonConfiguration(false, false, false, false));
		boolean upperOnly = FALL_BACK_IDLE.equals(clip);
		state.animation = new LocomotionAnimation(actual,
				upperOnly ? UPPER_BODY : FULL_BODY, suppressedParts);
		PlayerAnimationTransitions.play(
				layer, state.animation, transitionTicks);
		state.clip = clip;
	}

	private static int transitionStartTick(int entityId, State state,
			Identifier nextClip, KeyframeAnimation nextData) {
		if (!isGait(nextClip)) {
			return 0;
		}
		int nextLength = Math.max(1, nextData.endTick);
		float phase;
		if (isGait(state.clip) && state.animation != null) {
			int previousTick = state.animation.currentTick();
			int previousLength = Math.max(
					1, state.animation.lengthTicks());
			phase = (float) previousTick / previousLength;
		} else {
			phase = TkGunClient.walkPhase(entityId);
		}
		int startTick = Math.round(
				MathHelper.clamp(phase, 0.0F, 0.999999F) * nextLength);
		return Math.min(startTick, nextLength - 1);
	}

	private static boolean isGait(Identifier clip) {
		return WALK_FRONT.equals(clip)
				|| WALK_BACK.equals(clip)
				|| WALK_LEFT.equals(clip)
				|| WALK_RIGHT.equals(clip)
				|| RUN.equals(clip);
	}

	private static ModifierLayer<IAnimation> layerFor(UUID uuid) {
		ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
		if (layer != null) {
			return layer;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (!player.getUuid().equals(uuid)) {
				continue;
			}
			try {
				layer = new ModifierLayer<>();
				PlayerAnimationAccess.getPlayerAnimLayer(player)
						.addAnimLayer(LAYER_PRIORITY, layer);
				LAYERS.put(uuid, layer);
				return layer;
			} catch (RuntimeException exception) {
				return null;
			}
		}
		return null;
	}

	private static void clearState(UUID uuid, State state) {
		ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
		if (layer != null && state.clip != null) {
			PlayerAnimationTransitions.clear(
					layer, PlayerAnimationTransitions.QUICK_TICKS);
		}
		state.clip = null;
		state.animation = null;
	}

	public static void clear(UUID uuid) {
		State state = STATES.remove(uuid);
		ModifierLayer<IAnimation> layer = LAYERS.remove(uuid);
		if (layer != null) {
			layer.setAnimation(null);
		}
		if (state != null) {
			state.clip = null;
			state.animation = null;
		}
	}

	public static void clearAll() {
		for (ModifierLayer<IAnimation> layer : LAYERS.values()) {
			layer.setAnimation(null);
		}
		LAYERS.clear();
		STATES.clear();
	}

	private static final class LocomotionAnimation implements IAnimation {
		private final IActualAnimation<?> animation;
		private final Set<String> allowedParts;
		private volatile Set<String> suppressedParts;
		private boolean running = true;

		private LocomotionAnimation(IActualAnimation<?> animation,
				Set<String> allowedParts, Set<String> suppressedParts) {
			this.animation = animation;
			this.allowedParts = allowedParts;
			this.suppressedParts = Set.copyOf(suppressedParts);
		}

		private void setSuppressedParts(Set<String> parts) {
			suppressedParts = Set.copyOf(parts);
		}

		@Override
		public void tick() {
			if (running && animation.isActive()) {
				animation.tick();
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
			if (!allowedParts.contains(part)) {
				return fallback;
			}
			if ("head".equals(part)) {
				return type == TransformType.POSITION
						? animation.get3DTransform(part, type, tickDelta,
								fallback)
						: fallback;
			}
			if (suppressedParts.contains(part)) {
				return fallback;
			}
			return animation.get3DTransform(part, type, tickDelta, fallback);
		}

		@Override
		public void setupAnim(float tickDelta) {
			animation.setupAnim(tickDelta);
		}

		private int currentTick() {
			return animation instanceof KeyframeAnimationPlayer player
					? player.getCurrentTick() : 0;
		}

		private int lengthTicks() {
			return animation instanceof KeyframeAnimationPlayer player
					? player.getData().endTick : 1;
		}

		@Override
		public FirstPersonMode getFirstPersonMode(float tickDelta) {
			return FirstPersonMode.NONE;
		}

		@Override
		public FirstPersonConfiguration getFirstPersonConfiguration(
				float tickDelta) {
			return new FirstPersonConfiguration(false, false, false, false);
		}
	}

	private PlayerLocomotionAnimation() {
	}
}
