package com.tk.gun.mixin;

import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/class_481$class_483")
public interface CreativeScreenHandlerAccessor {
	@Accessor("field_29349")
	ScreenHandler mechaniciancombat$getParent();
}
