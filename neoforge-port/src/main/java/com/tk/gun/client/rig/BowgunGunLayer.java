package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.api.FortressDestroyerGun;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;

/**
 * 枪那一份：同一份几何里的**枪件骨骼**，换作者的枪贴图补画一遍。
 *
 * <p>做法：主渲染在 {@link BowgunRigRenderer#renderCubesOfBone} 里跳过非身体骨骼（免得拿玩家皮肤画枪），
 * 这里在 {@link #renderForBone} 里用枪贴图把它们补画。
 * 用的是**主模型那根骨骼此刻被动画摆好的姿态**（同一个 poseStack），所以枪真的跟着
 * 手臂 / 后坐 / 拉栓 / 抛壳 / 翻滚一起动；{@code renderCube} 不检查 hidden，所以藏了也能画。
 *
 * <p>⚠️ 别用 {@code renderer.reRender(第二套 BakedGeoModel, ...)}：那是另一份骨骼实例，
 * 动画摆的是主模型那份，第二份永远停在静止姿势（表现就是"枪不跟手"）。2026-10-12 踩过。
 *
 * <p>⚠️ 也**别再加"按状态显隐特效"的门禁**（2026-10-12 删掉的 effectVisible）：作者是靠
 * 动画里的 **scale 关键帧**做显隐的 —— 父骨 {@code fire} 在待机/跑/滑铲里是 (1,0,0)（Y 压成 0 = 收起），
 * Focus_shooting 里父骨没有通道（GeckoLib 会把没有通道的骨骼重置回 geo 的初始 scale=1）
 * 而 fire1..6 各自在 0.04s~0.29s 逐帧翻成 (1,1,1) = 连环枪口焰；速度线同理（父骨 bone2）。
 * GeckoLib 4.8 的 AnimationProcessor 是**读 scale** 的（scaleX/Y/ZQueue → setScaleX/Y/Z），
 * 之前那句"GeckoLib 不读 scale"是错的，加门禁反而把作者做好的特效盖掉了。
 */
public class BowgunGunLayer extends GeoRenderLayer<BowgunRig> {
	/** Fortress shell box UVs in its 64x64 gun texture. */
	private static final Map<Direction, float[]> FORTRESS_SHELL_UVS = Map.of(
			Direction.NORTH, new float[]{63, 45, 64, 44},
			Direction.EAST, new float[]{59, 45, 64, 44},
			Direction.SOUTH, new float[]{59, 45, 60, 44},
			Direction.WEST, new float[]{64, 45, 59, 44},
			Direction.UP, new float[]{59, 45, 64, 44},
			Direction.DOWN, new float[]{64, 45, 59, 44});
	private static final float FORTRESS_TEXTURE_SIZE = 64.0F;

	public BowgunGunLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable, GeoBone bone, RenderLayer type,
							  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
							  int packedLight, int packedOverlay) {
		if (!TkGun.holdingGun(animatable.player())) {
			return;
		}
		// 待机/收起时 fire 父骨的 scaleY = 0（特效平面被压平）；一旦它变成 1，说明**没有动画在跑**
		// （骨骼被重置回 geo 初始值）—— 也就是用户看到的"枪卡在原位、粒子特效全摊出来"。
		//
		// ⚠️ 2026-10-13：原来"只要数值变了就打一行"太吵（开火时 scale 会 0.78→0.72→…→0 一路过渡，
		// 一发十几行）。现在只在**跨过两个关键值**时打：≈1（整模型被重置的坏状态）和 ≈0（正常收起）。
		if ("fire".equals(bone.getName())) {
			float sy = bone.getScaleY();
			int state = Math.abs(sy - 1.0F) < 0.02F ? 1 : (Math.abs(sy) < 0.02F ? 0 : -1);
			if (state >= 0 && state != lastFireScaleState) {
				lastFireScaleState = state;
				lastFireScaleY = sy;
				TkGun.LOGGER.info("[mechaniciancombat] fire 平面 scaleY={} （0=收起正常，1=整模型被重置的最初状态）", sy);
			}
		}
		// ⚠️ 枪口的量测**不在这里**：GeoRenderLayer#renderForBone 对"没有方块的骨骼"（locator）
		//    根本不会触发（2026-10-14 实测：一次都没跑，服务端拿到 `客户端量到枪口=false`）。
		//    现在放在 BowgunRigRenderer#renderRecursively 里（骨骼遍历本身，必定经过每根骨骼）。
		// 气流平面（`airflow1/2/3`，用第 2 张贴图）交给 BowgunAirflowLayer 单独画 ——
		// 拿枪贴图硬画它的话 UV 越界，会糊出一块乱七八糟的东西。
		if (BowgunRigRenderer.BODY_BONES.contains(bone.getName()) || bone.getCubes().isEmpty()
				|| BowgunAirflowLayer.isAirflowBone(bone.getName())
				|| BowgunEffectSkillLayer.isSkillEffectBone(bone.getName())
				|| KineticSwordEnergyEffect.isEnergyBone(bone.getName())
				|| KineticSwordRotateEffect.isEffectBone(bone.getName())
				|| BowgunCloseCombatEffectLayer.isEffectBone(bone.getName())) {
			return;
		}
		// r853 fire: additive eyes layer, with emissive/full-light sampling.
		// change5 and the abyssal case keep their non-additive emissive branch.
		boolean muzzleFire = bone.getName().startsWith("fire");
		boolean glowPart = muzzleFire
				|| bone.getName().startsWith("change5");
		// 枪换一张贴图 → 换一个 buffer；下一个骨骼 GeckoLib 会自己把缓冲刷回来
		// （GeoRenderer.checkAndRefreshBuffer 每根骨骼都会重新取）。
		boolean shellCasing = BowgunRigRenderer
				.isShellCasingBone(bone.getName());
		Identifier gunTexture = shellCasing
				? FortressDestroyerGun.DEFINITION.gunTexture()
				: TkGun.definition(animatable.player()).gunTexture();
		boolean abyssalCase = "case".equals(bone.getName())
				&& TkGun.definition(animatable.player()).id()
						.equals(TkGun.id("abyssal_ancient_cannon"));
		boolean emissive = abyssalCase || glowPart;
		RenderLayer gunLayer = muzzleFire
				? RenderLayer.getEyes(gunTexture)
				: glowPart || abyssalCase
						? RenderLayer.getEntityTranslucentEmissive(
								gunTexture, false)
						: this.renderer.getRenderType(animatable, gunTexture,
								bufferSource, partialTick);
		VertexConsumer gunBuffer = bufferSource.getBuffer(gunLayer);
		int light = emissive
				? net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE
				: packedLight;
		for (GeoCube cube : bone.getCubes()) {
			poseStack.push();
			this.renderer.renderCube(poseStack,
					shellCasing ? remapFortressShellUvs(cube) : cube,
					gunBuffer, light, packedOverlay, 0xFFFFFFFF);
			poseStack.pop();
		}
	}

	/**
	 * The added guns reuse the Fortress shell geometry but inherited different
	 * atlas coordinates. Remap each face proportionally so one shared shell
	 * texture keeps the authored mirroring and face rotation.
	 */
	private static GeoCube remapFortressShellUvs(GeoCube cube) {
		GeoQuad[] source = cube.quads();
		GeoQuad[] remapped = new GeoQuad[source.length];
		for (int index = 0; index < source.length; index++) {
			GeoQuad quad = source[index];
			if (quad == null) {
				continue;
			}
			float[] target = FORTRESS_SHELL_UVS.get(quad.direction());
			if (target == null) {
				remapped[index] = quad;
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			float minU = Float.POSITIVE_INFINITY;
			float maxU = Float.NEGATIVE_INFINITY;
			float minV = Float.POSITIVE_INFINITY;
			float maxV = Float.NEGATIVE_INFINITY;
			for (GeoVertex vertex : vertices) {
				minU = Math.min(minU, vertex.texU());
				maxU = Math.max(maxU, vertex.texU());
				minV = Math.min(minV, vertex.texV());
				maxV = Math.max(maxV, vertex.texV());
			}
			float spanU = maxU - minU;
			float spanV = maxV - minV;
			GeoVertex[] updated = new GeoVertex[vertices.length];
			for (int vertexIndex = 0; vertexIndex < vertices.length; vertexIndex++) {
				GeoVertex vertex = vertices[vertexIndex];
				float amountU = spanU <= 1.0E-8F
						? 0.0F : (vertex.texU() - minU) / spanU;
				float amountV = spanV <= 1.0E-8F
						? 0.0F : (vertex.texV() - minV) / spanV;
				float targetU = target[0] + (target[2] - target[0]) * amountU;
				float targetV = target[1] + (target[3] - target[1]) * amountV;
				updated[vertexIndex] = vertex.withUVs(
						targetU / FORTRESS_TEXTURE_SIZE,
						targetV / FORTRESS_TEXTURE_SIZE);
			}
			remapped[index] = new GeoQuad(updated, quad.normal(),
					quad.direction());
		}
		return new GeoCube(remapped, cube.pivot(), cube.rotation(),
				cube.size(), cube.inflate(), cube.mirror());
	}

	private static float lastFireScaleY = -1.0F;
	private static int lastFireScaleState = -1;

}
