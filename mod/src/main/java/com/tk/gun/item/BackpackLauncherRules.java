package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;

/** Shared server/client rules for the portable launcher's occupied offhand. */
public final class BackpackLauncherRules {
	public static boolean hasActiveLauncher(LivingEntity entity) {
		return entity != null
				&& BackpackUpgradeData.canUseLauncher(
						CombatBackpackItem.equippedStack(entity));
	}

	public static boolean isLaunchable(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.isOf(Items.FIREWORK_ROCKET)
				|| stack.isOf(Items.TIPPED_ARROW)
				|| stack.isOf(Items.WIND_CHARGE)
				|| isTridentLike(stack);
	}

	public static boolean isTridentLike(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& (stack.getItem() instanceof TridentItem
				|| (TkGunServer.BOLT != null && stack.isOf(TkGunServer.BOLT)));
	}

	private BackpackLauncherRules() {
	}
}
