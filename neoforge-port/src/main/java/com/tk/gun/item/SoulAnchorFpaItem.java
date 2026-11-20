package com.tk.gun.item;

import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Hidden first-person Soul Anchor model controller. */
public final class SoulAnchorFpaItem extends Item implements GeoItem {
	private static final RawAnimation READY =
			RawAnimation.begin().thenPlayAndHold("ready");
	private static final RawAnimation THROW =
			RawAnimation.begin().thenPlayAndHold("throw");
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public SoulAnchorFpaItem(Settings settings) {
		super(settings);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "anchor", 0,
				state -> switch (SoulAnchorFpaRenderState.mode()) {
					case READY -> state.setAndContinue(READY);
					case THROW -> state.setAndContinue(THROW);
					case NONE -> PlayState.STOP;
				})
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
