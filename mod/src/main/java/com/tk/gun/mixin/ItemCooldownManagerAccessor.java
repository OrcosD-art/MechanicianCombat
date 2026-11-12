package com.tk.gun.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {
	@Accessor("field_8024")
	Map<Item, ?> mechaniciancombat$entries();

	@Accessor("field_8025")
	int mechaniciancombat$tick();
}
