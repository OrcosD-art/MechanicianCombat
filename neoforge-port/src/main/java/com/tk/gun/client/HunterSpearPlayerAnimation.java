package com.tk.gun.client;

import com.tk.gun.TkGun;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
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

/** Upper-body PlayerAnimator clip used while deploying the hunter turret. */
public final class HunterSpearPlayerAnimation {
	private static final int LAYER_PRIORITY = 27;
	private static final Identifier CLIP =
			TkGun.id("hunter_spear_deploy");
	private static final Map<UUID, ModifierLayer<IAnimation>> LAYERS =
			new HashMap<>();
	private static final Set<UUID> ACTIVE = new HashSet<>();

	public static void register() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			LAYERS.put(player.getUuid(), layer);
			ACTIVE.remove(player.getUuid());
		});
	}

	public static void play(AbstractClientPlayerEntity player) {
		UUID uuid = player.getUuid();
		if (ACTIVE.contains(uuid)) {
			return;
		}
		ModifierLayer<IAnimation> layer = layerFor(player);
		IPlayable playable = PlayerAnimationRegistry.getAnimation(CLIP);
		if (layer == null || playable == null) {
			TkGun.LOGGER.warn(
					"[mechaniciancombat] Missing hunter spear PlayerAnimator clip {}", CLIP);
			return;
		}
		PlayerAnimationTransitions.play(layer,
				new HoldAnimation(playable.playAnimation()),
				PlayerAnimationTransitions.NORMAL_TICKS);
		ACTIVE.add(uuid);
	}

	public static void clear(UUID uuid) {
		ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
		if (ACTIVE.remove(uuid)) {
			PlayerAnimationTransitions.clear(
					layer, PlayerAnimationTransitions.NORMAL_TICKS);
		}
	}

	public static void clearAll() {
		for (ModifierLayer<IAnimation> layer : LAYERS.values()) {
			layer.setAnimation(null);
		}
		LAYERS.clear();
		ACTIVE.clear();
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

	private static final class HoldAnimation implements IAnimation {
		private final IActualAnimation<?> animation;
		private boolean running = true;

		private HoldAnimation(IActualAnimation<?> animation) {
			this.animation = animation;
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
			if ("head".equals(part)) {
				// Head orientation remains vanilla. Position can follow a future
				// authored chest attachment or the lower normal-pose layer.
				return type == TransformType.POSITION
						? animation.get3DTransform(
								part, type, tickDelta, fallback)
						: fallback;
			}
			if (!"torso".equals(part) && !"rightArm".equals(part)
					&& !"leftArm".equals(part)) {
				return fallback;
			}
			return animation.get3DTransform(part, type, tickDelta, fallback);
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

	private HunterSpearPlayerAnimation() {
	}
}
