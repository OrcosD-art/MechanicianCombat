package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import com.tk.gun.entity.SoulAnchorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Renders the authored soul-anchor prop with its tail at the entity origin. */
public final class SoulAnchorEntityRenderer
		extends EntityRenderer<SoulAnchorEntity> {
	private static final Identifier TEXTURE =
			TkGun.id("textures/item/soul_anchor_projectile.png");
	private static final float PIXEL = 1.0F / 16.0F;
	private static final float TEXTURE_SIZE = 32.0F;
	private static final Map<String, Cube> CUBES = new HashMap<>();

	static {
		CUBES.put("body", new Cube(
				-1, 0, -1, 1, 5, 1,
				1, 0, 0, 0, 0, -90,
				Map.of(
					"north", face(5, 0, 0, 2, 90),
					"east", face(0, 0, 5, 2, 90),
					"south", face(0, 1, 5, 3, 90),
					"west", face(5, 1, 0, 3, 90),
					"up", face(4, 1, 6, 3, 90),
					"down", face(4, 2, 6, 4, 180))));
		CUBES.put("leftProng", new Cube(
				-2.5F, 5, 0, 2.5F, 9, 0,
				0, 5, 0, 0, -45, 0,
				Map.of("north", face(0, 10, 4, 5, 90))));
		CUBES.put("rightProng", new Cube(
				-2.5F, 7, 0, 2.5F, 9, 0,
				0, 5, 0, 0, 45, 0,
				Map.of("north", face(5, 10, 7, 5, 90))));
	}

	public SoulAnchorEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(SoulAnchorEntity entity, float yaw, float tickDelta,
			MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		Vec3d velocity = entity.getVelocity();
		Vec3d direction = velocity.lengthSquared() > 1.0E-6D
				? velocity.normalize() : entity.getRotationVector();
		matrices.push();
		matrices.multiply(new Quaternionf().rotationTo(
				new Vector3f(0.0F, 1.0F, 0.0F),
				new Vector3f((float) direction.x, (float) direction.y,
						(float) direction.z)));
		// ItemRenderer applies its own (-0.5, -0.5, -0.5) model-space shift.
		// Cancel it so the authored tail locator remains at the entity origin.
		matrices.translate(0.5D, 0.5D, 0.5D);
		ItemStack stack = TkGunServer.SOUL_ANCHOR_PROJECTILE_MODEL == null
				? ItemStack.EMPTY
				: new ItemStack(TkGunServer.SOUL_ANCHOR_PROJECTILE_MODEL);
		if (!stack.isEmpty()) {
			MinecraftClient.getInstance().getItemRenderer().renderItem(
					stack, ModelTransformationMode.NONE,
					LightmapTextureManager.MAX_LIGHT_COORDINATE,
					OverlayTexture.DEFAULT_UV, matrices, consumers,
					entity.getWorld(), entity.getId());
		}
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(SoulAnchorEntity entity) {
		return TEXTURE;
	}

	private static void drawCube(MatrixStack matrices, VertexConsumer buffer,
			Cube cube) {
		matrices.push();
		float pivotY = (16.0F - cube.pivotY) * PIXEL;
		matrices.translate(cube.pivotX * PIXEL, pivotY, cube.pivotZ * PIXEL);
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
		matrices.translate(-cube.pivotX * PIXEL, -pivotY,
				-cube.pivotZ * PIXEL);
		for (Map.Entry<String, Face> entry : cube.faces.entrySet()) {
			drawFace(matrices, buffer, cube, entry.getKey(),
					entry.getValue());
		}
		matrices.pop();
	}

	private static void drawFace(MatrixStack matrices, VertexConsumer buffer,
			Cube cube, String direction, Face face) {
		float x0 = cube.x0 * PIXEL;
		float y0 = (16.0F - cube.y1) * PIXEL;
		float z0 = cube.z0 * PIXEL;
		float x1 = cube.x1 * PIXEL;
		float y1 = (16.0F - cube.y0) * PIXEL;
		float z1 = cube.z1 * PIXEL;
		float[] topLeftBack = {x0, y0, z0};
		float[] topRightBack = {x0, y0, z1};
		float[] bottomLeftBack = {x0, y1, z0};
		float[] bottomRightBack = {x0, y1, z1};
		float[] topLeftFront = {x1, y0, z0};
		float[] topRightFront = {x1, y0, z1};
		float[] bottomLeftFront = {x1, y1, z0};
		float[] bottomRightFront = {x1, y1, z1};
		float[][] vertices = switch (direction) {
			case "north" -> new float[][]{
					topLeftBack, topLeftFront,
					bottomLeftFront, bottomLeftBack};
			case "south" -> new float[][]{
					topRightFront, topRightBack,
					bottomRightBack, bottomRightFront};
			case "west" -> new float[][]{
					topRightBack, topLeftBack,
					bottomLeftBack, bottomRightBack};
			case "east" -> new float[][]{
					topLeftFront, topRightFront,
					bottomRightFront, bottomLeftFront};
			case "up" -> new float[][]{
					topLeftBack, topLeftFront,
					topRightFront, topRightBack};
			default -> new float[][]{
					bottomLeftBack, bottomLeftFront,
					bottomRightFront, bottomRightBack};
		};
		float u0 = face.u0 / TEXTURE_SIZE;
		float v0 = face.v0 / TEXTURE_SIZE;
		float u1 = face.u1 / TEXTURE_SIZE;
		float v1 = face.v1 / TEXTURE_SIZE;
		float[][] uvs = {{u1, v0}, {u0, v0}, {u0, v1}, {u1, v1}};
		if (face.rotation != 0.0F) {
			int turns = Math.floorMod(
					Math.round(face.rotation / 90.0F), 4);
			float[][] rotated = new float[4][];
			for (int index = 0; index < 4; index++) {
				rotated[index] = uvs[(index + turns) % 4];
			}
			uvs = rotated;
		}
		float[] normal = switch (direction) {
			case "north" -> new float[]{0.0F, 0.0F, -1.0F};
			case "south" -> new float[]{0.0F, 0.0F, 1.0F};
			case "west" -> new float[]{-1.0F, 0.0F, 0.0F};
			case "east" -> new float[]{1.0F, 0.0F, 0.0F};
			case "up" -> new float[]{0.0F, 1.0F, 0.0F};
			default -> new float[]{0.0F, -1.0F, 0.0F};
		};
		var entry = matrices.peek();
		var matrix = entry.getPositionMatrix();
		for (int index = 0; index < 4; index++) {
			float[] vertex = vertices[index];
			buffer.vertex(matrix, vertex[0], vertex[1], vertex[2])
					.color(255, 255, 255, 255)
					.texture(uvs[index][0], uvs[index][1])
					.overlay(OverlayTexture.DEFAULT_UV)
					.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
					.normal(entry, normal[0], normal[1], normal[2]);
		}
	}

	private static Face face(float u0, float v0, float u1,
			float v1, float rotation) {
		return new Face(u0, v0, u1, v1, rotation);
	}

	private record Face(float u0, float v0, float u1, float v1,
			float rotation) {
	}

	private record Cube(float x0, float y0, float z0, float x1, float y1,
			float z1, float pivotX, float pivotY, float pivotZ,
			float rotationX, float rotationY, float rotationZ,
			Map<String, Face> faces) {
	}
}
