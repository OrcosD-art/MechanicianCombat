package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Deployable spear that summons a fixed hunter turret on right click. */
public final class MechanicianHunterSpearItem extends Item implements GeoItem {
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public MechanicianHunterSpearItem(Item.Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient()) {
			return TypedActionResult.success(stack);
		}
		if (!(user instanceof ServerPlayerEntity player)
				|| !TkGunServer.deployHunterTurret(player, stack)) {
			return TypedActionResult.fail(stack);
		}
		stack.decrement(1);
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context,
			List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("tooltip.mechaniciancombat.hunter_spear.deploy")
				.formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.mechaniciancombat.hunter_spear.return")
				.formatted(Formatting.GRAY));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// The deployment prop is driven directly by HunterSpearHeldRenderer.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
