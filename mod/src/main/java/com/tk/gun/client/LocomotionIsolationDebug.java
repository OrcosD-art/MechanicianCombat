package com.tk.gun.client;

import com.tk.gun.TkGun;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary visual isolation harness for weapon locomotion poses.
 *
 * <p>Enabled only with {@code -Dmechaniciancombat.locomotionIsolation=true}.
 * Press F8 to cycle through progressively smaller contributions. This class
 * is inert in normal gameplay and must be removed after visual validation.</p>
 */
public final class LocomotionIsolationDebug {
	private static final boolean ENABLED = truthy(
			System.getProperty("mechaniciancombat.locomotionIsolation"));
	private static final String[] LABELS = {
			"完整实现",
			"无附加摆动",
			"仅禁用直接手部附加",
			"仅禁用胸/身父级附加",
			"仅禁用姿态插值",
			"无附加摆动 + 无姿态插值",
			"纯资源循环",
			"移除手臂桥接父级旋转",
			"仅移除胸腔桥接旋转",
			"仅移除身体桥接旋转",
			"移除桥接父级位移",
			"手臂仅保留局部变换",
			"Great_sword行走固定首帧"
	};
	private static int mode;
	private static boolean f8WasDown;
	private static boolean announced;

	public static void tick(MinecraftClient client) {
		if (!ENABLED) {
			return;
		}
		if (!announced) {
			announced = true;
			announce(client);
		}
		boolean f8Down = client.currentScreen == null
				&& InputUtil.isKeyPressed(client.getWindow().getHandle(),
						GLFW.GLFW_KEY_F8);
		if (f8Down && !f8WasDown) {
			mode = (mode + 1) % LABELS.length;
			announce(client);
		}
		f8WasDown = f8Down;
	}

	public static boolean secondaryMotionEnabled() {
		return !ENABLED || mode != 1 && mode != 5 && mode != 6;
	}

	public static boolean directArmSecondaryEnabled() {
		return !ENABLED || mode != 1 && mode != 2
				&& mode != 5 && mode != 6;
	}

	public static boolean parentSecondaryEnabled() {
		return !ENABLED || mode != 1 && mode != 3
				&& mode != 5 && mode != 6;
	}

	public static boolean poseSmoothingEnabled() {
		return !ENABLED || mode != 4 && mode != 5 && mode != 6;
	}

	public static boolean manualChestBobEnabled() {
		return !ENABLED || mode != 6;
	}

	/** Parent rotations emulated while flattening Gecko's chest hierarchy onto vanilla arms. */
	public static boolean armBridgeRotationEnabled(String bone) {
		if (!ENABLED) {
			return true;
		}
		if (mode == 7 || mode == 11) {
			return false;
		}
		if (mode == 8 && "chest".equals(bone)) {
			return false;
		}
		return mode != 9 || !"body".equals(bone);
	}

	public static boolean armBridgeParentPositionEnabled() {
		return !ENABLED || mode != 10 && mode != 11;
	}

	public static boolean freezeGreatSwordWalk() {
		return ENABLED && mode == 12;
	}

	private static void announce(MinecraftClient client) {
		String message = "[隔离 " + mode + "] " + LABELS[mode];
		TkGun.LOGGER.info("[mechaniciancombat-isolation] {}（F8 切换）", message);
		if (client.player != null) {
			client.player.sendMessage(Text.literal(message), true);
		}
	}

	private static boolean truthy(String value) {
		return value != null && (value.equals("1")
				|| value.equalsIgnoreCase("true")
				|| value.equalsIgnoreCase("yes"));
	}

	private LocomotionIsolationDebug() {
	}
}
