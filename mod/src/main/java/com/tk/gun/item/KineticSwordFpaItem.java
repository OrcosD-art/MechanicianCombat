package com.tk.gun.item;

import com.tk.gun.client.KineticSwordFirstPersonState;
import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Hidden controller item used only by the Kinetic Sword first-person renderer. */
public final class KineticSwordFpaItem extends Item implements GeoItem {
	private static final Map<String, RawAnimation> RAW =
			new ConcurrentHashMap<>();
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public KineticSwordFpaItem(Settings settings) {
		super(settings);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "sword_fpa", 0,
				state -> {
					KineticSwordFirstPersonState.animationTick(
							state.getAnimationTick());
					String name = KineticSwordFirstPersonState.animationName();
					if (name == null || name.isBlank()) {
						return PlayState.STOP;
					}
					return state.setAndContinue(rawFor(name));
				})
				.setAnimationSpeedHandler(item ->
						KineticSwordFirstPersonState.animationSpeed()));
	}

	private static RawAnimation rawFor(String name) {
		return RAW.computeIfAbsent(name, key -> isLooping(key)
				? RawAnimation.begin().thenLoop(key)
				: RawAnimation.begin().thenPlayAndHold(key));
	}

	private static boolean isLooping(String name) {
		return "idle".equals(name)
				|| "GS_run".equals(name)
				|| name.startsWith("GS_walk_");
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
