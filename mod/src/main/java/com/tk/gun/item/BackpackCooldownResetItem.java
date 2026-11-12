package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Development item that clears every cooldown owned by the mod. */
public final class BackpackCooldownResetItem extends Item {
	public BackpackCooldownResetItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient()) {
			com.tk.gun.CombatRollClientCooldown.clear(user);
		}
		if (!world.isClient() && user instanceof ServerPlayerEntity player) {
			TkGunServer.clearAllCooldowns(player);
		}
		return TypedActionResult.success(stack);
	}
}
