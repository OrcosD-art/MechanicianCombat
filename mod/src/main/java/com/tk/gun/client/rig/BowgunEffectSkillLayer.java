package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunClientState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renders fortress skill effects from their own texture atlas.
 *
 * <p>The rig geometry uses a 64x64 gun texture, so GeckoLib has already
 * divided the authored UVs by 64. The skill cubes reference the embedded
 * 160x160 {@code effects.png} atlas instead. Scaling those UVs by 64/160
 * restores the exact coordinates authored in the bbmodel, including the
 * narrow beam faces and the frames at x=96..128 and x=128..160.</p>
 */
public final class BowgunEffectSkillLayer extends GeoRenderLayer<BowgunRig> {
	private static final Identifier TEXTURE =
			Identifier.of("tkgun", "textures/entity/bowgun_effects.png");
	private static final float SOURCE_ATLAS_SCALE = 64.0F / 160.0F;

	public BowgunEffectSkillLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	public static boolean isSkillEffectBone(String name) {
		return "step_1".equals(name) || "step_2".equals(name)
				|| "step_3".equals(name) || "step_4".equals(name);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable, GeoBone bone,
							  RenderLayer type, VertexConsumerProvider bufferSource,
							  VertexConsumer buffer, float partialTick, int packedLight,
							  int packedOverlay) {
		if (!isSkillEffectBone(bone.getName()) || bone.getCubes().isEmpty()
				|| !isHitStateActive()) {
			return;
		}

		RenderLayer effectLayer =
				RenderLayer.getEntityTranslucentEmissive(TEXTURE, false);
		VertexConsumer effectBuffer = bufferSource.getBuffer(effectLayer);
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			this.renderer.renderCube(poseStack, rescaleUv(cube), effectBuffer,
					LightmapTextureManager.MAX_LIGHT_COORDINATE, packedOverlay,
					0xFFFFFFFF);
			poseStack.pop();
		}
	}

	private boolean isHitStateActive() {
		if (!(this.renderer instanceof BowgunRigRenderer rig)) {
			return false;
		}
		Entity entity = rig.getCurrentEntity();
		GunClientState.Entry entry =
				entity == null ? null : GunClientState.get(entity.getId());
		return entry != null && entry.active()
				&& entry.state == TkGun.State.FORTRESS_SKILL_HIT;
	}

	private static GeoCube rescaleUv(GeoCube cube) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] scaled = new GeoQuad[source.length];
		boolean flatPlane = Math.abs(cube.size().z) < 1.0E-4;
		for (int i = 0; i < source.length; i++) {
			GeoQuad quad = source[i];
			if (quad == null) {
				continue;
			}
			// Faces without an assigned texture were exported with placeholder
			// UVs. Only the authored south face is valid on a flat skill plane.
			if (flatPlane && quad.direction() != Direction.SOUTH) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			GeoVertex[] rescaled = new GeoVertex[vertices.length];
			for (int j = 0; j < vertices.length; j++) {
				GeoVertex vertex = vertices[j];
				rescaled[j] = vertex.withUVs(
						vertex.texU() * SOURCE_ATLAS_SCALE,
						vertex.texV() * SOURCE_ATLAS_SCALE);
			}
			scaled[i] = new GeoQuad(rescaled, quad.normal(), quad.direction());
		}
		return new GeoCube(scaled, cube.pivot(), cube.rotation(), cube.size(),
				cube.inflate(), cube.mirror());
	}
}
