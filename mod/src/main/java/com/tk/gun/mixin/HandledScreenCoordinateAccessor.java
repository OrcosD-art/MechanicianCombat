package com.tk.gun.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenCoordinateAccessor {
	@Accessor("field_2776")
	int mechaniciancombat$getGuiX();

	@Accessor("field_2800")
	int mechaniciancombat$getGuiY();
}
