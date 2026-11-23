package com.tk.gun.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.AnchorLockRenderLayers;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Foreground 3x3-block melee slash hit, matching the authored 48x48 pixels. */
public final class SlashHitForegroundRenderer {
	private static final float SIZE = 3.0F;
	private static final RenderLayer[][] DEPTH_LAYERS =
			new RenderLayer[SlashHitClientState.PATTERN_COUNT]
					[SlashHitClientState.FRAME_COUNT];
	private static final RenderLayer[][] STENCIL_LAYERS =
			new RenderLayer[SlashHitClientState.PATTERN_COUNT]
					[SlashHitClientState.FRAME_COUNT];
	private static final RenderLayer TARGET_MASK =
			AnchorLockRenderLayers.createStencilMask(
					"mechaniciancombat_slash_hit_target_mask");

	static {
		for (int pattern = 0;
				pattern < SlashHitClientState.PATTERN_COUNT; pattern++) {
			for (int frame = 0;
					frame < SlashHitClientState.FRAME_COUNT; frame++) {
				String suffix = (pattern + 1) + "_" + frame;
				DEPTH_LAYERS[pattern][frame] =
						AnchorLockRenderLayers.createDepthTested(
						TkGun.id("textures/particle/slash_hit_"
								+ suffix + ".png"),
						"mechaniciancombat_slash_hit_depth_"
								+ suffix);
				STENCIL_LAYERS[pattern][frame] =
						AnchorLockRenderLayers.create(
						TkGun.id("textures/particle/slash_hit_"
								+ suffix + ".png"),
						"mechaniciancombat_slash_hit_"
								+ suffix);
			}
		}
	}

	private record PreparedEffect(SlashHitClientState.Effect effect,
								  Entity target, int frame,
								  int stencilValue) {
	}

	private SlashHitForegroundRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(
				SlashHitForegroundRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (context.world() == null) {
			return;
		}
		List<SlashHitClientState.Effect> effects =
				SlashHitClientState.effects();
		if (effects.isEmpty()) {
			return;
		}
		long now = context.world().getTime();
		List<PreparedEffect> prepared = new ArrayList<>();
		for (SlashHitClientState.Effect effect : effects) {
			int frame = effect.age(now);
			if (frame >= SlashHitClientState.FRAME_COUNT
					|| prepared.size() >= 255) {
				continue;
			}
			Entity target = context.world().getEntityById(
					effect.targetEntityId());
			if (target == null || !target.isAlive()) {
				continue;
			}
			prepared.add(new PreparedEffect(effect, target, frame,
					prepared.size() + 1));
		}
		if (prepared.isEmpty()) {
			return;
		}

		VertexConsumerProvider consumers = context.consumers();
		Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
		Vec3d camera = context.camera().getPos();
		Vector3f right = context.camera().getRotation()
				.transform(new Vector3f(1.0F, 0.0F, 0.0F));
		Vector3f up = context.camera().getRotation()
				.transform(new Vector3f(0.0F, 1.0F, 0.0F));

		boolean stencil = SlashHitStencilBuffer.begin(
				MinecraftClient.getInstance().getFramebuffer());
		try {
			if (stencil) {
				writeTargetMasks(consumers, matrix, camera, prepared);
			}
			for (PreparedEffect effect : prepared) {
				drawEffect(consumers, matrix, camera, right, up,
						effect, stencil);
			}
		} finally {
			if (stencil) {
				SlashHitStencilBuffer.end();
			}
		}
	}

	private static void writeTargetMasks(VertexConsumerProvider consumers,
			Matrix4f matrix, Vec3d camera,
			List<PreparedEffect> effects) {
		GlStateManager._stencilMask(0xFF);
		GlStateManager._stencilOp(GL11.GL_KEEP, GL11.GL_KEEP,
				GL11.GL_REPLACE);
		for (PreparedEffect effect : effects) {
			GlStateManager._stencilFunc(GL11.GL_ALWAYS,
					effect.stencilValue(), 0xFF);
			VertexConsumer buffer = consumers.getBuffer(TARGET_MASK);
			drawMaskBox(buffer, matrix, camera,
					effect.target().getBoundingBox());
			if (consumers
					instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw(TARGET_MASK);
			}
		}
		GlStateManager._stencilMask(0x00);
		GlStateManager._stencilOp(GL11.GL_KEEP, GL11.GL_KEEP,
				GL11.GL_KEEP);
	}

	private static void drawEffect(VertexConsumerProvider consumers,
			Matrix4f matrix, Vec3d camera, Vector3f right, Vector3f up,
			PreparedEffect effect, boolean stencil) {
		SlashHitClientState.Effect state = effect.effect();
		if (stencil) {
			GlStateManager._stencilFunc(GL11.GL_NOTEQUAL,
					effect.stencilValue(), 0xFF);
		}
		drawEffectLayer(consumers, matrix, camera, right, up, effect,
				DEPTH_LAYERS[state.pattern()][effect.frame()]);
		if (stencil) {
			GlStateManager._stencilFunc(GL11.GL_EQUAL,
					effect.stencilValue(), 0xFF);
			drawEffectLayer(consumers, matrix, camera, right, up, effect,
					STENCIL_LAYERS[state.pattern()][effect.frame()]);
		}
	}

	private static void drawEffectLayer(VertexConsumerProvider consumers,
			Matrix4f matrix, Vec3d camera, Vector3f right, Vector3f up,
			PreparedEffect effect, RenderLayer layer) {
		VertexConsumer buffer = consumers.getBuffer(layer);
		drawBillboard(matrix, buffer,
				SlashHitClientState.position(effect.effect(), effect.target())
						.subtract(camera),
				right, up);
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(layer);
		}
	}

	private static void drawMaskBox(VertexConsumer buffer, Matrix4f matrix,
			Vec3d camera, Box box) {
		float minX = (float) (box.minX - camera.x);
		float minY = (float) (box.minY - camera.y);
		float minZ = (float) (box.minZ - camera.z);
		float maxX = (float) (box.maxX - camera.x);
		float maxY = (float) (box.maxY - camera.y);
		float maxZ = (float) (box.maxZ - camera.z);
		maskQuad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ,
				maxX, minY, maxZ, minX, minY, maxZ);
		maskQuad(buffer, matrix, minX, maxY, minZ, minX, maxY, maxZ,
				maxX, maxY, maxZ, maxX, maxY, minZ);
		maskQuad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ,
				maxX, maxY, minZ, maxX, minY, minZ);
		maskQuad(buffer, matrix, minX, minY, maxZ, maxX, minY, maxZ,
				maxX, maxY, maxZ, minX, maxY, maxZ);
		maskQuad(buffer, matrix, minX, minY, minZ, minX, minY, maxZ,
				minX, maxY, maxZ, minX, maxY, minZ);
		maskQuad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ,
				maxX, maxY, maxZ, maxX, minY, maxZ);
	}

	private static void maskQuad(VertexConsumer buffer, Matrix4f matrix,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3) {
		maskVertex(buffer, matrix, x0, y0, z0);
		maskVertex(buffer, matrix, x1, y1, z1);
		maskVertex(buffer, matrix, x2, y2, z2);
		maskVertex(buffer, matrix, x3, y3, z3);
	}

	private static void maskVertex(VertexConsumer buffer, Matrix4f matrix,
			float x, float y, float z) {
		buffer.vertex(matrix, x, y, z).color(255, 255, 255, 255);
	}

	private static void drawBillboard(Matrix4f matrix, VertexConsumer buffer,
			Vec3d center, Vector3f right, Vector3f up) {
		float half = SIZE * 0.5F;
		Vector3f left = new Vector3f(right).mul(-half);
		Vector3f rightSide = new Vector3f(right).mul(half);
		Vector3f down = new Vector3f(up).mul(-half);
		Vector3f top = new Vector3f(up).mul(half);
		Vec3d bottomLeft = center.add(left.x + down.x,
				left.y + down.y, left.z + down.z);
		Vec3d bottomRight = center.add(rightSide.x + down.x,
				rightSide.y + down.y, rightSide.z + down.z);
		Vec3d topRight = center.add(rightSide.x + top.x,
				rightSide.y + top.y, rightSide.z + top.z);
		Vec3d topLeft = center.add(left.x + top.x,
				left.y + top.y, left.z + top.z);
		vertex(matrix, buffer, bottomLeft, 0.0F, 1.0F);
		vertex(matrix, buffer, bottomRight, 1.0F, 1.0F);
		vertex(matrix, buffer, topRight, 1.0F, 0.0F);
		vertex(matrix, buffer, topLeft, 0.0F, 0.0F);
	}

	private static void vertex(Matrix4f matrix, VertexConsumer buffer,
			Vec3d point, float u, float v) {
		buffer.vertex(matrix, (float) point.x, (float) point.y,
						(float) point.z)
				.color(255, 255, 255, 255)
				.texture(u, v)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}
}
