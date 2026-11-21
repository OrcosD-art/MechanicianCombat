package com.tk.gun.client.rig;

import com.tk.gun.item.KineticSwordFpaItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** First-person additive/full-bright rendering for the Kinetic Sword energy frames. */
public final class KineticSwordEnergyItemLayer
		extends GeoRenderLayer<KineticSwordFpaItem> {
	public KineticSwordEnergyItemLayer(
			GeoItemRenderer<KineticSwordFpaItem> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack,
			KineticSwordFpaItem animatable, GeoBone bone, RenderLayer type,
			VertexConsumerProvider bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (!KineticSwordEnergyEffect.isEnergyBone(bone.getName())
				|| bone.getCubes().isEmpty() || player == null) {
			return;
		}
		int colour = KineticSwordEnergyEffect.colorFor(player);
		if ((colour >>> 24) == 0) {
			return;
		}
		int frame = KineticSwordEnergyEffect.frameFor(player);
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
