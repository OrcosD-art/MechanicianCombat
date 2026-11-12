package com.tk.gun;

/** Duck interface applied to vanilla ItemCooldownManager.Entry by Mixin. */
public interface FortressCooldownEntryAccess {
	int mechaniciancombat$startTick();

	int mechaniciancombat$endTick();

	void mechaniciancombat$setWindow(int startTick, int endTick);
}
