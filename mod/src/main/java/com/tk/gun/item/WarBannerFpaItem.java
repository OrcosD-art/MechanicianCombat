package com.tk.gun.item;

import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Hidden runtime item whose sole purpose is driving the banner FPA controller. */
public final class WarBannerFpaItem extends Item implements GeoItem {
	private static final RawAnimation FPA =
			RawAnimation.begin().thenPlayAndHold("skill_flag_fpa");
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public WarBannerFpaItem(Settings settings) {
		super(settings);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "fpa", 0,
				state -> state.setAndContinue(FPA))
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
