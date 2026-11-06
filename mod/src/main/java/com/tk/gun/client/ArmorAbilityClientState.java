package com.tk.gun.client;

import com.tk.gun.net.ArmorAbilityPayload;

/** Client mirror of armor-module charges and cooldowns. */
public final class ArmorAbilityClientState {
	private static boolean shieldInstalled;
	private static boolean totemInstalled;
	private static boolean soulChainInstalled;
	private static int shieldCharges;
	private static int shieldRechargeKills;
	private static int shieldCooldownTicks;
	private static int totemCooldownTicks;

	public static void apply(ArmorAbilityPayload payload) {
		shieldInstalled = payload.shieldInstalled();
		totemInstalled = payload.totemInstalled();
		soulChainInstalled = payload.soulChainInstalled();
		shieldCharges = payload.shieldCharges();
		shieldRechargeKills = payload.shieldRechargeKills();
		shieldCooldownTicks = payload.shieldCooldownTicks();
		totemCooldownTicks = payload.totemCooldownTicks();
	}

	public static void tick() {
		shieldCooldownTicks = Math.max(0, shieldCooldownTicks - 1);
		totemCooldownTicks = Math.max(0, totemCooldownTicks - 1);
	}

	public static void reset() {
		shieldInstalled = false;
		totemInstalled = false;
		soulChainInstalled = false;
		shieldCharges = 0;
		shieldRechargeKills = 0;
		shieldCooldownTicks = 0;
		totemCooldownTicks = 0;
	}

	public static boolean shieldInstalled() {
		return shieldInstalled;
	}

	public static boolean totemInstalled() {
		return totemInstalled;
	}

	public static boolean soulChainInstalled() {
		return soulChainInstalled;
	}

	public static int shieldCharges() {
		return shieldCharges;
	}

	public static int shieldCooldownTicks() {
		return shieldCooldownTicks;
	}

	public static int shieldRechargeKills() {
		return shieldRechargeKills;
	}

	public static int totemCooldownTicks() {
		return totemCooldownTicks;
	}

	private ArmorAbilityClientState() {
	}
}
