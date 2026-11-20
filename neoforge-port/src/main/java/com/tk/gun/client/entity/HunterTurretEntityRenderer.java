package com.tk.gun.client.entity;

import com.tk.gun.client.HunterTurretGeoModel;
import com.tk.gun.entity.HunterTurretEntity;
import com.tk.gun.TkGunParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rotates the authored turret shoulder and forearm toward its server target. */
public final class HunterTurretEntityRenderer
		extends GeoEntityRenderer<HunterTurretEntity> {
	private final Map<UUID, Integer> lastMuzzleSequence = new HashMap<>();

	public HunterTurretEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new HunterTurretGeoModel());
	}

	@Override
	public void renderRecursively(MatrixStack matrices,
			HunterTurretEntity animatable, GeoBone bone,
			RenderLayer renderType, VertexConsumerProvider consumers,
			VertexConsumer buffer, boolean isReRender, float tickDelta,
			int packedLight, int packedOverlay, int colour) {
		boolean firingEffect = "F1".equals(bone.getName())
				|| "F2".equals(bone.getName())
				|| "F3".equals(bone.getName());
		spawnMuzzleGlowAtLocator(matrices, animatable, bone);
		if (firingEffect) {
			// Penetration feedback is rendered at the hit entity by
			// SteelArrowImpactEntity. The turret must not flash its old F groups.
			bone.setScaleX(1.0E-4F);
			bone.setScaleY(1.0E-4F);
			bone.setScaleZ(1.0E-4F);
		}
		if (!animatable.isSummoning()
				&& "shoulder".equals(bone.getName())) {
			float base = bone.getInitialSnapshot() == null
					? 0.0F : bone.getInitialSnapshot().getRotY();
			bone.setRotY(base
					- (float) Math.toRadians(animatable.shoulderYaw()));
		} else if (!animatable.isSummoning()
				&& "forearm".equals(bone.getName())) {
			float base = bone.getInitialSnapshot() == null
					? 0.0F : bone.getInitialSnapshot().getRotX();
			bone.setRotX(base
					+ (float) Math.toRadians(animatable.forearmPitch()));
		}
		super.renderRecursively(matrices, animatable, bone, renderType,
				consumers, buffer, isReRender, tickDelta, packedLight,
				packedOverlay, colour);
	}

	private void spawnMuzzleGlowAtLocator(MatrixStack matrices,
			HunterTurretEntity entity, GeoBone bone) {
		if (!"gunmode".equals(bone.getName())) {
			return;
		}
		int sequence = entity.fireSequence();
		UUID uuid = entity.getUuid();
		Integer previous = lastMuzzleSequence.get(uuid);
		if (previous == null) {
			lastMuzzleSequence.put(uuid, sequence);
			return;
		}
		if (sequence <= previous || entity.isSummoning()) {
			return;
		}
		int fireMode = entity.fireMode();
		if (fireMode != 2 && fireMode != 3) {
			return;
		}
		double localY = (fireMode == 2 ? 17.5D : 14.5D) / 16.0D;
		double localZ = -14.5D / 16.0D;
		matrices.push();
		Vec3d position;
		try {
			RenderUtil.prepMatrixForBone(matrices, bone);
			Vector4f rendered = matrices.peek().getPositionMatrix()
					.transform(new Vector4f(0.0F,
							(float) localY, (float) localZ, 1.0F));
			Vec3d camera = MinecraftClient.getInstance()
					.gameRenderer.getCamera().getPos();
			position = new Vec3d(camera.x + rendered.x,
					camera.y + rendered.y, camera.z + rendered.z);
		} finally {
			matrices.pop();
		}
		lastMuzzleSequence.put(uuid, sequence);
		entity.getWorld().addParticle(TkGunParticles.MUZZLE_GLOW,
				position.x, position.y, position.z,
				0.0D, 0.0D, 0.0D);
	}

	@Override
	public void renderCubesOfBone(MatrixStack matrices, GeoBone bone,
			VertexConsumer buffer, int packedLight, int packedOverlay,
			int colour) {
		HunterTurretEntity animatable = getAnimatable();
		boolean firingEffect = "F1".equals(bone.getName())
				|| "F2".equals(bone.getName())
				|| "F3".equals(bone.getName());
		if (firingEffect) {
			return;
		}
		super.renderCubesOfBone(
				matrices, bone, buffer, packedLight, packedOverlay, colour);
	}

	@Override
	protected float getShadowRadius(HunterTurretEntity entity) {
		return 0.8F;
	}
}
