package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.SoulAnchorSkillClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runs the hidden normal locomotion rig without replacing the vanilla body. */
public final class PlayerLocomotionRigClient {
	private static EntityRendererFactory.Context context;
	private static final Map<UUID, RigBundle> RIGS = new HashMap<>();

	private record RigBundle(AbstractClientPlayerEntity player,
			PlayerLocomotionRig animatable,
			PlayerLocomotionRigRenderer renderer) {
	}

	public static void setContext(EntityRendererFactory.Context value) {
		context = value;
	}

	public static void renderIfActive(AbstractClientPlayerEntity player,
			float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		if (!shouldEvaluate(player)) {
			return;
		}
		RigBundle bundle = RIGS.get(player.getUuid());
		if (bundle == null || bundle.player() != player) {
			PlayerLocomotionRig animatable = new PlayerLocomotionRig(player);
			bundle = new RigBundle(player, animatable,
					new PlayerLocomotionRigRenderer(context, animatable));
			RIGS.put(player.getUuid(), bundle);
		}
		EntityRenderer<AbstractClientPlayerEntity> renderer = bundle.renderer();
		renderer.render(player, yaw, tickDelta, matrices, consumers, light);
	}

	public static boolean shouldEvaluate(AbstractClientPlayerEntity player) {
		return context != null && player != null && player.isAlive()
				&& !player.isSpectator() && !player.isSneaking()
				&& !player.hasVehicle() && !player.isFallFlying()
				&& !player.isTouchingWater() && !player.isSwimming()
				&& !player.isInLava() && !player.isClimbing()
				&& !player.isUsingRiptide() && !player.isSleeping()
				&& !player.getAbilities().flying
				&& !player.hasStatusEffect(StatusEffects.LEVITATION)
				&& !TkGun.holdingGun(player)
				&& !SoulAnchorSkillClientState.active(player.getId());
	}

	public static boolean hasActivePose(
			AbstractClientPlayerEntity player) {
		return player != null && !TkGun.holdingGun(player)
				&& !SoulAnchorSkillClientState.active(player.getId())
				&& PlayerLocomotionPoseBridge.hasFreshPose(player.getId());
	}

	public static void clear() {
		RIGS.clear();
	}

	private PlayerLocomotionRigClient() {
	}
}
