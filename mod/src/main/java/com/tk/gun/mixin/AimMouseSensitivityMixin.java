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
	private static Field mechaniciancombat$deltaX;
	private static Field mechaniciancombat$deltaY;

	@Inject(method = "method_1606", at = @At("HEAD"))
	private void mechaniciancombat$halveAimMouseDelta(double timeDelta, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !GunCamera.aiming(client)) {
			return;
		}
		try {
			// Mouse 是 intermediary 类；字段名不使用 named 名称，避免无 refmap 时错位。
			if (mechaniciancombat$deltaX == null || mechaniciancombat$deltaY == null) {
				Field x = Mouse.class.getDeclaredField("field_1789");
				Field y = Mouse.class.getDeclaredField("field_1787");
				x.setAccessible(true);
				y.setAccessible(true);
				mechaniciancombat$deltaX = x;
				mechaniciancombat$deltaY = y;
			}
			mechaniciancombat$deltaX.setDouble(this, mechaniciancombat$deltaX.getDouble(this) * 0.5D);
			mechaniciancombat$deltaY.setDouble(this, mechaniciancombat$deltaY.getDouble(this) * 0.5D);
		} catch (ReflectiveOperationException ignored) {
			// 输入表现失败不影响游戏运行。
		}
	}
}
