package com.tk.gun;

/** Duck interface for Combat Roll's client-only cooldown state fields. */
public interface CombatRollCooldownAccess {
	int mechaniciancombat$maxRolls();

	void mechaniciancombat$setTimeSinceLastRoll(int ticks);

	void mechaniciancombat$setCooldownProgress(float progress);

	void mechaniciancombat$setAvailableRolls(int rolls);
}
