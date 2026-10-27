package com.tk.gun.client.entity;

import com.tk.gun.client.MoltenGolemGeoModel;
import com.tk.gun.entity.MoltenGolemEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MoltenGolemEntityRenderer
		extends GeoEntityRenderer<MoltenGolemEntity> {
	public MoltenGolemEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new MoltenGolemGeoModel());
	}

	@Override
	protected float getShadowRadius(MoltenGolemEntity entity) {
		return 1.4F;
	}

	@Override
	public void renderRecursively(MatrixStack matrices, MoltenGolemEntity animatable,
								  GeoBone bone, RenderLayer renderType,
								  VertexConsumerProvider consumers, VertexConsumer buffer,
								  boolean isReRender, float tickDelta, int packedLight,
								  int packedOverlay, int colour) {
		if (!isReRender && "locator_right".equals(bone.getName())) {
			Vector3d position = bone.getWorldPosition();
			Vec3d locator = new Vec3d(position.x, position.y, position.z);
			MoltenGolemEntity.recordRenderedLocator(animatable.getId(), locator,
					animatable.getWorld().getTime());
			animatable.emitClientAttackLocatorEffects(locator);
		}
		super.renderRecursively(matrices, animatable, bone, renderType, consumers,
				buffer, isReRender, tickDelta, packedLight, packedOverlay, colour);
	}
}
