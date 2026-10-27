package com.tk.gun.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 让自定义熔铁弩矢实体能初始化三叉戟的忠诚/附魔光效数据。 */
@Mixin(TridentEntity.class)
public interface TridentEntityAccessor {
	@Invoker("method_59960")
	byte tkgun$getLoyalty(ItemStack stack);

	@Accessor("field_7647")
	static TrackedData<Byte> tkgun$getLoyaltyData() {
		throw new AssertionError();
	}

	@Accessor("field_21514")
	static TrackedData<Boolean> tkgun$getEnchantedData() {
		throw new AssertionError();
	}

	@Accessor("field_7648")
	void tkgun$setDealtDamage(boolean value);
}
