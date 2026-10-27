package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * **玩家皮肤的第二层**（头发 / 帽子 / 衣服覆盖层）。
 * 用户 2026-10-17："为玩家的模型添加第二层皮肤（头发等）的渲染"。
 *
 * <h2>为什么原版第二层会丢</h2>
 * 我们的 mod 把玩家模型整个换成 GeckoLib rig（`GeoReplacedEntity`，见 {@link BowgunRig}），
 * 身体那 6 块骨骼的 UV 是导出脚本按**官方玩家几何的第一层**写死的（`gen_geckolib_official#skin_table()`）——
 * 原版模型里那套"第二层"方块（hat / jacket / sleeve / pant）自然就不存在了。
 *
 * <h2>怎么补（和 {@link BowgunGunLayer} / {@link BowgunAirflowLayer} 同一套路：几何不动，重画一遍）</h2>
 * 对这 6 块身体骨骼，把它们的方块**再画一遍**：
 * <ul>
 *   <li><b>UV 平移到原版皮肤的第二层区域</b>：64x64 皮肤的标准版式是
 *       头 +32u、躯干/右臂/右腿 +16v、左臂 +16u、左腿 −16u（下面的 {@link #LAYER2} 表）；</li>
 *   <li><b>沿面法线外扩一点点</b>（原版：帽子 0.5 像素、其余 0.25 像素）——
 *       不外扩就和第一层完全重合，会 z-fighting 花掉。</li>
 * </ul>
 *
 * <p>贴图和第一层共用（就是玩家皮肤本身），所以直接往当前那一层（参数里的 `type` / `buffer`）里画，
 * 不需要新建 RenderLayer。想关掉：启动参数 {@code -Dtkgun.skinlayer=false}。
 *
 * <p>已知限制：作者的几何是**宽手臂**（4x12x4），slim（Alex）皮肤的袖子层会稍微错位 —— 和第一层一样。
 */
public class BowgunSkinLayer extends GeoRenderLayer<BowgunRig> {
	/** 每块身体骨骼的第二层参数：{UV 偏移 u（像素）, UV 偏移 v（像素）, 法线外扩（像素）}。 */
	private static final java.util.Map<String, float[]> LAYER2 = java.util.Map.of(
			"head", new float[]{32.0F, 0.0F, 0.5F},       // hat
			"chest", new float[]{0.0F, 16.0F, 0.25F},     // jacket
			"arm_right", new float[]{0.0F, 16.0F, 0.25F}, // right sleeve
			"leg_right", new float[]{0.0F, 16.0F, 0.25F}, // right pant
			"arm_left", new float[]{16.0F, 0.0F, 0.25F},  // left sleeve
			"leg_left", new float[]{-16.0F, 0.0F, 0.25F}); // left pant
	private static final boolean ENABLED =
			!"false".equalsIgnoreCase(System.getProperty("tkgun.skinlayer", "true"));

	public BowgunSkinLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	/** 这根骨头有没有第二层（`head`/`chest`/两条胳膊/两条腿）。 */
	public static boolean hasSecondLayer(String name) {
		return LAYER2.containsKey(name);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable, GeoBone bone, RenderLayer type,
							  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
							  int packedLight, int packedOverlay) {
		if (!ENABLED || bone.getCubes().isEmpty()) {
			return;
		}
		// 头部已经戴了装备（默认是原版头盔）时不要再叠第二层皮肤，否则头发/帽子会从头盔里穿出来。
		if ("head".equals(bone.getName()) && hasHeadEquipment()) {
			return;
		}
		float[] spec = LAYER2.get(bone.getName());
		if (spec == null) {
			return;
		}
		var definition = TkGun.definition(BowgunRig.CURRENT_PLAYER);
		float du = spec[0] / definition.textureWidth();
		float dv = spec[1] / definition.textureHeight();
		float inflate = spec[2] / 16.0F;          // 像素 → 格
		for (GeoCube cube : bone.getCubes()) {
			// 位置/朝向/尺寸/pivot 全部沿用原方块，只改 UV 和"外扩"——
			// 于是它永远跟着第一层一起动（同一份骨骼姿态），不需要重新烘焙任何东西。
			this.renderer.renderCube(poseStack, shift(cube, du, dv, inflate), buffer,
					packedLight, packedOverlay, 0xFFFFFFFF);
		}
	}

	private boolean hasHeadEquipment() {
		if (!(this.renderer instanceof BowgunRigRenderer rig)) {
			return false;
		}
		return rig.getCurrentEntity() instanceof LivingEntity living
				&& !living.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
	}

	/** 复制一份方块：UV 整体平移 (du,dv)，顶点沿面法线外扩 {@code inflate}（格）。 */
	private static GeoCube shift(GeoCube cube, float du, float dv, float inflate) {
		GeoQuad[] quads = cube.quads();
		GeoQuad[] out = new GeoQuad[quads.length];
		for (int i = 0; i < quads.length; i++) {
			GeoQuad quad = quads[i];
			if (quad == null) {
				continue;   // ⚠️ 没写 UV 的面就是 null（airflow 层踩过：不判空直接 NPE 崩渲染线程）
			}
			Vector3f normal = new Vector3f(quad.normal());
			if (normal.lengthSquared() > 1.0E-8F) {
				normal.normalize();
			}
			GeoVertex[] vs = quad.vertices();
			GeoVertex[] nv = new GeoVertex[vs.length];
			for (int j = 0; j < vs.length; j++) {
				GeoVertex v = vs[j];
				Vector3f p = new Vector3f(v.position()).fma(inflate, normal);   // p + inflate * normal
				nv[j] = new GeoVertex(p, v.texU() + du, v.texV() + dv);
			}
			out[i] = new GeoQuad(nv, quad.normal(), quad.direction());
		}
		return new GeoCube(out, cube.pivot(), cube.rotation(), cube.size(), cube.inflate(), cube.mirror());
	}
}
