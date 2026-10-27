package com.tk.gun.mixin;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 熔铁矢三叉戟的下坠约为原版三分之一（0.05 → 0.017，四舍五入）。 */
@Mixin(PersistentProjectileEntity.class)
public abstract class TridentGravityMixin {
	@Inject(method = "method_7490()D", at = @At("RETURN"), cancellable = true)
	private void tkgun$reduceMoltenTridentGravity(CallbackInfoReturnable<Double> cir) {
		if (TkGunServer.isBackpackProjectile(
				(net.minecraft.entity.Entity) (Object) this)) {
			if ((Object) this instanceof TridentEntity trident
					&& TkGunServer.BOLT != null
					&& trident.getWeaponStack().isOf(TkGunServer.BOLT)) {
				cir.setReturnValue(0.017D);
			} else {
				cir.setReturnValue(cir.getReturnValueD() / 3.0D);
			}
			return;
		}
		if ((Object) this instanceof com.tk.gun.entity.MoltenTridentEntity molten
				&& molten.isGunFired()) {
			cir.setReturnValue(0.0D);              // 枪口发射：零重力
		} else if ((Object) this instanceof TridentEntity trident
				&& TkGunServer.BOLT != null
				&& trident.getWeaponStack().isOf(TkGunServer.BOLT)) {
			cir.setReturnValue(0.017D);            // 手持投掷：原版约 1/3 下坠
		}
	}
}
