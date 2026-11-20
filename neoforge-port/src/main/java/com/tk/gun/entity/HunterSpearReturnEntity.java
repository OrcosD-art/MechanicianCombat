package com.tk.gun.entity;

import com.tk.gun.TkGunServer;
import com.tk.gun.mixin.TridentEntityAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Loyalty-style return projectile that renders with the hunter spear item model. */
public final class HunterSpearReturnEntity extends TridentEntity {
	public HunterSpearReturnEntity(
			EntityType<? extends HunterSpearReturnEntity> type, World world) {
		super(type, world);
	}

	public HunterSpearReturnEntity(
			EntityType<? extends HunterSpearReturnEntity> type, World world,
			LivingEntity owner, ItemStack stack) {
		super(type, world);
		setOwner(owner);
		setStack(stack.copyWithCount(1));
		// Loyalty drives the return flight but must not become a real
		// enchantment, tooltip entry, or enchanted glint on the spear.
		getDataTracker().set(
				TridentEntityAccessor.mechaniciancombat$getLoyaltyData(), (byte) 3);
		getDataTracker().set(
				TridentEntityAccessor.mechaniciancombat$getEnchantedData(), false);
		((TridentEntityAccessor) (Object) this).mechaniciancombat$setDealtDamage(true);
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		return TkGunServer.HUNTER_SPEAR == null
				? ItemStack.EMPTY : new ItemStack(TkGunServer.HUNTER_SPEAR);
	}

	@Override
	public ItemStack getWeaponStack() {
		ItemStack stack = super.getWeaponStack().copy();
		stack.remove(DataComponentTypes.ENCHANTMENTS);
		stack.remove(DataComponentTypes.STORED_ENCHANTMENTS);
		return stack;
	}
}
