package com.tk.gun.item;

import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Hidden controller item for hunter-anchor and backpack-turret first-person props. */
public final class CombatPropFpaItem extends Item implements GeoItem {
	private static final RawAnimation HUNTER_ANCHOR =
			RawAnimation.begin().thenPlayAndHold("fpa");
	private static final RawAnimation READY =
			RawAnimation.begin().thenPlayAndHold("ready");
	private static final RawAnimation AIM =
			RawAnimation.begin().thenLoop("aim");
	private static final RawAnimation CD =
			RawAnimation.begin().thenPlayAndHold("CD");
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public CombatPropFpaItem(Settings settings) {
		super(settings);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "fpa", 0,
				state -> switch (CombatPropFpaRenderState.mode()) {
					case HUNTER_ANCHOR -> state.setAndContinue(HUNTER_ANCHOR);
					case READY -> state.setAndContinue(READY);
					case AIM -> state.setAndContinue(AIM);
					case CD -> state.setAndContinue(CD);
					case NONE -> PlayState.STOP;
				})
				.setAnimationSpeedHandler(item -> 1.0D));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
