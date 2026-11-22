package com.tk.gun.client;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F3+B visualization of the server-authored melee volumes. This intentionally
 * reconstructs the same box/cylinder tests used by TkGunServer instead of
 * drawing a generic Better Combat-style approximation.
 */
public final class MeleeHitboxDebugRenderer {
	private enum Shape {
		ORIENTED_BOX,
		RADIAL
	}

	private record HitVolume(Shape shape, Vec3d center, Vec3d right,
							 Vec3d up, Vec3d forward, double firstExtent,
							 double secondExtent, double thirdExtent,
							 String label) {
	}

	private record DashSample(int shotSeq, int elapsed, Vec3d position) {
	}

	private static final Map<Integer, DashSample> DASH_SAMPLES =
			new HashMap<>();
	private static final int[] BOX_EDGES = {
			0, 1, 1, 2, 2, 3, 3, 0,
			4, 5, 5, 6, 6, 7, 7, 4,
			0, 4, 1, 5, 2, 6, 3, 7
	};

	private MeleeHitboxDebugRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(
				MeleeHitboxDebugRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (context.world() == null || context.consumers() == null
				|| client.player == null
				|| !client.getEntityRenderDispatcher()
						.shouldRenderHitboxes()
				|| !TkGun.isMeleeWeapon(client.player)) {
			return;
		}
		GunClientState.Entry entry =
				GunClientState.get(client.player.getId());
		if (entry == null || entry.state != TkGun.State.MELEE
				|| entry.meleeReturning) {
			DASH_SAMPLES.remove(client.player.getId());
			return;
		}

		HitVolume volume = buildVolume(client.player, entry,
				context.tickCounter().getTickDelta(false));
		if (volume == null) {
			DASH_SAMPLES.remove(client.player.getId());
			return;
		}
		List<Entity> targets = intersectingTargets(client.player, volume);
		boolean intersects = !targets.isEmpty();

		MatrixStack matrices = context.matrixStack();
		Vec3d camera = context.camera().getPos();
		matrices.push();
		try {
			matrices.translate(-camera.x, -camera.y, -camera.z);
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			VertexConsumer lines =
					context.consumers().getBuffer(RenderLayer.getLines());
			if (volume.shape() != Shape.RADIAL) {
				drawOrientedBox(lines, matrix, volume, intersects);
			} else {
				drawRadialVolume(lines, matrix, volume, intersects);
			}
			for (Entity target : targets) {
				drawWorldBox(lines, matrix, target.getBoundingBox(),
						1.0F, 0.12F, 0.12F, 1.0F);
			}
			if (context.consumers()
					instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw(RenderLayer.getLines());
			}
			Vec3d labelPosition = volume.center().add(0.0D,
					volume.shape() != Shape.RADIAL
							? volume.secondExtent() + 0.2D
							: volume.secondExtent() + 0.2D,
					0.0D);
			DebugRenderer.drawString(matrices, context.consumers(),
					volume.label(), labelPosition.x, labelPosition.y,
					labelPosition.z, 0xFFFFFFFF);
		} finally {
			matrices.pop();
		}

		if (!entry.dashMeleeActive) {
			DASH_SAMPLES.remove(client.player.getId());
		}
	}

	private static HitVolume buildVolume(PlayerEntity player,
			GunClientState.Entry entry, float tickDelta) {
		float pitch = player.getPitch(tickDelta);
		float yawDegrees = player.getYaw(tickDelta);
		Vec3d forward = Vec3d.fromPolar(pitch, yawDegrees).normalize();
		Vec3d up = Vec3d.fromPolar(pitch + 90.0F,
				yawDegrees).normalize();
		Vec3d right = forward.crossProduct(up).normalize();
		Vec3d feet = player.getLerpedPos(tickDelta);

		if (TkGun.isKineticSword(player)) {
			int step = kineticSwordStep(entry);
			double attackRange = TkGun.kineticSwordAttackRange(step);
			Vec3d origin = feet.add(0.0D, 1.0D, 0.0D);
			Vec3d center = origin.add(
					forward.multiply(attackRange * 0.5D));
			return new HitVolume(Shape.ORIENTED_BOX, center, right, up,
					forward, 1.5D, 1.5D, attackRange * 0.5D,
					kineticSwordLabel(entry, step));
		}

		double range = TkGun.MELEE_RANGE;
		if (entry.dashMeleeActive) {
			Vec3d origin = feet.add(0.0D,
					player.getStandingEyeHeight() * 0.15D, 0.0D);
			Vec3d current = origin.add(forward.multiply(range * 0.5D));
			double halfDepth = range * 0.5D;
			String label = "Dash attack OBB";
			DashSample previous = DASH_SAMPLES.get(player.getId());
			if (previous != null
					&& previous.shotSeq() == entry.shotSeq
					&& entry.elapsed >= TkGun.MELEE_DASH_MOVE_TICKS
					&& entry.elapsed != previous.elapsed()) {
				Vec3d segmentCenter = previous.position()
						.add(feet).multiply(0.5D)
						.add(0.0D, player.getStandingEyeHeight()
								* 0.15D, 0.0D);
				double pathLength = Math.hypot(
						feet.x - previous.position().x,
						feet.z - previous.position().z);
				current = segmentCenter.add(
						forward.multiply(range * 0.5D));
				halfDepth += pathLength * 0.5D;
				label = "Dash hard-stop sweep";
			}
			if (previous == null
					|| previous.shotSeq() != entry.shotSeq
					|| entry.elapsed != previous.elapsed()) {
				DASH_SAMPLES.put(player.getId(),
						new DashSample(entry.shotSeq,
								entry.elapsed, feet));
			}
			return new HitVolume(Shape.ORIENTED_BOX, current, right, up,
					forward, range, range / 6.0D, halfDepth, label);
		}

		Vec3d origin = feet.add(0.0D, 0.9D, 0.0D);
		int step = Math.max(1, Math.min(4, entry.shotIndex));
		if (entry.meleeHoldOver) {
			return new HitVolume(Shape.RADIAL, origin, right, up, forward,
					range, 2.5D, 0.0D, "ATK_hold_over radial");
		}
		if (step == 3 || step == 4) {
			return new HitVolume(Shape.RADIAL, origin, right, up, forward,
					range, 2.5D, 0.0D,
					"ATK_" + step + " radial");
		}
		if (step == 2) {
			return new HitVolume(Shape.ORIENTED_BOX,
					origin.add(forward.multiply(2.0D)), right, up, forward,
					TkGun.MELEE_LATERAL_RADIUS, 1.5D, 2.0D,
					"ATK_2 forward OBB");
		}
		return new HitVolume(Shape.ORIENTED_BOX,
				origin.add(forward.multiply(1.875D)), right, up, forward,
				TkGun.MELEE_LATERAL_RADIUS, 1.8D, 2.125D,
				"ATK_1 forward OBB");
	}

	private static int kineticSwordStep(GunClientState.Entry entry) {
		int step = Math.max(1, Math.min(6, entry.shotIndex));
		if (entry.kineticSwordAccelerationAttack) {
			return 5;
		}
		if (entry.kineticSwordSpecial <= 0) {
			return step;
		}
		int currentStep = entry.kineticSwordSpecial;
		int[] damageTicks = TkGun.kineticSwordSpecialDamageTicks(
				entry.kineticSwordSpecial);
		for (int damageTick : damageTicks) {
			if (entry.elapsed >= damageTick) {
				currentStep++;
			}
		}
		return Math.min(5, currentStep);
	}

	private static String kineticSwordLabel(GunClientState.Entry entry,
			int step) {
		String suffix = entry.kineticSwordAccelerationAttack
				? " sprint attack OBB"
				: entry.kineticSwordSpecial > 0
						? " / special " + entry.kineticSwordSpecial
								+ "-5 OBB"
						: " OBB";
		return "Kinetic ATK_" + step + suffix
				+ " reach=" + TkGun.kineticSwordAttackRange(step);
	}

	private static List<Entity> intersectingTargets(PlayerEntity player,
			HitVolume volume) {
		List<Entity> targets = new ArrayList<>();
		Box search = player.getBoundingBox().expand(14.0D, 6.0D, 14.0D);
		for (Entity entity : player.getWorld().getOtherEntities(player, search,
				candidate -> candidate.isAlive() && candidate.canHit()
						&& !candidate.isSpectator()
						&& CombatTargeting.isTargetable(candidate))) {
			boolean hit = switch (volume.shape()) {
				case ORIENTED_BOX -> orientedBoxOverlaps(
						entity.getBoundingBox(), volume);
				case RADIAL -> radialOverlaps(
						entity.getBoundingBox(), volume);
			};
			if (hit) {
				targets.add(entity);
			}
		}
		return targets;
	}

	private static boolean radialOverlaps(Box bounds, HitVolume volume) {
		Vec3d offset = bounds.getCenter().subtract(volume.center());
		double radial = Math.hypot(offset.dotProduct(volume.right()),
				offset.dotProduct(volume.forward()));
		double halfHeight = (bounds.maxY - bounds.minY) * 0.5D;
		return radial <= volume.firstExtent()
				+ (bounds.maxX - bounds.minX) * 0.5D
				&& Math.abs(offset.dotProduct(volume.up()))
						<= volume.secondExtent() + halfHeight;
	}

	private static boolean orientedBoxOverlaps(Box bounds,
			HitVolume volume) {
		Vec3d aabbCenter = bounds.getCenter();
		Vec3d aabbHalf = new Vec3d(
				(bounds.maxX - bounds.minX) * 0.5D,
				(bounds.maxY - bounds.minY) * 0.5D,
				(bounds.maxZ - bounds.minZ) * 0.5D);
		Vec3d delta = aabbCenter.subtract(volume.center());
		Vec3d[] worldAxes = {
				new Vec3d(1.0D, 0.0D, 0.0D),
				new Vec3d(0.0D, 1.0D, 0.0D),
				new Vec3d(0.0D, 0.0D, 1.0D)
		};
		Vec3d[] boxAxes = {
				volume.right(), volume.up(), volume.forward()
		};
		for (Vec3d axis : worldAxes) {
			if (separatedOnAxis(delta, axis, aabbHalf, volume)) {
				return false;
			}
		}
		for (Vec3d axis : boxAxes) {
			if (separatedOnAxis(delta, axis, aabbHalf, volume)) {
				return false;
			}
		}
		for (Vec3d worldAxis : worldAxes) {
			for (Vec3d boxAxis : boxAxes) {
				Vec3d axis = worldAxis.crossProduct(boxAxis);
				if (axis.lengthSquared() > 1.0E-8D
						&& separatedOnAxis(delta, axis, aabbHalf, volume)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean separatedOnAxis(Vec3d delta, Vec3d axis,
			Vec3d aabbHalf, HitVolume volume) {
		double aabbRadius = aabbHalf.x * Math.abs(axis.x)
				+ aabbHalf.y * Math.abs(axis.y)
				+ aabbHalf.z * Math.abs(axis.z);
		double boxRadius = volume.firstExtent()
				* Math.abs(axis.dotProduct(volume.right()))
				+ volume.secondExtent()
				* Math.abs(axis.dotProduct(volume.up()))
				+ volume.thirdExtent()
				* Math.abs(axis.dotProduct(volume.forward()));
		return Math.abs(delta.dotProduct(axis))
				> aabbRadius + boxRadius + 1.0E-6D;
	}

	private static void drawOrientedBox(VertexConsumer lines,
			Matrix4f matrix, HitVolume volume, boolean intersects) {
		Vec3d[] corners = corners(volume);
		float red = intersects ? 1.0F : 0.15F;
		float green = intersects ? 0.12F : 1.0F;
		float blue = intersects ? 0.12F : 0.25F;
		for (int index = 0; index < BOX_EDGES.length; index += 2) {
			line(lines, matrix, corners[BOX_EDGES[index]],
					corners[BOX_EDGES[index + 1]],
					red, green, blue, 1.0F);
		}
	}

	private static Vec3d[] corners(HitVolume volume) {
		Vec3d x = volume.right().multiply(volume.firstExtent());
		Vec3d y = volume.up().multiply(volume.secondExtent());
		Vec3d z = volume.forward().multiply(volume.thirdExtent());
		Vec3d center = volume.center();
		return new Vec3d[]{
				center.subtract(x).subtract(y).subtract(z),
				center.add(x).subtract(y).subtract(z),
				center.add(x).add(y).subtract(z),
				center.subtract(x).add(y).subtract(z),
				center.subtract(x).subtract(y).add(z),
				center.add(x).subtract(y).add(z),
				center.add(x).add(y).add(z),
				center.subtract(x).add(y).add(z)
		};
	}

	private static void drawRadialVolume(VertexConsumer lines,
			Matrix4f matrix, HitVolume volume, boolean intersects) {
		float red = intersects ? 1.0F : 0.15F;
		float green = intersects ? 0.12F : 1.0F;
		float blue = intersects ? 0.12F : 0.25F;
		Vec3d bottom = volume.center().subtract(
				0.0D, volume.secondExtent(), 0.0D);
		Vec3d top = volume.center().add(
				0.0D, volume.secondExtent(), 0.0D);
		Vec3d[] previousBottom = {null};
		Vec3d[] previousTop = {null};
		for (int segment = 0; segment <= 32; segment++) {
			double angle = Math.PI * 2.0D * segment / 32.0D;
			Vec3d radial = volume.right().multiply(Math.cos(angle)
							* volume.firstExtent())
					.add(volume.forward().multiply(Math.sin(angle)
							* volume.firstExtent()));
			Vec3d currentBottom = bottom.add(radial);
			Vec3d currentTop = top.add(radial);
			if (previousBottom[0] != null) {
				line(lines, matrix, previousBottom[0], currentBottom,
						red, green, blue, 1.0F);
				line(lines, matrix, previousTop[0], currentTop,
						red, green, blue, 1.0F);
			}
			if ((segment & 7) == 0) {
				line(lines, matrix, currentBottom, currentTop,
						red, green, blue, 1.0F);
			}
			previousBottom[0] = currentBottom;
			previousTop[0] = currentTop;
		}
	}

	private static void drawWorldBox(VertexConsumer lines, Matrix4f matrix,
			Box box, float red, float green, float blue, float alpha) {
		Vec3d[] corners = {
				new Vec3d(box.minX, box.minY, box.minZ),
				new Vec3d(box.maxX, box.minY, box.minZ),
				new Vec3d(box.maxX, box.maxY, box.minZ),
				new Vec3d(box.minX, box.maxY, box.minZ),
				new Vec3d(box.minX, box.minY, box.maxZ),
				new Vec3d(box.maxX, box.minY, box.maxZ),
				new Vec3d(box.maxX, box.maxY, box.maxZ),
				new Vec3d(box.minX, box.maxY, box.maxZ)
		};
		for (int index = 0; index < BOX_EDGES.length; index += 2) {
			line(lines, matrix, corners[BOX_EDGES[index]],
					corners[BOX_EDGES[index + 1]],
					red, green, blue, alpha);
		}
	}

	private static void line(VertexConsumer buffer, Matrix4f matrix,
			Vec3d start, Vec3d end, float red, float green,
			float blue, float alpha) {
		float normalX = (float) (end.x - start.x);
		float normalY = (float) (end.y - start.y);
		float normalZ = (float) (end.z - start.z);
		float length = (float) Math.sqrt(normalX * normalX
				+ normalY * normalY + normalZ * normalZ);
		if (length <= 1.0E-5F) {
			normalY = 1.0F;
		} else {
			normalX /= length;
			normalY /= length;
			normalZ /= length;
		}
		vertex(buffer, matrix, start, red, green, blue, alpha,
				normalX, normalY, normalZ);
		vertex(buffer, matrix, end, red, green, blue, alpha,
				normalX, normalY, normalZ);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix,
			Vec3d point, float red, float green, float blue, float alpha,
			float normalX, float normalY, float normalZ) {
		buffer.vertex(matrix, (float) point.x, (float) point.y,
						(float) point.z)
				.color(red, green, blue, alpha)
				.normal(normalX, normalY, normalZ);
	}
}
