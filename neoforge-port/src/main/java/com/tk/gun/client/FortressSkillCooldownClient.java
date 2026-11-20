package com.tk.gun.client;

import com.tk.gun.net.FortressSkillCooldownPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

public final class FortressSkillCooldownClient {
	private static Item trackedItem;
	private static int remainingAtSync;
	private static float progressAtSync;
	private static long syncedWorldTime;

	public static void apply(FortressSkillCooldownPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			clear();
			return;
		}
		float currentProgress = progress(trackedItem, 0.0F);
		if (Float.isNaN(currentProgress)) {
			currentProgress = client.player.getItemCooldownManager()
					.getCooldownProgress(client.player.getMainHandStack().getItem(), 0.0F);
		}
		trackedItem = client.player.getMainHandStack().getItem();
		remainingAtSync = Math.max(0, payload.remainingTicks());
		progressAtSync = Math.max(0.0F, Math.min(1.0F, currentProgress));
		syncedWorldTime = client.world.getTime();
		if (remainingAtSync <= 0) {
			client.player.getItemCooldownManager().remove(trackedItem);
		}
	}

	/** Called by the client ItemCooldownManager mixin. */
	public static float progress(Item item, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (item != trackedItem || client.world == null || remainingAtSync <= 0) {
			return Float.NaN;
		}
		double elapsed = client.world.getTime() + tickDelta - syncedWorldTime;
		if (elapsed >= remainingAtSync) {
			// Release the override for the next skill use so the following
			// vanilla cooldown starts visible at its full 30-second progress.
			clear();
			return 0.0F;
		}
		return Math.max(0.0F, Math.min(1.0F,
				(float) (progressAtSync * (remainingAtSync - elapsed) / remainingAtSync)));
	}

	public static void clear() {
		trackedItem = null;
		remainingAtSync = 0;
		progressAtSync = 0.0F;
		syncedWorldTime = 0L;
	}

	private FortressSkillCooldownClient() {
	}
}
