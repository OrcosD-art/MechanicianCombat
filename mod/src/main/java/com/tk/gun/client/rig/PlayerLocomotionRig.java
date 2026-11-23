package com.tk.gun.client.rig;

import com.tk.gun.GunAirborneRules;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * GeckoLib evaluator for the ordinary player locomotion authored in the
 * player-animation bbmodel. It draws no geometry; its evaluated body hierarchy
 * is consumed by {@link PlayerLocomotionPoseBridge}.
 */
public final class PlayerLocomotionRig implements GeoReplacedEntity {
	private static final Set<String> GAIT = Set.of(
			"player_walk_front", "player_walk_back",
			"player_walk_left", "player_walk_right", "player_run");
	private static final Set<String> LOOPING = Set.of(
			"idle", "player_walk_front", "player_walk_back",
			"player_walk_left", "player_walk_right", "player_run");
	private static final Map<String, RawAnimation> RAW = new HashMap<>();
	private static final int LAND_RECOVERY_TICKS = 5;

	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);
	private final AbstractClientPlayerEntity player;
	private String currentAnimation = "idle";
	private String lastSelected;
	private boolean initialized;
	private boolean wasAirborne;
	private long fallBackUntil;

	public PlayerLocomotionRig(AbstractClientPlayerEntity player) {
		this.player = player;
	}

	public AbstractClientPlayerEntity player() {
		return player;
	}

	public String currentAnimation() {
		return currentAnimation;
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
		registrar.add(new AnimationController<>(this, "locomotion", 1, this::pick)
				.setAnimationSpeedHandler(rig ->
						GAIT.contains(currentAnimation)
								? TkGunClient.walkAnimationSpeed(player.getId())
								: 1.0D));
	}

	private PlayState pick(AnimationState<PlayerLocomotionRig> state) {
		Object raw = state.getExtraData().get(DataTickets.ENTITY);
		Entity entity = raw instanceof Entity value ? value : null;
		if (!(entity instanceof AbstractClientPlayerEntity current)) {
			return state.setAndContinue(rawFor("idle"));
		}
		long now = current.getWorld().getTime();
		boolean airborne = GunAirborneRules.isOrdinaryAirborne(current);
		if (initialized && wasAirborne && !airborne && current.isOnGround()) {
			fallBackUntil = now + LAND_RECOVERY_TICKS;
		}
		initialized = true;
		wasAirborne = airborne;

		String selected;
		if (airborne) {
			selected = current.getVelocity().y > 0.015D
					? "jump" : "fall";
		} else if (now < fallBackUntil) {
			selected = "fall_back-idle";
		} else {
			String movement = TkGunClient.walkAnimation(current.getId());
			selected = switch (movement == null ? "" : movement) {
				case "run" -> "player_run";
				case "walk_front" -> "player_walk_front";
				case "walk_back" -> "player_walk_back";
				case "walk_left" -> "player_walk_left";
				case "walk_right" -> "player_walk_right";
				default -> "idle";
			};
		}

		boolean lastWasGait = lastSelected != null
				&& GAIT.contains(lastSelected);
		int transition = lastWasGait && GAIT.contains(selected)
				? 2 : 3;
		state.getController().transitionLength(
				selected.equals(lastSelected) ? 0 : transition);
		lastSelected = selected;
		currentAnimation = selected;
		return state.setAndContinue(rawFor(selected));
	}

	private static RawAnimation rawFor(String name) {
		return RAW.computeIfAbsent(name, clip -> LOOPING.contains(clip)
				? RawAnimation.begin().thenLoop(clip)
				: RawAnimation.begin().thenPlayAndHold(clip));
	}
}
