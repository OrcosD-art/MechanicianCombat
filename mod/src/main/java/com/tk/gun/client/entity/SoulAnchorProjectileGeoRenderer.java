package com.tk.gun.client.entity;

import com.tk.gun.entity.SoulAnchorEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Arrow-like world-space renderer for the soul-anchor projectile. */
public final class SoulAnchorProjectileGeoRenderer
		extends GeoEntityRenderer<SoulAnchorEntity> {
	public SoulAnchorProjectileGeoRenderer(
			EntityRendererFactory.Context context) {
		super(context, new SoulAnchorProjectileGeoModel());
	}

	@Override
	public void render(SoulAnchorEntity entity, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, consumers,
				LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}

	@Override
	protected void applyRotations(SoulAnchorEntity entity,
			MatrixStack matrices, float ageInTicks, float rotationYaw,
			float partialTick, float scale) {
		Vec3d velocity = entity.getVelocity();
		Vec3d direction = entity.anchorState()
				== SoulAnchorEntity.State.FLYING
				&& velocity.lengthSquared() > 1.0E-6D
				? velocity.normalize() : entity.flightDirection();
		matrices.multiply(new Quaternionf().rotationTo(
				new Vector3f(0.0F, 1.0F, 0.0F),
				new Vector3f((float) direction.x, (float) direction.y,
						(float) direction.z)));
	}
}
