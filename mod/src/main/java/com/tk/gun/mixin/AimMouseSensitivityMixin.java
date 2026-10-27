package com.tk.gun.mixin;

import com.tk.gun.client.GunCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** 瞄准状态下把每帧鼠标位移减半，不改玩家保存的鼠标灵敏度。 */
@Mixin(Mouse.class)
public abstract class AimMouseSensitivityMixin {
	private static Field tkgun$deltaX;
	private static Field tkgun$deltaY;

	@Inject(method = "method_1606", at = @At("HEAD"))
	private void tkgun$halveAimMouseDelta(double timeDelta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !GunCamera.aiming(client)) {
			return;
		}
		try {
			// Mouse 是 intermediary 类；字段名不使用 named 名称，避免无 refmap 时错位。
			if (tkgun$deltaX == null || tkgun$deltaY == null) {
				Field x = Mouse.class.getDeclaredField("field_1789");
				Field y = Mouse.class.getDeclaredField("field_1787");
				x.setAccessible(true);
				y.setAccessible(true);
				tkgun$deltaX = x;
				tkgun$deltaY = y;
			}
			tkgun$deltaX.setDouble(this, tkgun$deltaX.getDouble(this) * 0.5D);
			tkgun$deltaY.setDouble(this, tkgun$deltaY.getDouble(this) * 0.5D);
		} catch (ReflectiveOperationException ignored) {
			// 输入表现失败不影响游戏运行。
		}
	}
}
