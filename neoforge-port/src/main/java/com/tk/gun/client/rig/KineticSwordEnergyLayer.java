package com.tk.gun.client.rig;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Third-person additive/full-bright rendering for the Kinetic Sword energy frames. */
public final class KineticSwordEnergyLayer
		extends GeoRenderLayer<BowgunRig> {
	public KineticSwordEnergyLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable,
			GeoBone bone, RenderLayer type,
			VertexConsumerProvider bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		if (!KineticSwordEnergyEffect.isEnergyBone(bone.getName())
				|| bone.getCubes().isEmpty()
				|| !(renderer instanceof BowgunRigRenderer rig)) {
			return;
		}
		Entity entity = rig.getCurrentEntity();
		int colour = KineticSwordEnergyEffect.colorFor(entity,
				animatable.currentAnimation(), partialTick);
		if ((colour >>> 24) == 0) {
			return;
		}
		int frame = KineticSwordEnergyEffect.frameFor(entity);
		VertexConsumer energy = bufferSource.getBuffer(
				RenderLayer.getEntityTranslucentEmissive(
						KineticSwordEnergyEffect.TEXTURE, false));
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			renderer.renderCube(poseStack,
					KineticSwordEnergyEffect.remapUv(cube, frame),
					energy, LightmapTextureManager.MAX_LIGHT_COORDINATE,
					packedOverlay, colour);
			poseStack.pop();
		}
	}
}
