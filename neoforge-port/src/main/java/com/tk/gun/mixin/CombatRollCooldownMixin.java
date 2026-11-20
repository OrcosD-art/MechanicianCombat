package com.tk.gun.mixin;

import com.tk.gun.CombatRollCooldownAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for the optional Combat Roll client cooldown manager. */
@Mixin(targets = "net.combat_roll.internals.RollManager", remap = false)
public interface CombatRollCooldownMixin extends CombatRollCooldownAccess {
	@Accessor(value = "maxRolls", remap = false)
	int mechaniciancombat$maxRolls();

	@Accessor(value = "timeSinceLastRoll", remap = false)
	void mechaniciancombat$setTimeSinceLastRoll(int ticks);

	@Accessor(value = "currentCooldownProgress", remap = false)
	void mechaniciancombat$setCooldownProgress(float progress);

	@Accessor(value = "availableRolls", remap = false)
	void mechaniciancombat$setAvailableRolls(int rolls);
}
