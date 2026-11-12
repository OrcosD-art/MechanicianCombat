package com.tk.gun.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import com.tk.gun.item.MechanicianArmorPiece;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

	public enum Feature {
		C1, C2, C3
	}

	private static final Map<MechanicianArmorPiece, Geometry> CACHE =
			new EnumMap<>(MechanicianArmorPiece.class);
	// The bbmodel's metadata still says 16x16, but its face UVs and the authored
	// downsuit.png image both use a 64x64 atlas.
	private static final int TEXTURE_WIDTH = 64;
	private static final int TEXTURE_HEIGHT = 64;
	private static final float PIXEL = 1.0F / 16.0F;

	private MechanicianArmorGeometry() {
	}

	public static void clearCache() {
		CACHE.clear();
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
					entity, limbAngle, limbDistance, headYaw,
					EnumSet.allOf(Feature.class), null);
		}
	}

	public static void render(MechanicianArmorPiece piece, MatrixStack matrices,
							  VertexConsumerProvider consumers,
							  BipedEntityModel<?> baseModel, int light,
							  LivingEntity entity, float limbAngle,
							  float limbDistance, float headYaw,
							  Part... parts) {
		render(piece, matrices, consumers, baseModel, light, entity,
				limbAngle, limbDistance, headYaw,
				EnumSet.noneOf(Feature.class), parts);
	}

	public static void renderHelmet(MatrixStack matrices,
									VertexConsumerProvider consumers,
									BipedEntityModel<?> baseModel, int light,
									LivingEntity entity, float limbAngle,
									float limbDistance, float headYaw,
									String animation, float seconds) {
		Geometry geometry = get(MechanicianArmorPiece.HELMET);
		renderPart(geometry, Part.HEAD, matrices, consumers, baseModel, light,
				entity, limbAngle, limbDistance, headYaw,
				EnumSet.noneOf(Feature.class),
				new AnimationPose(animation, seconds));
	}

	public static void render(MechanicianArmorPiece piece, ItemStack stack,
							  MatrixStack matrices,
							  VertexConsumerProvider consumers,
							  BipedEntityModel<?> baseModel, int light,
							  LivingEntity entity, float limbAngle,
							  float limbDistance, float headYaw,
							  Part... parts) {
		Set<Feature> installed = EnumSet.noneOf(Feature.class);
		for (Feature feature : Feature.values()) {
			boolean hasPart = ArmorUpgradeData.hasPart(stack,
					ArmorPartItem.Kind.valueOf(feature.name()));
			if (entity == MinecraftClient.getInstance().player) {
				hasPart = switch (feature) {
					case C1 -> ArmorAbilityClientState.totemInstalled();
					case C2 -> ArmorAbilityClientState.shieldInstalled();
					case C3 -> ArmorAbilityClientState.soulChainInstalled();
				};
			}
			if (hasPart) {
				installed.add(feature);
			}
		}
		render(piece, matrices, consumers, baseModel, light, entity,
				limbAngle, limbDistance, headYaw, installed, parts);
	}

	private static void render(MechanicianArmorPiece piece, MatrixStack matrices,
							   VertexConsumerProvider consumers,
							   BipedEntityModel<?> baseModel, int light,
							   LivingEntity entity, float limbAngle,
							   float limbDistance, float headYaw,
							   Set<Feature> installed, Part... parts) {
		Geometry geometry = get(piece);
		for (Part part : parts) {
			renderPart(geometry, part, matrices, consumers, baseModel, light,
					entity, limbAngle, limbDistance, headYaw, installed, null);
		}
	}

	private static void renderPart(Geometry geometry, Part part,
								   MatrixStack matrices,
								   VertexConsumerProvider consumers,
								   BipedEntityModel<?> baseModel, int light,
								   LivingEntity entity, float limbAngle,
								   float limbDistance, float headYaw,
								   Set<Feature> installed,
								   AnimationPose animation) {
		List<Cube> cubes = geometry.cubes.get(part);
		if (cubes == null || cubes.isEmpty()) {
			return;
		}
		matrices.push();
		try {
			ModelPart modelPart = part(baseModel, part);
			modelPart.rotate(matrices);
			net.minecraft.client.model.ModelTransform origin =
					modelPart.getDefaultTransform();
			for (Cube cube : cubes) {
				// The helmet bbmodel includes the vanilla head cube as an editing
				// reference. Keep the player's face and skin; only A/B/C are armor.
				if (animation != null && part == Part.HEAD
						&& "\u5934".equals(cube.boneName)) {
					continue;
				}
				if (cube.feature != null && !installed.contains(cube.feature)) {
					continue;
				}
				BoneTransform bone = sampleBone(
						geometry, cube.boneName, animation);
				if (Math.abs(bone.scaleX) < 1.0E-4F
						|| Math.abs(bone.scaleY) < 1.0E-4F
						|| Math.abs(bone.scaleZ) < 1.0E-4F) {
					continue;
				}
				renderCube(matrices, consumers, geometry, cube, light,
						entity, limbAngle, limbDistance, headYaw,
						origin.pivotX, origin.pivotY, origin.pivotZ,
						bone);
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

	private static void renderCube(MatrixStack matrices,
								   VertexConsumerProvider consumers,
								   Geometry geometry, Cube cube, int light,
								   LivingEntity entity, float limbAngle,
								   float limbDistance, float headYaw,
								   float bonePivotX, float bonePivotY,
								   float bonePivotZ, BoneTransform bone) {
		matrices.push();
		try {
			applyBoneTransform(matrices, cube, bone,
					bonePivotX, bonePivotY, bonePivotZ);
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
			drawCube(matrices, consumers, geometry, cube, light,
					bonePivotX, bonePivotY, bonePivotZ);
		} finally {
			matrices.pop();
		}
	}

	private static void applyBoneTransform(MatrixStack matrices, Cube cube,
										   BoneTransform bone,
										   float modelPivotX,
										   float modelPivotY,
										   float modelPivotZ) {
		float pivotX = (cube.bonePivotX - modelPivotX) * PIXEL;
		float pivotY = (cube.bonePivotY - modelPivotY) * PIXEL;
		float pivotZ = (cube.bonePivotZ - modelPivotZ) * PIXEL;
		matrices.translate(pivotX, pivotY, pivotZ);
		matrices.translate(bone.positionX * PIXEL,
				-bone.positionY * PIXEL, bone.positionZ * PIXEL);
		if (bone.rotationZ != 0.0F) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
					bone.rotationZ));
		}
		if (bone.rotationY != 0.0F) {
			matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(
					bone.rotationY));
		}
		if (bone.rotationX != 0.0F) {
			// Bone-space keyframes use Blockbench's Y-up convention. The armor
			// cube conversion reflects Y into Minecraft's Y-down model space, so
			// animated bone rotations need the opposite X/Y axes and unchanged Z.
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(
					bone.rotationX));
		}
		matrices.scale(bone.scaleX, bone.scaleY, bone.scaleZ);
		matrices.translate(-pivotX, -pivotY, -pivotZ);
	}

	private static BoneTransform sampleBone(Geometry geometry,
											String boneName,
											AnimationPose animation) {
		if (animation == null || boneName == null) {
			return BoneTransform.IDENTITY;
		}
		AnimationClip clip = geometry.animations.get(animation.name());
		if (clip == null) {
			return BoneTransform.IDENTITY;
		}
		BoneClip bone = clip.bones.get(boneName);
		if (bone == null) {
			return BoneTransform.IDENTITY;
		}
		float time = MathHelper.clamp(animation.seconds,
				0.0F, clip.length);
		float[] position = sample(bone.position, time, 0.0F, 0.0F, 0.0F);
		float[] rotation = sample(bone.rotation, time, 0.0F, 0.0F, 0.0F);
		float[] scale = sample(bone.scale, time, 1.0F, 1.0F, 1.0F);
		return new BoneTransform(
				position[0], position[1], position[2],
				rotation[0], rotation[1], rotation[2],
				scale[0], scale[1], scale[2]);
	}

	private static float[] sample(List<Keyframe> frames, float time,
								  float defaultX, float defaultY,
								  float defaultZ) {
		if (frames.isEmpty()) {
			return new float[]{defaultX, defaultY, defaultZ};
		}
		Keyframe first = frames.get(0);
		if (time <= first.time) {
			return new float[]{first.x, first.y, first.z};
		}
		Keyframe last = frames.get(frames.size() - 1);
		if (time >= last.time) {
			return new float[]{last.x, last.y, last.z};
		}
		for (int index = 1; index < frames.size(); index++) {
			Keyframe right = frames.get(index);
			if (time > right.time) {
				continue;
			}
			Keyframe left = frames.get(index - 1);
			float span = right.time - left.time;
			float amount = span <= 1.0E-6F
					? 1.0F : (time - left.time) / span;
			return new float[]{
					MathHelper.lerp(amount, left.x, right.x),
					MathHelper.lerp(amount, left.y, right.y),
					MathHelper.lerp(amount, left.z, right.z)
			};
		}
		return new float[]{last.x, last.y, last.z};
	}

	private static void animateCloth(MatrixStack matrices, Cloth cloth,
									 LivingEntity entity, float limbAngle,
									 float limbDistance, float headYaw) {
		float speed = MathHelper.clamp(entity.limbAnimator.getSpeed() * 1.25F,
				0.0F, 1.0F);
		float walk = MathHelper.sin(limbAngle) * limbDistance;
		float idle = MathHelper.sin((entity.age + limbAngle) * 0.09F);
		float twistScale = cloth.back ? 0.30F : 0.22F;
		float twistLimit = cloth.back ? 18.0F : 12.0F;
		float twist = MathHelper.clamp(
				MathHelper.wrapDegrees(headYaw) * twistScale,
				-twistLimit, twistLimit);
		float pitch;
		if (cloth.back) {
			// F2 is the rear drape. Keep its secondary motion pronounced so it
			// reads as cloth instead of a rigid plate attachment.
			pitch = 5.0F + speed * 36.0F + walk * 13.5F + idle * 3.0F;
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

	private static void drawCube(MatrixStack matrices,
								 VertexConsumerProvider consumers,
								 Geometry geometry, Cube cube, int light,
								 float bonePivotX,
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
			String[] order = vertexOrder(direction,
					cube.topFaceXFlip && "UP".equals(direction));
			float[][] uvs = faceUvs(face,
					geometry.uvWidth(face.textureIndex),
					geometry.uvHeight(face.textureIndex));
			float[] normal = normalOf(direction);
			Identifier texture = geometry.texture(face.textureIndex);
			RenderLayer layer;
			if (cube.feature == Feature.C3) {
				AnimatableTexture.setAndUpdate(texture);
				layer = RenderLayer.getEntityCutoutNoCull(texture);
			} else {
				layer = RenderLayer.getArmorCutoutNoCull(texture);
			}
			VertexConsumer buffer = consumers.getBuffer(layer);
			for (int i = 0; i < 4; i++) {
				float[] vertex = vertices.get(order[i]);
				buffer.vertex(matrix, vertex[0], vertex[1], vertex[2])
						.color(255, 255, 255, 255)
						.texture(uvs[i][0], uvs[i][1])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(cube.feature == Feature.C3
								? LightmapTextureManager.MAX_LIGHT_COORDINATE
								: light)
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
		if (face.textureIndex == 0) {
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
		int quarterTurns = Math.floorMod(Math.round(face.rotation / 90.0F), 4);
		float[][] rotated = new float[4][];
		for (int index = 0; index < 4; index++) {
			rotated[index] = out[(index + quarterTurns) % 4];
		}
		return rotated;
	}

	private static String[] vertexOrder(String direction, boolean topFaceXFlip) {
		if ("UP".equals(direction)) {
			return topFaceXFlip
					? new String[]{
							"topLeftFront", "topLeftBack",
							"topRightBack", "topRightFront"}
					: new String[]{
							"topLeftBack", "topLeftFront",
							"topRightFront", "topRightBack"};
		}
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
			// Projected armor vertices use Minecraft's Y-down model space:
			// the geometric top face faces -Y, while the bottom faces +Y.
			case "UP" -> new float[]{0.0F, -1.0F, 0.0F};
			default -> new float[]{0.0F, 1.0F, 0.0F};
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
				walkBone(topLevel.getAsJsonObject(), null, null, null,
						null, 0.0F, 0.0F, 0.0F, elements, cubes);
			}
		}
		Map<Integer, TextureInfo> textures = new HashMap<>();
		JsonArray sourceTextures = document.getAsJsonArray("textures");
		for (int index = 0; index < sourceTextures.size(); index++) {
			JsonObject sourceTexture =
					sourceTextures.get(index).getAsJsonObject();
			String name = sourceTexture.get("name").getAsString();
			Identifier texture = switch (name) {
				case "eng_chain.png" -> Identifier.of("mechaniciancombat",
						"textures/entity/mechanician_soul_chain.png");
				default -> piece.textureLocation();
			};
			textures.put(index, new TextureInfo(texture,
					sourceTexture.has("uv_width")
							? sourceTexture.get("uv_width").getAsInt() : TEXTURE_WIDTH,
					sourceTexture.has("uv_height")
							? sourceTexture.get("uv_height").getAsInt() : TEXTURE_HEIGHT));
		}
		return new Geometry(textures, cubes, loadAnimations(document));
	}

	private static Map<String, AnimationClip> loadAnimations(
			JsonObject document) {
		Map<String, AnimationClip> animations = new HashMap<>();
		if (!document.has("animations")
				|| !document.get("animations").isJsonArray()) {
			return animations;
		}
		for (JsonElement animationElement :
				document.getAsJsonArray("animations")) {
			JsonObject animation = animationElement.getAsJsonObject();
			String name = animation.get("name").getAsString();
			float length = animation.has("length")
					? animation.get("length").getAsFloat() : 0.0F;
			Map<String, BoneClip> bones = new HashMap<>();
			if (animation.has("animators")
					&& animation.get("animators").isJsonObject()) {
				for (JsonElement animatorElement :
						animation.getAsJsonObject("animators")
								.asMap().values()) {
					JsonObject animator = animatorElement.getAsJsonObject();
					String boneName = animator.has("name")
							? animator.get("name").getAsString() : "";
					if (boneName.isEmpty() || !animator.has("keyframes")) {
						continue;
					}
					List<Keyframe> position = new ArrayList<>();
					List<Keyframe> rotation = new ArrayList<>();
					List<Keyframe> scale = new ArrayList<>();
					for (JsonElement keyframeElement :
							animator.getAsJsonArray("keyframes")) {
						JsonObject keyframe = keyframeElement.getAsJsonObject();
						if (!keyframe.has("data_points")
								|| keyframe.getAsJsonArray("data_points")
								.isEmpty()) {
							continue;
						}
						JsonObject point = keyframe.getAsJsonArray(
								"data_points").get(0).getAsJsonObject();
						Keyframe frame = new Keyframe(
								keyframe.get("time").getAsFloat(),
								point.get("x").getAsFloat(),
								point.get("y").getAsFloat(),
								point.get("z").getAsFloat());
						switch (keyframe.get("channel").getAsString()) {
							case "position" -> position.add(frame);
							case "rotation" -> rotation.add(frame);
							case "scale" -> scale.add(frame);
							default -> {
							}
						}
					}
					Comparator<Keyframe> byTime =
							Comparator.comparingDouble(frame -> frame.time);
					position.sort(byTime);
					rotation.sort(byTime);
					scale.sort(byTime);
					bones.put(boneName,
							new BoneClip(position, rotation, scale));
				}
			}
			animations.put(name, new AnimationClip(length, bones));
		}
		return animations;
	}

	private static void walkBone(JsonObject bone, Part inheritedPart,
								 Cloth inheritedCloth,
								 Feature inheritedFeature,
								 String inheritedBoneName,
								 float inheritedPivotX,
								 float inheritedPivotY,
								 float inheritedPivotZ,
								 Map<String, JsonObject> elements,
								 Map<Part, List<Cube>> cubes) {
		String name = bone.has("name") ? bone.get("name").getAsString() : "";
		String boneName = name.isEmpty() ? inheritedBoneName : name;
		float[] boneOrigin = vector(
				bone.has("origin") ? bone.getAsJsonArray("origin") : null,
				inheritedPivotX, inheritedPivotY, inheritedPivotZ);
		float bonePivotX = boneOrigin[0];
		float bonePivotY = 24.0F - boneOrigin[1];
		float bonePivotZ = boneOrigin[2];
		Part part = partFor(name);
		if (part == null) {
			part = inheritedPart;
		}
		Feature feature = switch (name) {
			case "pauldorn_left" -> Feature.C2;
			case "undying_totem", "totem" -> Feature.C1;
				case "soul_chain" -> Feature.C3;
			default -> inheritedFeature;
		};
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
							.add(toCube(element, cloth, feature, boneName,
									bonePivotX, bonePivotY, bonePivotZ));
				}
			} else if (child.isJsonObject()) {
				JsonObject childBone = child.getAsJsonObject();
				if (!"locator".equals(childBone.has("type")
						? childBone.get("type").getAsString() : "bone")) {
					walkBone(childBone, part, cloth, feature, boneName,
							bonePivotX, bonePivotY, bonePivotZ,
							elements, cubes);
				}
			}
		}
	}

	private static Cube toCube(JsonObject element, Cloth cloth, Feature feature,
							   String boneName, float bonePivotX,
							   float bonePivotY, float bonePivotZ) {
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
			int textureIndex = sourceFace.has("texture")
					? sourceFace.get("texture").getAsInt() : 0;
			faces.put(direction,
					new Face(u0, v0, u1, v1, uvRotation, textureIndex));
		}
		return new Cube(
				Math.min(from[0], to[0]), 24.0F - Math.max(from[1], to[1]),
				Math.min(from[2], to[2]),
				Math.max(from[0], to[0]), 24.0F - Math.min(from[1], to[1]),
				Math.max(from[2], to[2]),
				pivot[0], 24.0F - pivot[1], pivot[2],
				rotation[0], rotation[1], rotation[2],
				Math.abs(rotation[2]) >= 89.0F, faces, cloth, feature,
				boneName, bonePivotX, bonePivotY, bonePivotZ);
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
		String path = "/assets/mechaniciancombat/" + piece.modelLocation().getPath();
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

	private record Geometry(Map<Integer, TextureInfo> textures,
							Map<Part, List<Cube>> cubes,
							Map<String, AnimationClip> animations) {
		private Identifier texture(int index) {
			return info(index).texture();
		}

		private int uvWidth(int index) {
			return info(index).uvWidth();
		}

		private int uvHeight(int index) {
			return info(index).uvHeight();
		}

		private TextureInfo info(int index) {
			return textures.getOrDefault(index,
					textures.getOrDefault(0, new TextureInfo(
							Identifier.of("mechaniciancombat",
									"textures/entity/mechanician_chestplate.png"),
							TEXTURE_WIDTH, TEXTURE_HEIGHT)));
		}
	}

	private record TextureInfo(Identifier texture, int uvWidth, int uvHeight) {
	}

	private record Face(float u0, float v0, float u1, float v1,
						float rotation, int textureIndex) {
	}

	private record AnimationPose(String name, float seconds) {
	}

	private record AnimationClip(float length,
								 Map<String, BoneClip> bones) {
	}

	private record BoneClip(List<Keyframe> position,
							List<Keyframe> rotation,
							List<Keyframe> scale) {
	}

	private record Keyframe(float time, float x, float y, float z) {
	}

	private record BoneTransform(
			float positionX, float positionY, float positionZ,
			float rotationX, float rotationY, float rotationZ,
			float scaleX, float scaleY, float scaleZ) {
		private static final BoneTransform IDENTITY = new BoneTransform(
				0.0F, 0.0F, 0.0F,
				0.0F, 0.0F, 0.0F,
				1.0F, 1.0F, 1.0F);
	}

	private record Cloth(boolean back, float pivotX, float pivotY,
						 float pivotZ) {
	}

	private record Cube(float x0, float y0, float z0,
						float x1, float y1, float z1,
						float pivotX, float pivotY, float pivotZ,
						float rotationX, float rotationY, float rotationZ,
						boolean topFaceXFlip,
						Map<String, Face> faces, Cloth cloth,
						Feature feature, String boneName,
						float bonePivotX, float bonePivotY,
						float bonePivotZ) {
	}
}
