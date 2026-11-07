package com.tk.gun.mixin;

import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets backpack missiles survive the unpowered cold-launch phase. */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
	@Accessor("field_7612")
	void tkgun$setLifeTime(int lifeTime);

	@Accessor("field_7612")
	int tkgun$getLifeTime();
}
