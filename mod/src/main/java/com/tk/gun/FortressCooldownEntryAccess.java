package com.tk.gun;

/** Duck interface applied to vanilla ItemCooldownManager.Entry by Mixin. */
public interface FortressCooldownEntryAccess {
	int tkgun$startTick();

	int tkgun$endTick();

	void tkgun$setWindow(int startTick, int endTick);
}
