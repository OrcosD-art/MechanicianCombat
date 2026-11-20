package com.tk.gun.mixin;

import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandBlockEntityAccessor {
	@Accessor("field_11882")
	DefaultedList<ItemStack> mechaniciancombat$getInventory();

	@Accessor("field_11878")
	int mechaniciancombat$getBrewTime();

	@Accessor("field_11878")
	void mechaniciancombat$setBrewTime(int ticks);

	@Accessor("field_11885")
	int mechaniciancombat$getFuel();

	@Accessor("field_11885")
	void mechaniciancombat$setFuel(int fuel);
}
