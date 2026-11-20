package com.tk.gun.client;

import com.tk.gun.entity.WarBannerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** F3+B locator marker for War Banner entities. */
public final class WarBannerDebugRenderer {
	private static final float[] AXIS_X = {0.2F, 0.9F, 1.0F, 0.9F};
	private static final float[] AXIS_Y = {1.0F, 0.9F, 0.2F, 0.9F};
	private static final float[] AXIS_Z = {1.0F, 0.35F, 0.35F, 0.9F};
	private static final float[] DIRECTION = {0.7F, 1.0F, 0.7F, 0.9F};

	private WarBannerDebugRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (context.world() == null || context.consumers() == null
					|| !client.getEntityRenderDispatcher().shouldRenderHitboxes()) {
				return;
			}
			MatrixStack matrices = context.matrixStack();
			Vec3d camera = context.camera().getPos();
			matrices.push();
			try {
				matrices.translate(-camera.x, -camera.y, -camera.z);
				for (Entity entity : context.world().getEntities()) {
					if (!(entity instanceof WarBannerEntity banner)
							|| !banner.isAlive()) {
						continue;
					}
					drawLocator(matrices, context, banner);
				}
			} finally {
				matrices.pop();
			}
		});
	}

	private static void drawLocator(MatrixStack matrices,
			net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context,
			WarBannerEntity banner) {
		double x = banner.getX();
		double y = banner.getY() + WarBannerEntity.LOCATOR_HEIGHT;
		double z = banner.getZ();
		DebugRenderer.drawBox(matrices, context.consumers(),
				box(x, y, z, 0.2D, 0.035D, 0.035D), AXIS_X[0],
				AXIS_X[1], AXIS_X[2], AXIS_X[3]);
		DebugRenderer.drawBox(matrices, context.consumers(),
				box(x, y, z, 0.035D, 0.2D, 0.035D), AXIS_Y[0],
				AXIS_Y[1], AXIS_Y[2], AXIS_Y[3]);
		DebugRenderer.drawBox(matrices, context.consumers(),
				box(x, y, z, 0.035D, 0.035D, 0.2D), AXIS_Z[0],
				AXIS_Z[1], AXIS_Z[2], AXIS_Z[3]);

		double yaw = Math.toRadians(banner.visualYaw());
		Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
		for (int index = 1; index <= 5; index++) {
			Vec3d point = new Vec3d(x, y, z).add(forward.multiply(index * 0.16D));
			DebugRenderer.drawBox(matrices, context.consumers(),
					box(point.x, point.y, point.z,
							0.055D, 0.055D, 0.055D),
					DIRECTION[0], DIRECTION[1],
					DIRECTION[2], DIRECTION[3]);
		}
		DebugRenderer.drawString(matrices, context.consumers(),
				"WarBanner locator", x + 0.15D, y + 0.25D, z,
				0x55FFFFFF);
	}

	private static Box box(double x, double y, double z,
			double halfX, double halfY, double halfZ) {
		return new Box(x - halfX, y - halfY, z - halfZ,
				x + halfX, y + halfY, z + halfZ);
	}
}
