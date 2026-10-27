package com.tk.gun.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

/**
 * 枪 / 枪口焰 / 抛壳 / dash 速度线的几何与动画（我们自己生成的 gunfx/*.json）。
 *
 * 坐标系：原版玩家模型空间 M（y 向下、原点在脖子线、1 单位 = 1/16 格），
 * 所以顶点直接按 px/16 发出去，和原版 ModelPart 完全同一套。
 * 每个骨骼的变换形式固定为 v -> R·S·(v - p) + p + d（p=枢轴, d=位移），
 * 这正是转换器 bbmodel_to_playeranim.py 反解出来的口径。
 */
public final class GunModel {
	public record Bone(String name, String parent, float[] pivot, List<Cube> cubes) {
	}

	public record Cube(float[] origin, float[] size, Map<String, float[][]> uv) {
	}

	public static final class Anim {
		public final int length;
		public final Map<String, float[][][]> bones = new HashMap<>();   // bone -> [3][len+1][3] (rot,pos,scale)

		Anim(int length) {
			this.length = length;
		}

		public float[] rot(String bone, int frame) {
			return channel(bone, 0, frame);
		}

		public float[] pos(String bone, int frame) {
			return channel(bone, 1, frame);
		}

		public float[] scale(String bone, int frame) {
			return channel(bone, 2, frame);
		}

		private float[] channel(String bone, int idx, int frame) {
			float[][][] c = bones.get(bone);
			if (c == null) {
				return idx == 2 ? new float[]{1.0F, 1.0F, 1.0F} : new float[]{0.0F, 0.0F, 0.0F};
			}
			int f = Math.max(0, Math.min(c[idx].length - 1, frame));
			return c[idx][f];
		}
	}

	private static GunModel instance;
	private final List<Bone> bones = new ArrayList<>();
	private final Map<String, Bone> byName = new HashMap<>();
	private final Set<String> gunSide = new HashSet<>();
	private final Set<String> effectSide = new HashSet<>();
	private final Map<String, Anim> anims = new HashMap<>();
	public static final Identifier TEXTURE = TkGun.id("textures/entity/gun/bowgun.png");
	private final int texW;
	private final int texH;

	private GunModel() {
		JsonObject geo = read(TkGun.id("gunfx/bg_geo.json"));
		texW = geo.get("texture_width").getAsInt();
		texH = geo.get("texture_height").getAsInt();
		for (JsonElement el : geo.getAsJsonArray("bones")) {
			JsonObject b = el.getAsJsonObject();
			List<Cube> cubes = new ArrayList<>();
			if (b.has("cubes")) {
				for (JsonElement ce : b.getAsJsonArray("cubes")) {
					JsonObject c = ce.getAsJsonObject();
					Map<String, float[][]> uv = new HashMap<>();
					JsonObject uvs = c.getAsJsonObject("uv");
					for (String face : uvs.keySet()) {
						JsonObject f = uvs.getAsJsonObject(face);
						uv.put(face, new float[][]{vec(f.getAsJsonArray("uv")), vec(f.getAsJsonArray("uv_size"))});
					}
					cubes.add(new Cube(vec(c.getAsJsonArray("origin")), vec(c.getAsJsonArray("size")), uv));
				}
			}
			Bone bone = new Bone(b.get("name").getAsString(),
					b.get("parent").isJsonNull() ? null : b.get("parent").getAsString(),
					vec(b.getAsJsonArray("pivot")), cubes);
			bones.add(bone);
			byName.put(bone.name(), bone);
		}
		for (Bone b : bones) {
			if (isDescendantOf(b.name(), "gun")) {
				gunSide.add(b.name());
			}
			if (b.name().equals("bone2") || b.name().startsWith("dash")) {
				effectSide.add(b.name());
			}
		}
	}

	private boolean isDescendantOf(String name, String root) {
		String cur = name;
		int guard = 0;
		while (cur != null && guard++ < 64) {
			if (cur.equals(root)) {
				return true;
			}
			Bone b = byName.get(cur);
			cur = b == null ? null : b.parent();
		}
		return false;
	}

	public static GunModel get() {
		if (instance == null) {
			try {
				instance = new GunModel();
			} catch (Throwable t) {
				TkGun.LOGGER.error("[tkgun] 枪械几何加载失败", t);
				instance = null;
			}
		}
		return instance;
	}

	public Anim anim(String name) {
		return anims.computeIfAbsent(name, n -> {
			JsonObject doc = read(TkGun.id("gunfx/" + n.toLowerCase(Locale.ROOT) + ".json"));
			int len = doc.get("length").getAsInt();
			Anim a = new Anim(len);
			JsonObject bonesObj = doc.getAsJsonObject("bones");
			for (String bone : bonesObj.keySet()) {
				JsonObject ch = bonesObj.getAsJsonObject(bone);
				float[][][] data = new float[3][len + 1][3];
				for (int i = 0; i < 3; i++) {
					String key = i == 0 ? "rotation" : (i == 1 ? "position" : "scale");
					if (ch.has(key)) {
						for (JsonElement ke : ch.getAsJsonArray(key)) {
							JsonArray arr = ke.getAsJsonArray();
							int tick = Math.min(len, arr.get(0).getAsInt());
							data[i][tick] = new float[]{(float) arr.get(1).getAsDouble(),
									(float) arr.get(2).getAsDouble(), (float) arr.get(3).getAsDouble()};
						}
					}
					for (int f = 0; f <= len; f++) {
						if (data[i][f] == null) {
							data[i][f] = i == 2 ? new float[]{1.0F, 1.0F, 1.0F} : new float[]{0.0F, 0.0F, 0.0F};
						}
					}
				}
				a.bones.put(bone, data);
			}
			return a;
		});
	}

	private static JsonObject read(Identifier id) {
		Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			throw new IllegalStateException("missing resource " + id);
		}
		try (InputStreamReader r = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("bad resource " + id, e);
		}
	}

	private static float[] vec(JsonArray a) {
		float[] out = new float[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = (float) a.get(i).getAsDouble();
		}
		return out;
	}

	// ------------------------------------------------------------------ 渲染
	/**
	 * @param gun    true=画枪那一支（挂在躯干骨骼下，调用方已经 body.rotate 过）
	 * @param effect true=画 dash 速度线（独立根骨骼 bone2，不跟躯干）
	 */
	public void render(MatrixStack ms, String animName, int frame, boolean gun, boolean effect, int light,
					   net.minecraft.client.render.VertexConsumerProvider consumers) {
		Anim a = anim(animName);
		// ⚠️ 必须用渲染器传进来的 provider：自己从 MinecraftClient 取 Immediate 的缓冲会在
		//    "非 building" 状态下写顶点，抛 IllegalStateException("Not building!")，
		//    而且会把同帧后续（背包里的物品图标）一起渲染废掉。
		VertexConsumer solid = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		VertexConsumer glow = consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
		if (gun) {
			Bone root = byName.get("gun");
			if (root != null) {
				renderTree(ms, root, a, frame, solid, glow, light);
			}
		}
		if (effect) {
			Bone root = byName.get("bone2");
			if (root != null) {
				renderTree(ms, root, a, frame, solid, glow, light);
			}
		}
	}

	private List<Bone> childrenOf(String name) {
		List<Bone> out = new ArrayList<>();
		for (Bone b : bones) {
			if (name.equals(b.parent())) {
				out.add(b);
			}
		}
		return out;
	}

	/** 树形遍历：本地变换是"相对父级"的，必须把父级累积起来（枪口焰/抛壳都在 gun 的子树里）。 */
	private void renderTree(MatrixStack ms, Bone bone, Anim a, int frame,
							VertexConsumer solid, VertexConsumer glow, int light) {
		float[] p = bone.pivot();
		float[] d = a.pos(bone.name(), frame);
		float[] r = a.rot(bone.name(), frame);
		float[] s = a.scale(bone.name(), frame);
		if (Math.abs(s[0]) < 1e-4F || Math.abs(s[1]) < 1e-4F || Math.abs(s[2]) < 1e-4F) {
			return;                              // scale 0 = 整支隐藏（枪口焰/速度线就是靠这个开关的）
		}
		ms.push();
		ms.translate(d[0] / 16.0F, d[1] / 16.0F, d[2] / 16.0F);
		ms.translate(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F);
		if (r[2] != 0.0F) {
			ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r[2]));
		}
		if (r[1] != 0.0F) {
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(r[1]));
		}
		if (r[0] != 0.0F) {
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(r[0]));
		}
		if (s[0] != 1.0F || s[1] != 1.0F || s[2] != 1.0F) {
			ms.scale(s[0], s[1], s[2]);
		}
		ms.translate(-p[0] / 16.0F, -p[1] / 16.0F, -p[2] / 16.0F);

		VertexConsumer vc = bone.name().startsWith("fire") ? glow : solid;
		for (Cube cube : bone.cubes()) {
			drawCube(ms, vc, cube, light);
		}
		for (Bone child : childrenOf(bone.name())) {
			renderTree(ms, child, a, frame, solid, glow, light);
		}
		ms.pop();
	}

	private void drawCube(MatrixStack ms, VertexConsumer vc, Cube cube, int light) {
		float[] o = cube.origin();
		float[] sz = cube.size();
		// 8 个角（顶点名与 mc-assets 一致，供下面按面取）
		float x0 = o[0] / 16.0F, y0 = o[1] / 16.0F, z0 = o[2] / 16.0F;
		float x1 = (o[0] + sz[0]) / 16.0F, y1 = (o[1] + sz[1]) / 16.0F, z1 = (o[2] + sz[2]) / 16.0F;
		Map<String, float[]> vs = new HashMap<>();
		vs.put("bottomLeftBack", new float[]{x0, y0, z0});
		vs.put("bottomRightBack", new float[]{x0, y0, z1});
		vs.put("topLeftBack", new float[]{x0, y1, z0});
		vs.put("topRightBack", new float[]{x0, y1, z1});
		vs.put("topLeftFront", new float[]{x1, y1, z0});
		vs.put("topRightFront", new float[]{x1, y1, z1});
		vs.put("bottomLeftFront", new float[]{x1, y0, z0});
		vs.put("bottomRightFront", new float[]{x1, y0, z1});
		MatrixStack.Entry entry = ms.peek();
		Matrix4f m = entry.getPositionMatrix();
		for (Map.Entry<String, float[][]> face : cube.uv().entrySet()) {
			String dir = face.getKey().toUpperCase();
			float[] uv = face.getValue()[0];
			float[] uvSize = face.getValue()[1];
			String[] order = vertexOrder(dir);
			// Blockbench/GeckoLib 的非镜像约定：u 轴与顶点顺序相反（见 preview_geo.build_quad 的 swap）
			float ua = uv[0] / texW, va = uv[1] / texH;
			float ub = (uv[0] + uvSize[0]) / texW, vb = (uv[1] + uvSize[1]) / texH;
			float[][] uvs = {{ub, va}, {ua, va}, {ua, vb}, {ub, vb}};
			float[] n = normalOf(dir);
			for (int i = 0; i < 4; i++) {
				float[] v = vs.get(order[i]);
				vc.vertex(m, v[0], v[1], v[2]).color(255, 255, 255, 255)
						.texture(uvs[i][0], uvs[i][1]).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
						.light(light).normal(entry, n[0], n[1], n[2]);
			}
		}
	}

	/** 与 GeckoLib/Blockbench 的顶点顺序一致（见 preview_geo.vertices_for_quad）。 */
	private static String[] vertexOrder(String dir) {
		switch (dir) {
			case "WEST":
				return new String[]{"topRightBack", "topLeftBack", "bottomLeftBack", "bottomRightBack"};
			case "EAST":
				return new String[]{"topLeftFront", "topRightFront", "bottomRightFront", "bottomLeftFront"};
			case "NORTH":
				return new String[]{"topLeftBack", "topLeftFront", "bottomLeftFront", "bottomLeftBack"};
			case "SOUTH":
				return new String[]{"topRightFront", "topRightBack", "bottomRightBack", "bottomRightFront"};
			case "UP":
				return new String[]{"bottomLeftBack", "bottomLeftFront", "bottomRightFront", "bottomRightBack"};
			default:
				return new String[]{"bottomLeftBack", "bottomLeftFront", "bottomRightFront", "bottomRightBack"};
		}
	}

	private static float[] normalOf(String dir) {
		switch (dir) {
			case "WEST":
				return new float[]{-1.0F, 0.0F, 0.0F};
			case "EAST":
				return new float[]{1.0F, 0.0F, 0.0F};
			case "NORTH":
				return new float[]{0.0F, 0.0F, -1.0F};
			case "SOUTH":
				return new float[]{0.0F, 0.0F, 1.0F};
			case "UP":
				return new float[]{0.0F, 1.0F, 0.0F};
			default:
				return new float[]{0.0F, -1.0F, 0.0F};
		}
	}
}
