package com.tk.gun.mixin;

import com.tk.gun.CombatTargeting;
import com.tk.gun.item.BackpackFireworkData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents only marked backpack rockets from damaging their owner. */
@Mixin(FireworkRocketEntity.class)
public abstract class BackpackFireworkSelfDamageMixin {
	@Redirect(method = "method_7475",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/class_1309;method_5643(Lnet/minecraft/class_1282;F)Z"))
	private boolean tkgun$skipBackpackOwnerDamage(LivingEntity target, DamageSource source, float amount) {
		FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
		if (CombatTargeting.isGolem(target)) {
			return false;
		}
		if (BackpackFireworkData.preventsSelfDamage(rocket.getStack())
				&& target == rocket.getOwner()) {
			return false;
		}
		return target.damage(source, amount);
	}
}
