package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Third-person full-bright rendering for the Kinetic Sword rotate effect. */
public final class KineticSwordRotateEffectLayer
		extends GeoRenderLayer<BowgunRig> {
	public static final Identifier TEXTURE =
			TkGun.id("textures/entity/kinetic_sword_rotate_effect.png");
	private static final float U_SCALE = 64.0F / 144.0F;
	private static final float V_SCALE = 64.0F / 96.0F;

	public KineticSwordRotateEffectLayer(
			GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable,
			GeoBone bone, RenderLayer type,
			VertexConsumerProvider bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		if (!isStepBone(bone.getName()) || bone.getCubes().isEmpty()
				|| !visible(bone)
				|| !(renderer instanceof BowgunRigRenderer rig)) {
			return;
		}
		Entity entity = rig.getCurrentEntity();
		if (entity == null) {
			return;
		}
		VertexConsumer effect = bufferSource.getBuffer(
				RenderLayer.getEntityTranslucentEmissive(TEXTURE, false));
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			BowgunCloseCombatEffectLayer.renderUnshadedCube(
					poseStack, rescaleUv(cube), effect, packedOverlay);
			poseStack.pop();
		}
	}

	private static boolean isStepBone(String name) {
		return name != null && name.startsWith("effect_step_");
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
						vertices[vertex].texU() * U_SCALE,
						vertices[vertex].texV() * V_SCALE);
			}
			scaled[index] = new GeoQuad(updated, quad.normal(),
					quad.direction());
		}
		return new GeoCube(scaled, cube.pivot(), cube.rotation(),
				cube.size(), cube.inflate(), cube.mirror());
	}
}
