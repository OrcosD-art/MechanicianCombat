package com.tk.gun.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;

/** Marks only the firework rockets launched by the combat backpack. */
public final class BackpackFireworkData {
	private static final String NO_SELF_DAMAGE = "tkgun_backpack_no_self_damage";

	public static void markNoSelfDamage(ItemStack rocket) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, rocket,
				nbt -> nbt.putBoolean(NO_SELF_DAMAGE, true));
	}

	public static boolean preventsSelfDamage(ItemStack rocket) {
		return rocket.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
				.copyNbt().getBoolean(NO_SELF_DAMAGE);
	}

	private BackpackFireworkData() {
	}
}
