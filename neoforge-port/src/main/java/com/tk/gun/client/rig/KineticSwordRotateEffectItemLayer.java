package com.tk.gun.client.rig;

import com.tk.gun.item.KineticSwordFpaItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** First-person full-bright rendering for the Kinetic Sword rotate effect. */
public final class KineticSwordRotateEffectItemLayer
		extends GeoRenderLayer<KineticSwordFpaItem> {
	private static final double U_SCALE = 64.0D / 144.0D;
	private static final double V_SCALE = 64.0D / 96.0D;

	public KineticSwordRotateEffectItemLayer(
			GeoItemRenderer<KineticSwordFpaItem> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack,
			KineticSwordFpaItem animatable, GeoBone bone, RenderLayer type,
			VertexConsumerProvider bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null || bone.getCubes().isEmpty()
				|| bone.getName() == null
				|| !bone.getName().startsWith("effect_step_")
				|| !visible(bone)) {
			return;
		}
		VertexConsumer effect = bufferSource.getBuffer(
				RenderLayer.getEntityTranslucentEmissive(
						KineticSwordRotateEffectLayer.TEXTURE, false));
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			BowgunCloseCombatEffectLayer.renderUnshadedCube(
					poseStack, rescaleUv(cube), effect, packedOverlay);
			poseStack.pop();
		}
	}

	private static boolean visible(GeoBone bone) {
		return Math.abs(bone.getScaleX()) > 1.0E-4F
				&& Math.abs(bone.getScaleY()) > 1.0E-4F
				&& Math.abs(bone.getScaleZ()) > 1.0E-4F;
	}

	private static GeoCube rescaleUv(GeoCube cube) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] scaled = new GeoQuad[source.length];
		for (int index = 0; index < source.length; index++) {
			GeoQuad quad = source[index];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			GeoVertex[] updated = new GeoVertex[vertices.length];
			for (int vertex = 0; vertex < vertices.length; vertex++) {
				updated[vertex] = vertices[vertex].withUVs(
						(float) (vertices[vertex].texU() * U_SCALE),
						(float) (vertices[vertex].texV() * V_SCALE));
			}
			scaled[index] = new GeoQuad(updated, quad.normal(),
					quad.direction());
		}
		return new GeoCube(scaled, cube.pivot(), cube.rotation(),
				cube.size(), cube.inflate(), cube.mirror());
	}
}
