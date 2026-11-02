package com.tk.gun.mixin;

import com.tk.gun.FortressCooldownEntryAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.class_1796$class_1797")
public abstract class ItemCooldownManagerEntryMixin implements FortressCooldownEntryAccess {
	@Mutable
	@Final
	@Shadow
	private int field_8028;

	@Mutable
	@Final
	@Shadow
	private int field_8027;

	@Override
	public int tkgun$startTick() {
		return field_8028;
	}

	@Override
	public int tkgun$endTick() {
		return field_8027;
	}

	@Override
	public void tkgun$setWindow(int startTick, int endTick) {
		this.field_8028 = startTick;
		this.field_8027 = endTick;
	}
}
