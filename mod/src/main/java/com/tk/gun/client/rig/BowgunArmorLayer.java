package com.tk.gun.client.rig;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

/**
 * 把原版玩家盔甲画在 GeckoLib rig 的身体骨骼上。
 *
 * <p>本模组在手持破垒重炮时取消的是**整条 PlayerEntityRenderer**，原版
 * {@code ArmorFeatureRenderer} 也跟着不执行。这里不能再拿原版 PlayerEntityModel 近似 rig 姿势
 * （那会造成之前出现过的肢体变形），而是在每一根身体骨骼自己的 poseStack 里，把该骨骼的方块
 * 按盔甲贴图再画一遍：
 *
 * <ul>
 *   <li>头盔 → {@code head}</li>
 *   <li>胸甲 → {@code chest} + 两条手臂</li>
 *   <li>护腿 → {@code chest}（腰带部分）+ 两条腿</li>
 *   <li>靴子 → 两条腿</li>
 * </ul>
 *
	 * <p>玩家的 64x64 皮肤和 64x32 盔甲纹理使用同一套玩家模型**像素坐标**，但归一化后的 V
	 * 要按 64x32 重新换算（纵向乘 2），否则所有面都会采样到错误的盔甲区域。
	 * 几何不用重新生成，只按原版 {@code Dilation} 的规则把方块整体向六个方向各扩 1px
	 *（护腿内层 0.5px），再换
	 * {@code RenderLayer.getArmorCutoutNoCull(texture)}。
 * 染色、多材质层、盔甲镶饰和附魔光效都按原版 ArmorFeatureRenderer 的数据路径处理。
 *
 * <p>可关闭：启动参数 {@code -Dtkgun.armorlayer=false}。
 */
public final class BowgunArmorLayer extends GeoRenderLayer<BowgunRig> {
	private static final boolean ENABLED =
			!"false".equalsIgnoreCase(System.getProperty("tkgun.armorlayer", "true"));
	/** 原版玩家盔甲贴图的默认染色值（皮革）。 */
	private static final int DEFAULT_LEATHER_COLOR = -6265536;

	public BowgunArmorLayer(GeoRenderer<BowgunRig> renderer) {
		super(renderer);
	}

	@Override
	public void renderForBone(MatrixStack poseStack, BowgunRig animatable, GeoBone bone,
							  RenderLayer type, VertexConsumerProvider buffers, VertexConsumer buffer,
							  float partialTick, int packedLight, int packedOverlay) {
		if (!ENABLED || bone.getCubes().isEmpty()) {
			return;
		}
		if (!(this.renderer instanceof BowgunRigRenderer rig)) {
			return;
		}
		Object current = rig.getCurrentEntity();
		if (!(current instanceof LivingEntity living)) {
			return;
		}
		List<EquipmentSlot> slots = slotsFor(bone.getName());
		if (slots.isEmpty()) {
			return;
		}
		for (EquipmentSlot slot : slots) {
			ItemStack stack = living.getEquippedStack(slot);
			if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType() != slot) {
				continue;
			}
			boolean inner = slot == EquipmentSlot.LEGS;
			float inflate = (inner ? 0.5F : 1.0F) / 16.0F;
			int color = stack.isIn(ItemTags.DYEABLE)
					? (DyedColorComponent.getColor(stack, DEFAULT_LEATHER_COLOR) | 0xFF000000)
					: -1;
			ArmorMaterial material = armor.getMaterial().value();
			for (ArmorMaterial.Layer materialLayer : material.layers()) {
				int layerColor = materialLayer.isDyeable() ? color : -1;
				Identifier texture = materialLayer.getTexture(inner);
				renderCubes(poseStack, bone, buffers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture)),
						packedLight, packedOverlay, layerColor, inflate);
			}
			renderTrim(poseStack, bone, stack, armor, inner, buffers, packedLight, packedOverlay, inflate);
			if (stack.hasGlint()) {
				renderCubes(poseStack, bone, buffers.getBuffer(RenderLayer.getArmorEntityGlint()),
						packedLight, packedOverlay, -1, inflate);
			}
		}
	}

	/** 一根 rig 骨骼需要承接哪些装备槽（护腿和靴子会共用腿骨）。 */
	private static List<EquipmentSlot> slotsFor(String bone) {
		return switch (bone) {
			case "head" -> List.of(EquipmentSlot.HEAD);
			case "chest" -> List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS);
			case "arm_left", "arm_right" -> List.of(EquipmentSlot.CHEST);
			case "leg_left", "leg_right" -> List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET);
			default -> List.of();
		};
	}

	private void renderTrim(MatrixStack poseStack, GeoBone bone, ItemStack stack, ArmorItem armor,
							boolean inner, VertexConsumerProvider buffers, int packedLight,
							int packedOverlay, float inflate) {
		ArmorTrim trim = stack.get(DataComponentTypes.TRIM);
		if (trim == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getBakedModelManager() == null) {
			return;
		}
		Identifier modelId = inner
				? trim.getLeggingsModelId(armor.getMaterial())
				: trim.getGenericModelId(armor.getMaterial());
		Sprite sprite = client.getBakedModelManager()
				.getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE)
				.getSprite(modelId);
		RenderLayer layer = TexturedRenderLayers.getArmorTrims(
				trim.getPattern().value().decal());
		VertexConsumer trimBuffer = sprite.getTextureSpecificVertexConsumer(
				buffers.getBuffer(layer));
		renderCubes(poseStack, bone, trimBuffer, packedLight, packedOverlay, -1, inflate);
	}

	private void renderCubes(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer,
							 int packedLight, int packedOverlay, int color, float inflate) {
		if (!(this.renderer instanceof BowgunRigRenderer rig)) {
			return;
		}
		GeoBone sourceBone = bone;
		String mirrorSource = switch (bone.getName()) {
			case "arm_left" -> "arm_right";
			case "leg_left" -> "leg_right";
			default -> null;
		};
		if (mirrorSource != null) {
			sourceBone = rig.getGeoModel().getBone(mirrorSource).orElse(null);
			if (sourceBone == null) {
				return;
			}
		}
		for (GeoCube cube : sourceBone.getCubes()) {
			GeoCube shaped = mirrorSource != null ? mirrorX(cube) : cube;
			this.renderer.renderCube(poseStack, dilate(shaped, inflate), buffer,
					packedLight, packedOverlay, color);
		}
	}

	/**
	 * 按原版 {@code Dilation} 的规则扩张方块：每条轴的 min/max 都向两侧各移 1px，
	 * 相邻面仍然共享顶点，不会像“逐面沿法线平移”那样在六个棱边留下透明缝隙。
	 * UV 仍覆盖完整的原版盔甲面。
	 */
	private static GeoCube dilate(GeoCube cube, float amount) {
		GeoQuad[] quads = cube.quads();
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (GeoQuad quad : quads) {
			if (quad == null) {
				continue;
			}
			for (GeoVertex vertex : quad.vertices()) {
				Vector3f p = vertex.position();
				minX = Math.min(minX, p.x());
				minY = Math.min(minY, p.y());
				minZ = Math.min(minZ, p.z());
				maxX = Math.max(maxX, p.x());
				maxY = Math.max(maxY, p.y());
				maxZ = Math.max(maxZ, p.z());
			}
		}
		float centerX = (minX + maxX) * 0.5F;
		float centerY = (minY + maxY) * 0.5F;
		float centerZ = (minZ + maxZ) * 0.5F;
		GeoQuad[] out = new GeoQuad[quads.length];
		for (int i = 0; i < quads.length; i++) {
			GeoQuad quad = quads[i];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			GeoVertex[] moved = new GeoVertex[vertices.length];
			for (int j = 0; j < vertices.length; j++) {
				GeoVertex v = vertices[j];
				Vector3f p = new Vector3f(v.position());
				p.x = p.x < centerX ? p.x - amount : p.x + amount;
				p.y = p.y < centerY ? p.y - amount : p.y + amount;
				p.z = p.z < centerZ ? p.z - amount : p.z + amount;
				moved[j] = new GeoVertex(
						p,
						v.texU(), v.texV() * 2.0F);
			}
			out[i] = new GeoQuad(moved, quad.normal(), quad.direction());
		}
		return new GeoCube(out, cube.pivot(), cube.rotation(), cube.size(),
				cube.inflate(), cube.mirror());
	}

	/**
	 * 取右臂/右腿的 UV 和几何，沿模型 X=0 镜像后交给左骨骼。
	 *
	 * <p>皮肤左侧区域（32..44 / 16..28，y48..60）和原版盔甲贴图不是同一套面排列，
	 * 只做 U 翻转会串面。原版 ArmorEntityModel 的做法就是复用右侧 cuboid 的 UV，
	 * 以 {@code mirrored=true} 生成左侧几何；这里等价地镜像顶点、法线和 pivot。
	 */
	private static GeoCube mirrorX(GeoCube cube) {
		GeoQuad[] quads = cube.quads();
		GeoQuad[] out = new GeoQuad[quads.length];
		for (int i = 0; i < quads.length; i++) {
			GeoQuad quad = quads[i];
			if (quad == null) {
				continue;
			}
			GeoVertex[] vertices = quad.vertices();
			GeoVertex[] mirrored = new GeoVertex[vertices.length];
			for (int j = 0; j < vertices.length; j++) {
				GeoVertex v = vertices[j];
				Vector3f p = new Vector3f(v.position());
				p.x = -p.x;
				mirrored[j] = new GeoVertex(p, v.texU(), v.texV());
			}
			Vector3f normal = new Vector3f(quad.normal());
			normal.x = -normal.x;
			Direction direction = switch (quad.direction()) {
				case EAST -> Direction.WEST;
				case WEST -> Direction.EAST;
				default -> quad.direction();
			};
			out[i] = new GeoQuad(mirrored, normal, direction);
		}
		Vec3d pivot = cube.pivot();
		return new GeoCube(out, new Vec3d(-pivot.x, pivot.y, pivot.z),
				cube.rotation(), cube.size(), cube.inflate(), cube.mirror());
	}
}
