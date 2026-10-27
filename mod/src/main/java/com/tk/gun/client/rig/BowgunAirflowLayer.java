package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * **气流那一层**：素材 v4（2026-10-16）里作者把"气流"重画成了一张**独立的贴图**
 * `airflow.png`（160x32 = 5 帧 32x32），并且是模型的**第 2 张贴图** —— 用第 2 张贴图的
 * 3 个方块（枪口焰第 2/3/4 帧那三块 32x32 平面）在导出时被拆成 `airflow1/2/3` 三根子骨
 * （见 `tools/gen_geckolib_mech.py` 的 `split_foreign_cubes`）。
 *
 * <p>为什么不塞进主贴图：一张 geo 只有一组 `texture_width/height`，而主贴图 64x64 同时被
 * **身体骨骼（玩家皮肤 UV）** 和枪件共用 —— 把尺寸改大，皮肤的 UV 就整体错位（见 SKILL.md 坑 2）。
 * 所以走"第二层 + 自己的贴图"这条路，和 {@link BowgunGunLayer}（枪件用枪贴图）同一套机制。
 *
 * <p>UV 怎么算：作者的 uv 是**在 airflow.png 自己的坐标系里**（u 32..64 / 96..128 / 128..160，
 * v 0..32），但 GeckoLib 烘焙顶点时是按主贴图的 64 除的 → 拿到的 `texU/texV` 是错的。
 * 这里把**每个面自己的 UV 包围盒**归一化到 [0,1]：那三块平面正好各自独占一个 32x32 帧，
 * 归一化就等于"整帧贴上去"，而且**不会猜错朝向**（上下/左右镜像都沿用 GeckoLib 原本的映射）。
 *
 * <p>1x1 的占位面（作者给其余 5 个面填的 uv 是 1 像素的垃圾值）按 UV 跨度过滤掉，
 * 免得把它们拉成整帧画上去。
 */
public class BowgunAirflowLayer extends GeoRenderLayer<BowgunRig> {
	private static final Identifier TEX = Identifier.of("tkgun", "textures/entity/bowgun_airflow.png");
	/** 导出脚本拆出来的骨名前缀（`airflow1/2/3`）。 */
	public static final String BONE_PREFIX = "airflow";
	/**
	 * 面的 UV 跨度小于这个（归一化单位）就当"占位面"跳过。
	 * 主贴图 64 像素，1 像素的垃圾面 = 1/64 ≈ 0.0156，真正的气流面是 32/64 = 0.5。
	 */
	private static final float MIN_UV_SPAN = 4.0F / 64.0F;

	public BowgunAirflowLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	/** 这根骨头是不是"气流平面"（`BowgunGunLayer` 靠它跳过，免得拿枪贴图画）。 */
	public static boolean isAirflowBone(String name) {
		return name != null && name.startsWith(BONE_PREFIX);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable, GeoBone bone, RenderLayer type,
							  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
							  int packedLight, int packedOverlay) {
		if (!isAirflowBone(bone.getName()) || bone.getCubes().isEmpty()) {
			return;
		}
		RenderLayer airflow = this.renderer.getRenderType(animatable, TEX, bufferSource, partialTick);
		VertexConsumer buffer2 = bufferSource.getBuffer(airflow);
		for (GeoCube cube : bone.getCubes()) {
			// 只换 UV，几何（pivot / rotation / size / inflate / mirror）原样抄 ——
			// 位置/朝向交给 GeckoLib 自个儿的 renderCube，和枪件走同一条路，不会跑偏。
			GeoCube swapped = new GeoCube(requantify(cube.quads()), cube.pivot(), cube.rotation(),
					cube.size(), cube.inflate(), cube.mirror());
			this.renderer.renderCube(poseStack, swapped, buffer2, packedLight, packedOverlay, 0xFFFFFFFF);
		}
		if (!logged) {
			logged = true;
			TkGun.LOGGER.info("[tkgun] 气流层已接管 {}（贴图 tkgun:textures/entity/bowgun_airflow.png，160x32 / 5 帧）",
					bone.getName());
		}
	}

	private static boolean logged;

	/** 把每个面的 UV 归一化到 [0,1]；跨度太小（1 像素的占位面）的直接丢掉不画。 */
	private static GeoQuad[] requantify(GeoQuad[] quads) {
		java.util.List<GeoQuad> out = new java.util.ArrayList<>(quads.length);
		for (GeoQuad quad : quads) {
			if (quad == null) {
				continue;   // ⚠️ GeoCube.quads() 里**没写的面就是 null**（作者只填了 north），
				            //    不判空的话 GeckoLib 这里直接 NPE → 客户端渲染线程崩（2026-10-16 实测踩到）
			}
			GeoVertex[] vs = quad.vertices();
			float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
			float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
			for (GeoVertex v : vs) {
				minU = Math.min(minU, v.texU());
				maxU = Math.max(maxU, v.texU());
				minV = Math.min(minV, v.texV());
				maxV = Math.max(maxV, v.texV());
			}
			float du = maxU - minU;
			float dv = maxV - minV;
			if (du < MIN_UV_SPAN || dv < MIN_UV_SPAN) {
				continue;
			}
			GeoVertex[] nv = new GeoVertex[vs.length];
			for (int j = 0; j < vs.length; j++) {
				GeoVertex v = vs[j];
				nv[j] = v.withUVs((v.texU() - minU) / du, (v.texV() - minV) / dv);
			}
			out.add(new GeoQuad(nv, quad.normal(), quad.direction()));
		}
		return out.toArray(new GeoQuad[0]);
	}
}
