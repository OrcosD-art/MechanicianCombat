package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;

/** Shared server/client rules for the portable launcher's occupied offhand. */
public final class BackpackLauncherRules {
	private static final String VIRTUAL_BATTLE_BANNER =
			"mechaniciancombat_virtual_war_banner";

	public static boolean hasActiveLauncher(LivingEntity entity) {
		if (entity == null) {
			return false;
		}
		BackpackUpgradeData.LauncherType type = BackpackUpgradeData.launcherType(
				CombatBackpackItem.equippedStack(entity));
		return type == BackpackUpgradeData.LauncherType.PORTABLE
				|| type == BackpackUpgradeData.LauncherType.AUTO
				|| type == BackpackUpgradeData.LauncherType.SUMMON_ANTENNA
				|| type == BackpackUpgradeData.LauncherType.BATTLE_BANNER
				|| (type == BackpackUpgradeData.LauncherType.JUMP_THRUSTER
				&& BackpackUpgradeData.canUseMissileBox(
						CombatBackpackItem.equippedStack(entity)));
	}

	public static boolean isBattleBannerInstalled(LivingEntity entity) {
		return entity != null
				&& BackpackUpgradeData.launcherType(
						CombatBackpackItem.equippedStack(entity))
						== BackpackUpgradeData.LauncherType.BATTLE_BANNER;
	}

	public static ItemStack createBattleBannerIcon() {
		return new ItemStack(TkGunServer.WAR_BANNER);
	}

	public static boolean isVirtualBattleBanner(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.isOf(TkGunServer.WAR_BANNER)
				&& stack.getOrDefault(
						DataComponentTypes.CUSTOM_DATA,
						NbtComponent.DEFAULT)
				.copyNbt().getBoolean(VIRTUAL_BATTLE_BANNER);
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
