package com.tk.gun.mixin;

import com.tk.gun.client.GunCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 枪械视角 FOV：右键瞄准时放大，战旗施法时拉远。
 *
 * <p>做法：在 {@code GameRenderer#getFov} 的返回值上乘一个系数（{@link GunCamera#fovScale()}：
 * 不瞄准且未施法 = 1、瞄准时平滑到 {@link GunCamera#AIM_FOV}，施法时平滑到
 * {@link GunCamera#BANNER_FOV}）。改在 RETURN 上乘，所以原版的疾跑/药水/死亡效果都照旧。
 *
 * <p>⚠️ 没有 refmap → intermediary：{@code GameRenderer#getFov(Camera, float, boolean)} = {@code method_3196}。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererZoomMixin {
	@Inject(method = "method_3196(Lnet/minecraft/class_4184;FZ)D", at = @At("RETURN"), cancellable = true)
	private void mechaniciancombat$aimZoom(Camera camera, float tickDelta, boolean changingFov,
							   CallbackInfoReturnable<Double> cir) {
		// ⚠️ 平滑必须**按帧**做（这里每帧都会跑）：原来是在客户端 tick（20 Hz）里插值，
		//    结果放大一卡一卡（用户 2026-10-14："视角放缩一卡一卡的 不丝滑"）。
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			GunCamera.advanceZoom(client, client.getRenderTickCounter().getLastFrameDuration());
		}
		float scale = GunCamera.fovScale();
		if (VERBOSE && (frame++ % 400) == 0) {
			com.tk.gun.TkGun.LOGGER.info("[mechaniciancombat] 放大逐帧探针：帧={} FOV系数={} 返回={}",
					frame, Math.round(scale * 1000) / 1000.0, Math.round(cir.getReturnValueD() * 100.0) / 100.0);
		}
		if (Math.abs(scale - 1.0F) > 0.001F) {
			cir.setReturnValue(cir.getReturnValueD() * (double) scale);
		}
		GunCamera.recordRenderedFov(cir.getReturnValueD());
	}

	private static int frame;
	private static final boolean VERBOSE = System.getProperty("mechaniciancombat.demo") != null;
}
