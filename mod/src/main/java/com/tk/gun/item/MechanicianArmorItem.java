package com.tk.gun.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Netherite-stat armor that can only be equipped over the undersuit. */
public final class MechanicianArmorItem extends ArmorItem implements GeoItem {
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");
	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);
	private final MechanicianArmorPiece piece;

	public MechanicianArmorItem(MechanicianArmorPiece piece, Type type,
								Settings settings) {
		super(ArmorMaterials.NETHERITE, type, settings.maxCount(1));
		this.piece = piece;
	}

	public MechanicianArmorPiece piece() {
		return piece;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user,
											Hand hand) {
		if (!MechanicianUndersuitItem.isEquipped(user)) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable(
						"message.tkgun.requires_undersuit"), true);
			}
			return TypedActionResult.fail(user.getStackInHand(hand));
		}
		return super.use(world, user, hand);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context,
							  List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("tooltip.tkgun.mechanician_"
				+ piece.modelName()));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "armor", 0,
				state -> state.setAndContinue(IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
