package com.tk.gun.mixin;

import com.tk.gun.TkGunServer;
import com.tk.gun.item.BackpackAmmoData;
import com.tk.gun.item.BackpackLauncherRules;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Loyalty returns backpack-launched tridents to the occupied offhand first. */
@Mixin(TridentEntity.class)
public abstract class BackpackLoyaltyPickupMixin {
	@Inject(method = "method_34713(Lnet/minecraft/class_1657;)Z",
			at = @At("HEAD"), cancellable = true)
	private void mechaniciancombat$returnLoyaltyToLauncherStorage(
			net.minecraft.entity.player.PlayerEntity player,
			CallbackInfoReturnable<Boolean> cir) {
		TridentEntity trident = (TridentEntity) (Object) this;
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| trident.getOwner() != player
				|| !TkGunServer.isBackpackProjectile(trident)
				|| !BackpackLauncherRules.hasActiveLauncher(player)
				|| !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ItemStack weapon = trident.getWeaponStack();
		int loyalty = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT)
				.getEntry(Enchantments.LOYALTY)
				.map(entry -> EnchantmentHelper.getLevel(entry, weapon))
				.orElse(0);
		if (loyalty <= 0) {
			return;
		}

		ItemStack returned = weapon.copyWithCount(1);
		boolean accepted = BackpackAmmoData.storeIncoming(serverPlayer, returned);
		if (accepted) {
			TkGunServer.restoreBackpackChargeAfterLoyalty(serverPlayer);
			cir.setReturnValue(true);
		}
	}
}
