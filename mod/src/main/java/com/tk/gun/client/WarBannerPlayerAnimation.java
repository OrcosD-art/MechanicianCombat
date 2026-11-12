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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One-shot upper-body animation for deploying the War Banner. */
public final class WarBannerPlayerAnimation {
	private static final int LAYER_PRIORITY = 26;
	private static final Identifier CLIP = TkGun.id("flag_skill");
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

	public static void play(AbstractClientPlayerEntity player, boolean gunHeld) {
		UUID uuid = player.getUuid();
		if (gunHeld) {
			// The gun rig consumes skill_flag_gun through GeckoLib and bridges
			// its body pose back to PlayerAnimator. A second upper-body clip
			// would overwrite the authored arms, head and legs.
			clear(uuid);
			return;
		}
		if (ACTIVE.contains(uuid)) {
			return;
		}
		ModifierLayer<IAnimation> layer = layerFor(player);
		IPlayable playable = PlayerAnimationRegistry.getAnimation(CLIP);
		if (layer == null || playable == null) {
			TkGun.LOGGER.warn("[mechaniciancombat] Missing War Banner PlayerAnimator clip {}", CLIP);
			return;
		}
		layer.setAnimation(new HoldAnimation(playable.playAnimation()));
		ACTIVE.add(uuid);
	}

	public static void clear(UUID uuid) {
		ModifierLayer<IAnimation> layer = LAYERS.get(uuid);
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
			// skill_flag only authors the upper body. Keep every other player
			// channel on the lower animation layer's pose.
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

	private WarBannerPlayerAnimation() {
	}
}
