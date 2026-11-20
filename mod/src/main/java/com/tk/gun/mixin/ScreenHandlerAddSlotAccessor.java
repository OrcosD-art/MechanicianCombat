package com.tk.gun.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerAddSlotAccessor {
	@Invoker("method_7621")
	Slot mechaniciancombat$addSlot(Slot slot);
}
