package com.tk.gun.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.TkGun;
import com.tk.gun.item.MechanicianArmorPiece;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the authored Blockbench armor without converting its per-face UVs to
 * a vanilla box UV layout.
 *
 * <p>Blockbench uses Y-up player coordinates. A vanilla {@link ModelPart} uses
 * the same pixel scale but Y points down from the neck, so the vertical vertex
 * conversion is {@code minecraftY = 24 - blockbenchY}. Negative-X limb groups
 * are the player's right arm/leg in vanilla, regardless of their Chinese
 * Blockbench label.</p>
 */
public final class MechanicianArmorGeometry {
	public enum Part {
		HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG
	}

	private static final Map<MechanicianArmorPiece, Geometry> CACHE =
			new EnumMap<>(MechanicianArmorPiece.class);
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/mechanician_downsuit.png");
	// The bbmodel's metadata still says 16x16, but its face UVs and the authored
	// downsuit.png image both use a 64x64 atlas.
	private static final int TEXTURE_WIDTH = 64;
	private static final int TEXTURE_HEIGHT = 64;
	private static final float PIXEL = 1.0F / 16.0F;

	private MechanicianArmorGeometry() {
	}

	public static void renderAll(MechanicianArmorPiece piece, MatrixStack matrices,
								 VertexConsumerProvider consumers,
								 BipedEntityModel<?> baseModel, int light,
								 LivingEntity entity, float limbAngle,
								 float limbDistance, float headYaw) {
		Geometry geometry = get(piece);
		for (Part part : Part.values()) {
			if (part == Part.HEAD && piece == MechanicianArmorPiece.UNDERSUIT) {
				continue;
			}
			renderPart(geometry, part, matrices, consumers, baseModel, light,
					entity, limbAngle, limbDistance, headYaw);
		}
	}

	public static void render(MechanicianArmorPiece piece, MatrixStack matrices,
							  VertexConsumerProvider consumers,
							  BipedEntityModel<?> baseModel, int light,
							  LivingEntity entity, float limbAngle,
							  float limbDistance, float headYaw,
							  Part... parts) {
		Geometry geometry = get(piece);
		for (Part part : parts) {
			renderPart(geometry, part, matrices, consumers, baseModel, light,
					entity, limbAngle, limbDistance, headYaw);
		}
	}

	private static void renderPart(Geometry geometry, Part part,
								   MatrixStack matrices,
								   VertexConsumerProvider consumers,
								   BipedEntityModel<?> baseModel, int light,
								   LivingEntity entity, float limbAngle,
								   float limbDistance, float headYaw) {
		List<Cube> cubes = geometry.cubes.get(part);
		if (cubes == null || cubes.isEmpty()) {
			return;
		}
		VertexConsumer buffer = consumers.getBuffer(
				RenderLayer.getArmorCutoutNoCull(TEXTURE));
		matrices.push();
		try {
			ModelPart modelPart = part(baseModel, part);
			modelPart.rotate(matrices);
			net.minecraft.client.model.ModelTransform origin =
					modelPart.getDefaultTransform();
			for (Cube cube : cubes) {
				renderCube(matrices, buffer, geometry, cube, light,
						entity, limbAngle, limbDistance, headYaw,
						origin.pivotX, origin.pivotY, origin.pivotZ);
			}
		} finally {
			matrices.pop();
		}
	}

	private static ModelPart part(BipedEntityModel<?> model, Part part) {
		return switch (part) {
			case HEAD -> model.head;
			case BODY -> model.body;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_ARM -> model.leftArm;
			case RIGHT_LEG -> model.rightLeg;
			case LEFT_LEG -> model.leftLeg;
		};
	}

	private static void renderCube(MatrixStack matrices, VertexConsumer buffer,
								   Geometry geometry, Cube cube, int light,
								   LivingEntity entity, float limbAngle,
								   float limbDistance, float headYaw,
								   float bonePivotX, float bonePivotY,
								   float bonePivotZ) {
		matrices.push();
		try {
			if (cube.cloth != null) {
				animateCloth(matrices, cube.cloth, entity, limbAngle,
						limbDistance, headYaw);
			}
			if (cube.rotationX != 0.0F || cube.rotationY != 0.0F
					|| cube.rotationZ != 0.0F) {
				matrices.translate((cube.pivotX - bonePivotX) * PIXEL,
						(cube.pivotY - bonePivotY) * PIXEL,
						(cube.pivotZ - bonePivotZ) * PIXEL);
				if (cube.rotationZ != 0.0F) {
					matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(
							cube.rotationZ));
				}
				if (cube.rotationY != 0.0F) {
					matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
							cube.rotationY));
				}
				if (cube.rotationX != 0.0F) {
					matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(
							cube.rotationX));
				}
				matrices.translate(-(cube.pivotX - bonePivotX) * PIXEL,
						-(cube.pivotY - bonePivotY) * PIXEL,
						-(cube.pivotZ - bonePivotZ) * PIXEL);
			}
			drawCube(matrices, buffer, geometry.textureWidth,
					geometry.textureHeight, cube, light,
					bonePivotX, bonePivotY, bonePivotZ);
		} finally {
			matrices.pop();
		}
	}

	private static void animateCloth(MatrixStack matrices, Cloth cloth,
									 LivingEntity entity, float limbAngle,
									 float limbDistance, float headYaw) {
		float speed = MathHelper.clamp(entity.limbAnimator.getSpeed() * 1.25F,
				0.0F, 1.0F);
		float walk = MathHelper.sin(limbAngle) * limbDistance;
		float idle = MathHelper.sin((entity.age + limbAngle) * 0.09F);
		float twist = MathHelper.clamp(
				MathHelper.wrapDegrees(headYaw) * 0.22F, -12.0F, 12.0F);
		float pitch;
		if (cloth.back) {
			pitch = 5.0F + speed * 24.0F + walk * 9.0F + idle * 2.0F;
		} else {
			pitch = -2.0F - speed * 7.0F - walk * 4.0F - idle;
		}
		matrices.translate(cloth.pivotX * PIXEL, cloth.pivotY * PIXEL,
				cloth.pivotZ * PIXEL);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
				cloth.back ? twist : twist * 0.55F));
		matrices.translate(-cloth.pivotX * PIXEL, -cloth.pivotY * PIXEL,
				-cloth.pivotZ * PIXEL);
	}

	private static void drawCube(MatrixStack matrices, VertexConsumer buffer,
								 int textureWidth, int textureHeight,
								 Cube cube, int light, float bonePivotX,
								 float bonePivotY, float bonePivotZ) {
		float[] p = {
				(cube.x0 - bonePivotX) * PIXEL,
				(cube.y0 - bonePivotY) * PIXEL,
				(cube.z0 - bonePivotZ) * PIXEL,
				(cube.x1 - bonePivotX) * PIXEL,
				(cube.y1 - bonePivotY) * PIXEL,
				(cube.z1 - bonePivotZ) * PIXEL
		};
		Map<String, float[]> vertices = new HashMap<>();
		// Minecraft model coordinates grow downward, so min-Y is the top face.
		vertices.put("topLeftBack", new float[]{p[0], p[1], p[2]});
		vertices.put("topRightBack", new float[]{p[0], p[1], p[5]});
		vertices.put("bottomLeftBack", new float[]{p[0], p[4], p[2]});
		vertices.put("bottomRightBack", new float[]{p[0], p[4], p[5]});
		vertices.put("topLeftFront", new float[]{p[3], p[1], p[2]});
		vertices.put("topRightFront", new float[]{p[3], p[1], p[5]});
		vertices.put("bottomLeftFront", new float[]{p[3], p[4], p[2]});
		vertices.put("bottomRightFront", new float[]{p[3], p[4], p[5]});

		MatrixStack.Entry entry = matrices.peek();
		Matrix4f matrix = entry.getPositionMatrix();
		for (Map.Entry<String, Face> entryFace : cube.faces.entrySet()) {
			String direction = entryFace.getKey().toUpperCase(Locale.ROOT);
			Face face = entryFace.getValue();
			String[] order = vertexOrder(direction);
			float[][] uvs = faceUvs(face, textureWidth, textureHeight);
			float[] normal = normalOf(direction);
			for (int i = 0; i < 4; i++) {
				float[] vertex = vertices.get(order[i]);
				buffer.vertex(matrix, vertex[0], vertex[1], vertex[2])
						.color(255, 255, 255, 255)
						.texture(uvs[i][0], uvs[i][1])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(light)
						.normal(entry, normal[0], normal[1], normal[2]);
			}
		}
	}

	private static float[][] faceUvs(Face face, int width, int height) {
		float u0 = face.u0 / width;
		float v0 = face.v0 / height;
		float u1 = face.u1 / width;
		float v1 = face.v1 / height;
		float[][] out = {{u1, v0}, {u0, v0}, {u0, v1}, {u1, v1}};
		if (face.rotation == 0.0F) {
			return out;
		}
		float centerU = (u0 + u1) * 0.5F;
		float centerV = (v0 + v1) * 0.5F;
		double radians = Math.toRadians(face.rotation);
		float cos = (float) Math.cos(radians);
		float sin = (float) Math.sin(radians);
		for (float[] uv : out) {
			float du = uv[0] - centerU;
			float dv = uv[1] - centerV;
			uv[0] = centerU + du * cos - dv * sin;
			uv[1] = centerV + du * sin + dv * cos;
		}
		return out;
	}

	private static String[] vertexOrder(String direction) {
		return switch (direction) {
			case "WEST" -> new String[]{
					"topRightBack", "topLeftBack",
					"bottomLeftBack", "bottomRightBack"};
			case "EAST" -> new String[]{
					"topLeftFront", "topRightFront",
					"bottomRightFront", "bottomLeftFront"};
			case "NORTH" -> new String[]{
					"topLeftBack", "topLeftFront",
					"bottomLeftFront", "bottomLeftBack"};
			case "SOUTH" -> new String[]{
					"topRightFront", "topRightBack",
					"bottomRightBack", "bottomRightFront"};
			case "UP" -> new String[]{
					"topLeftBack", "topLeftFront",
					"topRightFront", "topRightBack"};
			case "DOWN" -> new String[]{
					"bottomLeftBack", "bottomLeftFront",
					"bottomRightFront", "bottomRightBack"};
			default -> new String[]{
					"bottomLeftBack", "bottomLeftFront",
					"bottomRightFront", "bottomRightBack"};
		};
	}

	private static float[] normalOf(String direction) {
		return switch (direction) {
			case "WEST" -> new float[]{-1.0F, 0.0F, 0.0F};
			case "EAST" -> new float[]{1.0F, 0.0F, 0.0F};
			case "NORTH" -> new float[]{0.0F, 0.0F, -1.0F};
			case "SOUTH" -> new float[]{0.0F, 0.0F, 1.0F};
			case "UP" -> new float[]{0.0F, 1.0F, 0.0F};
			default -> new float[]{0.0F, -1.0F, 0.0F};
		};
	}

	private static Geometry get(MechanicianArmorPiece piece) {
		return CACHE.computeIfAbsent(piece, MechanicianArmorGeometry::load);
	}

	private static Geometry load(MechanicianArmorPiece piece) {
		JsonObject document = read(piece);
		Map<String, JsonObject> elements = new HashMap<>();
		for (JsonElement element : document.getAsJsonArray("elements")) {
			JsonObject cube = element.getAsJsonObject();
			if ("cube".equals(cube.has("type")
					? cube.get("type").getAsString() : "cube")) {
				elements.put(cube.get("uuid").getAsString(), cube);
			}
		}
		Map<Part, List<Cube>> cubes = new EnumMap<>(Part.class);
		for (JsonElement topLevel : document.getAsJsonArray("outliner")) {
			if (topLevel.isJsonObject()) {
				walkBone(topLevel.getAsJsonObject(), null, null,
						elements, cubes);
			}
		}
		return new Geometry(TEXTURE_WIDTH, TEXTURE_HEIGHT, cubes);
	}

	private static void walkBone(JsonObject bone, Part inheritedPart,
								 Cloth inheritedCloth,
								 Map<String, JsonObject> elements,
								 Map<Part, List<Cube>> cubes) {
		String name = bone.has("name") ? bone.get("name").getAsString() : "";
		Part part = partFor(name);
		if (part == null) {
			part = inheritedPart;
		}
		Cloth cloth = inheritedCloth;
		if ("F1".equals(name) || "F2".equals(name)) {
			float[] pivot = vector(bone.getAsJsonArray("origin"), 0, 0, 0);
			cloth = new Cloth("F2".equals(name), pivot[0], 24.0F - pivot[1],
					pivot[2]);
		}
		JsonArray children = bone.has("children")
				? bone.getAsJsonArray("children") : new JsonArray();
		for (JsonElement child : children) {
			if (child.isJsonPrimitive()) {
				JsonObject element = elements.get(child.getAsString());
				if (element != null && part != null) {
					cubes.computeIfAbsent(part, ignored -> new ArrayList<>())
							.add(toCube(element, cloth));
				}
			} else if (child.isJsonObject()) {
				JsonObject childBone = child.getAsJsonObject();
				if (!"locator".equals(childBone.has("type")
						? childBone.get("type").getAsString() : "bone")) {
					walkBone(childBone, part, cloth, elements, cubes);
				}
			}
		}
	}

	private static Cube toCube(JsonObject element, Cloth cloth) {
		float[] from = vector(element.getAsJsonArray("from"), 0, 0, 0);
		float[] to = vector(element.getAsJsonArray("to"), 0, 0, 0);
		float[] pivot = vector(element.has("origin")
				? element.getAsJsonArray("origin") : null,
				(from[0] + to[0]) * 0.5F,
				(from[1] + to[1]) * 0.5F,
				(from[2] + to[2]) * 0.5F);
		float[] rotation = vector(element.has("rotation")
				? element.getAsJsonArray("rotation") : null, 0, 0, 0);
		Map<String, Face> faces = new HashMap<>();
		JsonObject sourceFaces = element.getAsJsonObject("faces");
		for (String direction : sourceFaces.keySet()) {
			JsonObject sourceFace = sourceFaces.getAsJsonObject(direction);
			if (!sourceFace.has("uv")
					|| (sourceFace.has("texture")
					&& sourceFace.get("texture").isJsonNull())) {
				continue;
			}
			JsonArray uv = sourceFace.getAsJsonArray("uv");
			float u0 = uv.get(0).getAsFloat();
			float v0 = uv.get(1).getAsFloat();
			float u1 = uv.get(2).getAsFloat();
			float v1 = uv.get(3).getAsFloat();
			float uvRotation = sourceFace.has("rotation")
					? sourceFace.get("rotation").getAsFloat() : 0.0F;
			faces.put(direction, new Face(u0, v0, u1, v1, uvRotation));
		}
		return new Cube(
				Math.min(from[0], to[0]), 24.0F - Math.max(from[1], to[1]),
				Math.min(from[2], to[2]),
				Math.max(from[0], to[0]), 24.0F - Math.min(from[1], to[1]),
				Math.max(from[2], to[2]),
				pivot[0], 24.0F - pivot[1], pivot[2],
				rotation[0], rotation[1], rotation[2], faces, cloth);
	}

	private static Part partFor(String name) {
		return switch (name) {
			case "\u5934" -> Part.HEAD;
			case "\u4e0a\u534a\u8eab", "\u80f8\u7532" -> Part.BODY;
			// The authored negative-X limb is the vanilla right limb.
			case "\u5de6\u81c2" -> Part.RIGHT_ARM;
			case "\u53f3\u81c2" -> Part.LEFT_ARM;
			case "\u5de6\u817f" -> Part.RIGHT_LEG;
			case "\u53f3\u817f" -> Part.LEFT_LEG;
			default -> null;
		};
	}

	private static JsonObject read(MechanicianArmorPiece piece) {
		String path = "/assets/tkgun/" + piece.modelLocation().getPath();
		try (InputStream stream = MechanicianArmorGeometry.class
				.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IllegalStateException("Missing armor model " + path);
			}
			return JsonParser.parseString(new String(stream.readAllBytes(),
					StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to load " + path, exception);
		}
	}

	private static float[] vector(JsonArray array, float x, float y, float z) {
		if (array == null) {
			return new float[]{x, y, z};
		}
		return new float[]{array.get(0).getAsFloat(),
				array.get(1).getAsFloat(), array.get(2).getAsFloat()};
	}

	private record Geometry(int textureWidth, int textureHeight,
							Map<Part, List<Cube>> cubes) {
	}

	private record Face(float u0, float v0, float u1, float v1,
						float rotation) {
	}

	private record Cloth(boolean back, float pivotX, float pivotY,
						 float pivotZ) {
	}

	private record Cube(float x0, float y0, float z0,
						float x1, float y1, float z1,
						float pivotX, float pivotY, float pivotZ,
						float rotationX, float rotationY, float rotationZ,
						Map<String, Face> faces, Cloth cloth) {
	}
}
