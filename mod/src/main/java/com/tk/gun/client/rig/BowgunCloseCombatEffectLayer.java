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
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.Set;

/**
 * Emissive planes authored in Veritas close_combat.bbmodel.
 *
 * <p>The animation uses step-held scale keys to switch each plane on and off.
 * A group whose only scale key is zero stays hidden for the whole clip. The
 * layer also gates rendering to clips that actually contain close-combat
 * effects, preventing an unanimated group from falling back to scale 1.</p>
 */
public final class BowgunCloseCombatEffectLayer
		extends GeoRenderLayer<BowgunRig> {
	private static final Set<String> EFFECT_BONES = Set.of(
			"effect_1", "1_1", "1_2", "1_3", "1_4",
			"effect_2", "2_1", "2_2", "2_3", "2_4");
	private static final Set<String> EFFECT_ANIMATIONS = Set.of(
			"ATK_1", "ATK_1_upper", "ATK_2", "ATK_4", "ATK_hold_over");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/close_combat_effect-sheet.png");
	private static final Identifier TEXTURE_2 =
			TkGun.id("textures/entity/close_combat_effect2.png");
	private static final Map<String, Identifier> BONE_TEXTURES = Map.of(
			"1_1", TEXTURE,
			"1_2", TEXTURE,
			"1_3", TEXTURE,
			"1_4", TEXTURE,
			"2_1", TEXTURE_2,
			"2_2", TEXTURE_2,
			"2_3", TEXTURE_2,
			"2_4", TEXTURE_2);
	private static final Map<String, Float> EFFECT_START_SECONDS = Map.ofEntries(
			// ATK1 wind-up: the earliest authored effect starts at 0.1667 s.
			// This gate also prevents GeckoLib's bind-scale fallback from
			// exposing a plane during the first transition frame.
			Map.entry("ATK_1/1_1", 0.25F),
			Map.entry("ATK_1/1_2", 0.29167F),
			Map.entry("ATK_1/1_3", 0.33333F),
			Map.entry("ATK_1/1_4", 0.375F),
			Map.entry("ATK_1_upper/1_1", 0.25F),
			Map.entry("ATK_1_upper/1_2", 0.29167F),
			Map.entry("ATK_1_upper/1_3", 0.33333F),
			Map.entry("ATK_1_upper/1_4", 0.375F),
			// The other melee clips use the same defensive timing guard.
			Map.entry("ATK_2/1_1", 0.41667F),
			Map.entry("ATK_2/1_2", 0.45833F),
			Map.entry("ATK_2/1_3", 0.5F),
			Map.entry("ATK_2/1_4", 0.54167F),
			Map.entry("ATK_2/2_1", 0.375F),
			Map.entry("ATK_2/2_2", 0.41667F),
			Map.entry("ATK_2/2_3", 0.5F),
			Map.entry("ATK_2/2_4", 0.58333F),
			Map.entry("ATK_4/1_1", 0.66667F),
			Map.entry("ATK_4/1_2", 0.70833F),
			Map.entry("ATK_4/1_3", 0.75F),
			Map.entry("ATK_4/1_4", 0.79167F),
			Map.entry("ATK_4/2_1", 0.375F),
			Map.entry("ATK_4/2_2", 0.41667F),
			Map.entry("ATK_4/2_3", 0.5F),
			Map.entry("ATK_4/2_4", 0.58333F),
			Map.entry("ATK_hold_over/1_1", 0.70833F),
			Map.entry("ATK_hold_over/1_2", 0.75F),
			Map.entry("ATK_hold_over/1_3", 0.83333F),
			Map.entry("ATK_hold_over/1_4", 0.91667F),
			Map.entry("ATK_hold_over/2_1", 0.25F),
			Map.entry("ATK_hold_over/2_2", 0.29167F),
			Map.entry("ATK_hold_over/2_3", 0.375F),
			Map.entry("ATK_hold_over/2_4", 0.45833F));
	private static final Map<String, EffectWindow> EFFECT_WINDOWS =
			Map.ofEntries(
					entry("ATK_1", "1_1", 0.25F, 0.29167F),
					entry("ATK_1", "1_2", 0.29167F, 0.33333F),
					entry("ATK_1", "1_3", 0.33333F, 0.375F),
					entry("ATK_1", "1_4", 0.375F, 0.41667F),
					entry("ATK_2", "2_1", 0.375F, 0.41667F),
					entry("ATK_2", "2_2", 0.41667F, 0.45833F),
					entry("ATK_2", "2_3", 0.5F, 0.54167F),
					entry("ATK_2", "2_4", 0.58333F, 0.625F),
					entry("ATK_2", "1_1", 0.41667F, 0.45833F),
					entry("ATK_2", "1_2", 0.45833F, 0.5F),
					entry("ATK_2", "1_3", 0.5F, 0.54167F),
					entry("ATK_2", "1_4", 0.54167F, 0.58333F),
					entry("ATK_4", "1_1", 0.66667F, 0.70833F),
					entry("ATK_4", "1_2", 0.70833F, 0.75F),
					entry("ATK_4", "1_3", 0.75F, 0.79167F),
					entry("ATK_4", "1_4", 0.79167F, 0.83333F),
					entry("ATK_4", "2_1", 0.375F, 0.41667F),
					entry("ATK_4", "2_2", 0.41667F, 0.45833F),
					entry("ATK_4", "2_3", 0.5F, 0.54167F),
					entry("ATK_4", "2_4", 0.58333F, 0.625F),
					entry("ATK_hold_over", "2_1", 0.25F, 0.29167F),
					entry("ATK_hold_over", "2_2", 0.29167F, 0.33333F),
					entry("ATK_hold_over", "2_3", 0.375F, 0.41667F),
					entry("ATK_hold_over", "2_4", 0.45833F, 0.5F),
					entry("ATK_hold_over", "1_1", 0.70833F, 0.75F),
					entry("ATK_hold_over", "1_2", 0.75F, 0.79167F),
					entry("ATK_hold_over", "1_3", 0.83333F, 0.875F),
					entry("ATK_hold_over", "1_4", 0.91667F, 0.95833F));
	private static final float VISIBLE_EPSILON = 1.0E-4F;

	public BowgunCloseCombatEffectLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	public static boolean isEffectBone(String name) {
		return EFFECT_BONES.contains(name);
	}

	/**
	 * Applies the complete effect schedule from the authoritative client
	 * melee clock. Hitstop pauses that clock, so the same step window resumes
	 * instead of being skipped or cut by a controller reset.
	 */
	public static void applyScheduledVisibility(GeoBone bone, Entity entity,
			float partialTick) {
		if (entity == null || !isEffectBone(bone.getName())) {
			return;
		}
		GunClientState.Entry entry = GunClientState.get(entity.getId());
		if (entry == null || entry.state != com.tk.gun.TkGun.State.MELEE
				|| entry.meleeReturning) {
			hide(bone);
			return;
		}
		String animation = normalizedEffectAnimation();
		double seconds = (entry.elapsed + partialTick) / 20.0D;
		String boneName = bone.getName();
		if ("effect_1".equals(boneName) || "effect_2".equals(boneName)) {
			String prefix = "effect_1".equals(boneName) ? "1_" : "2_";
			boolean visible = false;
			for (Map.Entry<String, EffectWindow> window
					: EFFECT_WINDOWS.entrySet()) {
				if (!window.getKey().startsWith(animation + "/" + prefix)) {
					continue;
				}
				EffectWindow value = window.getValue();
				if (seconds + 1.0E-4D >= value.start()
						&& seconds < value.end() - 1.0E-4D) {
					visible = true;
					break;
				}
			}
			setScale(bone, visible ? 1.0F : 0.0F);
			return;
		}
		EffectWindow window = EFFECT_WINDOWS.get(animation + "/" + boneName);
		boolean visible = window != null
				&& seconds + 1.0E-4D >= window.start()
				&& seconds < window.end() - 1.0E-4D;
		setScale(bone, visible ? 1.0F : 0.0F);
	}

	private static Map.Entry<String, EffectWindow> entry(
			String animation, String bone, float start, float end) {
		return Map.entry(animation + "/" + bone,
				new EffectWindow(start, end));
	}

	private static String normalizedEffectAnimation() {
		String active = activeEffectAnimation();
		if (active != null && active.endsWith("_upper")) {
			return active.substring(0,
					active.length() - "_upper".length());
		}
		return active == null ? "" : active;
	}

	private static void setScale(GeoBone bone, float value) {
		bone.setScaleX(value);
		bone.setScaleY(value);
		bone.setScaleZ(value);
	}

	private static void hide(GeoBone bone) {
		setScale(bone, 0.0F);
	}

	private record EffectWindow(float start, float end) {
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable,
			GeoBone bone, RenderLayer type, VertexConsumerProvider bufferSource,
			VertexConsumer buffer, float partialTick, int packedLight,
			int packedOverlay) {
		Identifier texture = BONE_TEXTURES.get(bone.getName());
		if (texture == null || bone.getCubes().isEmpty()
				|| !isEffectAnimationActive()
				|| !isAfterAuthoredStart(bone.getName(), partialTick)
				|| !isScaleVisible(bone)) {
			return;
		}
		RenderLayer effectLayer =
				RenderLayer.getEntityTranslucentEmissive(texture, false);
		VertexConsumer effectBuffer = bufferSource.getBuffer(effectLayer);
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			renderUnshadedCube(poseStack, cube, effectBuffer, packedOverlay);
			poseStack.pop();
		}
	}

	/**
	 * The vanilla emissive shader still multiplies by normal lighting. Authored
	 * hold-over planes rotate their up face toward the ground, so use a fixed
	 * view-space normal and maximum vertex colour to keep both sides equally
	 * bright.
	 */
	private static void renderUnshadedCube(MatrixStack poseStack, GeoCube cube,
			VertexConsumer buffer, int packedOverlay) {
		Matrix4f matrix = poseStack.peek().getPositionMatrix();
		for (GeoQuad quad : cube.quads()) {
			if (quad == null) {
				continue;
			}
			emitQuad(matrix, quad, buffer, packedOverlay, false);
			// Entity render layers enable back-face culling. Emit the reversed
			// winding as well so a plane remains visible after effect_1/effect_2
			// rotate it away from the camera. The culled side produces no
			// duplicate fragments at any viewing angle.
			emitQuad(matrix, quad, buffer, packedOverlay, true);
		}
	}

	private static void emitQuad(Matrix4f matrix, GeoQuad quad,
			VertexConsumer buffer, int packedOverlay, boolean reverse) {
		GeoVertex[] vertices = quad.vertices();
		for (int index = 0; index < vertices.length; index++) {
			GeoVertex vertex = vertices[reverse
					? vertices.length - 1 - index : index];
			Vector4f position = matrix.transform(
					new Vector4f(vertex.position(), 1.0F));
			buffer.vertex(position.x, position.y, position.z)
					.color(255, 255, 255, 255)
					.texture(vertex.texU(), vertex.texV())
					.overlay(packedOverlay)
					.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
					.normal(0.0F, 1.0F, 0.0F);
		}
	}

	private static boolean isScaleVisible(GeoBone bone) {
		return Math.abs(bone.getScaleX()) > VISIBLE_EPSILON
				&& Math.abs(bone.getScaleY()) > VISIBLE_EPSILON
				&& Math.abs(bone.getScaleZ()) > VISIBLE_EPSILON;
	}

	private static boolean isEffectAnimationActive() {
		String upper = BowgunRig.currentUpperAnimation();
		if (upper != null && EFFECT_ANIMATIONS.contains(upper)) {
			return true;
		}
		return EFFECT_ANIMATIONS.contains(BowgunRig.currentAnimation());
	}

	private boolean isAfterAuthoredStart(String boneName,
			float partialTick) {
		String animation = activeEffectAnimation();
		Float start = EFFECT_START_SECONDS.get(animation + "/" + boneName);
		if (start == null || !(this.renderer instanceof BowgunRigRenderer rig)) {
			return true;
		}
		Entity entity = rig.getCurrentEntity();
		GunClientState.Entry entry = entity == null
				? null : GunClientState.get(entity.getId());
		if (entry == null) {
			return true;
		}
		double elapsedSeconds = (entry.elapsed + partialTick) / 20.0D;
		return elapsedSeconds + 1.0E-4D >= start;
	}

	private static String activeEffectAnimation() {
		String upper = BowgunRig.currentUpperAnimation();
		if (upper != null && EFFECT_ANIMATIONS.contains(upper)) {
			return upper;
		}
		return BowgunRig.currentAnimation();
	}
}
