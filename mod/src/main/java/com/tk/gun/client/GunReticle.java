package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * **弹着点圈圈**（用户 2026-10-14 明确语义）："这个圆圈是用来表示**实际子弹落点**的，
 * 就是枪口直线射出去映射的位置；长按右键这个圆圈会去稳定到玩家准心位置 —— 就是瞄准过程"。
 *
 * <p>所以它画的是 {@link GunCamera#impactPoint()} —— 从枪口沿枪管方向打出去的**第一个撞到的东西**
 * （方块或生物）投影到屏幕上的位置，不是枪口本身。平时枪管斜着端在身侧 → 圈落在远处的墙上/地上；
 * 长按右键身体和枪管转向视角 → 圈会**收敛到准星上**，那就是"瞄准"的可视化。
 *
 * <p>⚠️ 屏幕正中的 **`- . -` 准星不在这里画** —— 那是 ShoulderSurfing 的 crosshair
 * （`crosshair_type = STATIC` 就是它画的原版式样）。我第一版自己画了一个，结果和它的叠在一起，
 * 用户看到的就是"`. ` 没在两横正中间"（2026-10-14 用户原话）。现在只画它画不出来的枪口圈。
 *
 * <p>第一人称不画（交回原版准星）。
 */
public final class GunReticle {
	/**
	 * 枪口圈贴图。两条硬规矩（2026-10-14 用户骂过之后才定的）：
	 * <ol>
	 *   <li>**PNG 必须是 256x256 的画布**（圈画在左上角 {@link #RING_SIZE} 那块）：
	 *       `DrawContext.drawTexture(tex, x, y, u, v, w, h)` 这个 7 参数重载**假定贴图 256x256**，
	 *       用 17x17 的图只会取到左上角 0.6% → 屏幕上什么都不显示
	 *       （用户："从来没看到过你给的圆圈，我以为你没做"）。</li>
	 *   <li>**画风必须跟整合包准心一致**（用户："你这画风完全跟我们现有的准心风格完全不一样，
	 *       自己去看资源包 mod 看我们的准心什么样"）。准心在
	 *       `resourcepacks/THE KINGDOM/assets/minecraft/textures/gui/sprites/hud/crosshair.png`：
	 *       **15x15、纯白、1 像素细线、无描边** → 这个圈照同一套规格（15x15 的 1 像素细白圈）。</li>
	 * </ol>
	 * 生成脚本 `_mech_work\make_reticle.py`。
	 */
	private static final int ARROW_SIZE = 15;
	private static final float HOTBAR_WIDTH = 182.0F;
	private static final int RETICLE_TEXTURE_SIZE = 64;
	private static final int RETICLE_HALF_SIZE = RETICLE_TEXTURE_SIZE / 2;
	private static final float RETICLE_VISUAL_HALF_SIZE = 17.0F;
	private static final float SCREEN_JUMP_SNAP_DISTANCE = 40.0F;
	private static final float CIRCLE_SOFT_BORDER = 6.0F;
	private static final int HIT_MARKER_INNER = 18;
	private static final int HIT_CROSS_BAR_LENGTH = 3;
	private static final long HIT_MARKER_NANOS = 180_000_000L;
	private static final long AIM_TRANSITION_NANOS = 200_000_000L;
	private static final Identifier[] AIM_FRAMES = {
			Identifier.of("mechaniciancombat",
					"textures/gui/reticle/frame_0.png"),
			Identifier.of("mechaniciancombat",
					"textures/gui/reticle/frame_1.png"),
			Identifier.of("mechaniciancombat",
					"textures/gui/reticle/frame_2.png"),
			Identifier.of("mechaniciancombat",
					"textures/gui/reticle/frame_3.png"),
			Identifier.of("mechaniciancombat",
					"textures/gui/reticle/frame_4.png")
	};
	private static long hitMarkerUntil;
	private static long lastTransitionNanos;
	private static float transitionProgress;
	private static long interpolationTick = Long.MIN_VALUE;
	private static boolean interpolationValid;
	private static float interpolationFromX;
	private static float interpolationFromY;
	private static float interpolationToX;
	private static float interpolationToY;
	private static float lastRawX;
	private static float lastRawY;
	private static int reticleScreenX;
	private static int reticleScreenY;
	private static boolean reticleVisible;

	public static void triggerHitMarker() {
		hitMarkerUntil = System.nanoTime() + HIT_MARKER_NANOS;
	}

	public static void render(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden
				|| client.currentScreen != null) {
			lastTransitionNanos = 0L;
			resetInterpolation();
			return;
		}
		reticleVisible = false;
		if (!TkGun.holdingGun(client.player)) {
			transitionProgress = 0.0F;
			lastTransitionNanos = 0L;
			resetInterpolation();
			return;
		}
		boolean aiming = GunCamera.aiming(client);
		GunClientState.Entry state =
				GunClientState.get(client.player.getId());
		boolean deploying = isDeploying(state);
		updateTransition(aiming && !deploying);
		boolean focused = GunClientState.focusish(client.player.getId());
		if (deploying) {
			resetInterpolation();
			return;
		}
		if (client.options.getPerspective().isFirstPerson()) {
			resetInterpolation();
			// 第一人称不画落点圈；越界方向箭头只在专注状态下提示，腰射不显示。
			if (aiming && GunCamera.aimOutsideCone()
					&& focused) {
				drawLostDirectionArrow(context, GunCamera.aimOutsideSide());
			}
			return;
		}
		// Outside crouch-fire focus, the reticle is only shown while the
		// player is actively aiming with the use key.
		// Keep the reticle through AIM_HOLD, FIRE and every other active
		// crouch-fire state. Only the deployment transitions are hidden above.
		if (!aiming && !focused) {
			resetInterpolation();
			return;
		}
		if (aiming && GunCamera.aimOutsideCone()) {
			resetInterpolation();
			if (focused) {
				drawLostDirectionArrow(context, GunCamera.aimOutsideSide());
			}
			return;
		}
		drawMuzzleReticle(context, tickDelta);
		drawHitCrossFlash(context);
	}

	private static boolean isDeploying(GunClientState.Entry state) {
		if (state == null) {
			return false;
		}
		return state.state == TkGun.State.AIM
				|| state.state == TkGun.State.AIM_FAST
				|| state.state == TkGun.State.AIM_ROLL
				|| state.state == TkGun.State.AIM_SLIDE;
	}

	private static void resetInterpolation() {
		interpolationTick = Long.MIN_VALUE;
		interpolationValid = false;
	}

	private static void updateTransition(boolean aiming) {
		long now = System.nanoTime();
		if (lastTransitionNanos == 0L) {
			lastTransitionNanos = now;
			return;
		}
		long elapsed = Math.max(0L,
				Math.min(100_000_000L, now - lastTransitionNanos));
		lastTransitionNanos = now;
		float target = aiming ? 1.0F : 0.0F;
		float step = (float) elapsed / AIM_TRANSITION_NANOS;
		if (transitionProgress < target) {
			transitionProgress = Math.min(target,
					transitionProgress + step);
		} else if (transitionProgress > target) {
			transitionProgress = Math.max(target,
					transitionProgress - step);
		}
	}

	private static int currentFrame() {
		return Math.max(0, Math.min(AIM_FRAMES.length - 1,
				(int) Math.floor(transitionProgress
						* (AIM_FRAMES.length - 1))));
	}

	/** Four 3-pixel bars flash once outside the X reticle on a confirmed hit. */
	private static void drawHitCrossFlash(DrawContext context) {
		if (!reticleVisible || System.nanoTime() >= hitMarkerUntil) {
			return;
		}
		int near = HIT_MARKER_INNER;
		int far = near + HIT_CROSS_BAR_LENGTH;
		int white = 0xFFFFFFFF;

		context.fill(reticleScreenX, reticleScreenY - far,
				reticleScreenX + 1, reticleScreenY - near + 1, white);
		context.fill(reticleScreenX, reticleScreenY + near,
				reticleScreenX + 1, reticleScreenY + far + 1, white);
		context.fill(reticleScreenX - far, reticleScreenY,
				reticleScreenX - near + 1, reticleScreenY + 1, white);
		context.fill(reticleScreenX + near, reticleScreenY,
				reticleScreenX + far + 1, reticleScreenY + 1, white);
	}

	/**
	 * 准星超出枪口左右 25° 扇区：落点圈丢失，只在准星左右画一个 15×15、1px 白色箭头，
	 * 和原版/资源包准星保持同一套尺寸与线条效果。
	 */
	private static void drawLostDirectionArrow(DrawContext ctx, int side) {
		if (side == 0) {
			return;
		}
		int cx = ctx.getScaledWindowWidth() / 2;
		int cy = ctx.getScaledWindowHeight() / 2;
		int x = side < 0 ? cx - 34 : cx + 19;
		int y = cy - ARROW_SIZE / 2;
		int white = 0xFFFFFFFF;
		// 7 段镜像斜线组成一个 15×15 的 < / >，每段都是 1px。
		for (int i = 0; i <= ARROW_SIZE / 2; i++) {
			int px = side < 0 ? x + ARROW_SIZE / 2 - i : x + ARROW_SIZE / 2 + i;
			ctx.fill(px, y + i, px + 1, y + i + 1, white);
			ctx.fill(px, y + ARROW_SIZE - 1 - i,
					px + 1, y + ARROW_SIZE - i, white);
		}
	}

	/** 把**弹着点**世界坐标投影到屏幕上画圈（在相机身后 / 太靠边就不画）。 */
	private static void drawMuzzleReticle(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d muzzle = GunCamera.impactPoint();
		if (muzzle == null) {
			return;
		}
		Camera camera = client.gameRenderer.getCamera();
		// FOV 要用**跟画面同一个**（含瞄准放大），否则圈圈会偏
		double fov = client.options.getFov().getValue() * GunCamera.fovScale();
		Matrix4f proj = client.gameRenderer.getBasicProjectionMatrix(fov);
		// 视图矩阵**照抄 WorldRenderer 里那一套**：Rx(pitch) * Ry(yaw+180)。
		// ⚠️ 别用 `camera.getRotation().conjugate()` —— 那个四元数是给公告板用的，
		//    拿来做屏幕投影会差一个轴向+180°（2026-10-14 实测：圈圈根本不出现）。
		//    点已经是"相对相机"的，所以不用再平移。
		Matrix4f viewProj = new Matrix4f(proj).mul(new Matrix4f()
				.rotateX((float) Math.toRadians(camera.getPitch()))
				.rotateY((float) Math.toRadians(camera.getYaw() + 180.0F)));
		Vec3d rel = muzzle.subtract(camera.getPos());
		Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
		viewProj.transform(v);
		if (VERBOSE && (debugTicks++ % 40) == 0) {
			TkGun.LOGGER.info("[mechaniciancombat] 枪口圈投影：枪口=({}, {}, {}) 相机=({}, {}, {}) W={} NDC=({}, {})",
					r(muzzle.x), r(muzzle.y), r(muzzle.z), r(camera.getPos().x), r(camera.getPos().y),
					r(camera.getPos().z), r(v.w()), r(v.x() / Math.max(1.0E-6F, v.w())),
					r(v.y() / Math.max(1.0E-6F, v.w())));
		}
		if (v.w() <= 1.0E-4F) {
			return;                                  // 在相机背后（w<=0 = 不可见）
		}
		float ndcX = v.x() / v.w();
		float ndcY = v.y() / v.w();
		float centerX = ctx.getScaledWindowWidth() * 0.5F;
		float centerY = ctx.getScaledWindowHeight() * 0.5F;
		float projectedX =
				(ndcX * 0.5F + 0.5F) * ctx.getScaledWindowWidth();
		float projectedY =
				(0.5F - ndcY * 0.5F) * ctx.getScaledWindowHeight();
		long worldTick = client.world.getTime();
		if (!interpolationValid
				|| worldTick != interpolationTick) {
			interpolationFromX = interpolationValid
					? lastRawX : projectedX;
			interpolationFromY = interpolationValid
					? lastRawY : projectedY;
			interpolationTick = worldTick;
			interpolationValid = true;
		}
		interpolationToX = projectedX;
		interpolationToY = projectedY;
		lastRawX = projectedX;
		lastRawY = projectedY;

		float jumpX = interpolationToX - interpolationFromX;
		float jumpY = interpolationToY - interpolationFromY;
		boolean snap = jumpX * jumpX + jumpY * jumpY
				> SCREEN_JUMP_SNAP_DISTANCE * SCREEN_JUMP_SNAP_DISTANCE;
		float blend = snap ? 1.0F
				: MathHelper.clamp(tickDelta, 0.0F, 1.0F);
		float displayX = snap ? interpolationToX
				: MathHelper.lerp(blend,
						interpolationFromX, interpolationToX);
		float displayY = snap ? interpolationToY
				: MathHelper.lerp(blend,
						interpolationFromY, interpolationToY);
		if (snap) {
			interpolationFromX = interpolationToX;
			interpolationFromY = interpolationToY;
		}

		float dx = displayX - centerX;
		float dy = displayY - centerY;
		float hardRadius = Math.max(0.0F,
				Math.min(HOTBAR_WIDTH * 0.5F,
						Math.min(centerX, centerY))
						- RETICLE_VISUAL_HALF_SIZE);
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		float softStart = Math.max(0.0F,
				hardRadius - CIRCLE_SOFT_BORDER);
		if (distance > softStart && distance > 1.0E-5F) {
			float excess = distance - softStart;
			float limited = softStart + CIRCLE_SOFT_BORDER
					* (1.0F - (float) Math.exp(
							-excess / CIRCLE_SOFT_BORDER));
			float scale = limited / distance;
			dx *= scale;
			dy *= scale;
		}
		float sx = centerX + dx;
		float sy = centerY + dy;
		float drawX = sx - RETICLE_HALF_SIZE;
		float drawY = sy - RETICLE_HALF_SIZE;
		int baseX = (int) Math.floor(drawX);
		int baseY = (int) Math.floor(drawY);
		ctx.getMatrices().push();
		try {
			ctx.getMatrices().translate(drawX - baseX, drawY - baseY, 0.0F);
			ctx.drawTexture(AIM_FRAMES[currentFrame()],
					baseX, baseY,
					0, 0, RETICLE_TEXTURE_SIZE, RETICLE_TEXTURE_SIZE,
					RETICLE_TEXTURE_SIZE, RETICLE_TEXTURE_SIZE);
		} finally {
			ctx.getMatrices().pop();
		}
		reticleScreenX = Math.round(sx);
		reticleScreenY = Math.round(sy);
		reticleVisible = true;
	}

	private static int debugTicks;
	private static final boolean VERBOSE = System.getProperty("mechaniciancombat.demo") != null;

	private static double r(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	private GunReticle() {
	}
}
