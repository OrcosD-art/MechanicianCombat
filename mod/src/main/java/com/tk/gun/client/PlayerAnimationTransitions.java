package com.tk.gun.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.math.MathHelper;

/**
 * Small pose-level transition layer for PlayerAnimator clips.
 *
 * <p>PlayerAnimator's modifier API can fade within one ModifierLayer. The
 * wrappers here additionally blend against the fallback supplied by lower
 * layers, which is what makes a clip fade in from normal locomotion and fade
 * out back into it.
 */
public final class PlayerAnimationTransitions {
	public static final int QUICK_TICKS = 2;
	public static final int NORMAL_TICKS = 3;

	public static void play(ModifierLayer<IAnimation> layer,
			IAnimation next, int ticks) {
		if (layer == null) {
			return;
		}
		if (next == null) {
			clear(layer, ticks);
			return;
		}
		IAnimation current = layer.getAnimation();
		if (current == null || !current.isActive()) {
			layer.setAnimation(ticks > 0
					? new FadeIn(next, ticks) : next);
			return;
		}
		if (current == next) {
			return;
		}
		layer.setAnimation(ticks > 0
				? new Crossfade(current, next, ticks) : next);
	}

	public static void clear(ModifierLayer<IAnimation> layer, int ticks) {
		if (layer == null) {
			return;
		}
		IAnimation current = layer.getAnimation();
		if (current == null || !current.isActive()) {
			layer.setAnimation(null);
			return;
		}
		layer.setAnimation(ticks > 0
				? new FadeOut(current, ticks) : null);
	}

	private static float progress(int age, float tickDelta, int length) {
		if (length <= 0) {
			return 1.0F;
		}
		return Ease.INOUTSINE.invoke(MathHelper.clamp(
				(age + tickDelta) / length, 0.0F, 1.0F));
	}

	private static Vec3f blend(Vec3f from, Vec3f to, float amount) {
		return new Vec3f(
				MathHelper.lerp(amount,
						from.getX().floatValue(), to.getX().floatValue()),
				MathHelper.lerp(amount,
						from.getY().floatValue(), to.getY().floatValue()),
				MathHelper.lerp(amount,
						from.getZ().floatValue(), to.getZ().floatValue()));
	}

	private abstract static class TimedTransition implements IAnimation {
		protected final int length;
		protected int age;

		private TimedTransition(int length) {
			this.length = Math.max(1, length);
		}

		protected float progress(float tickDelta) {
			return PlayerAnimationTransitions.progress(age, tickDelta, length);
		}

	}

	private static final class FadeIn extends TimedTransition {
		private final IAnimation target;

		private FadeIn(IAnimation target, int length) {
			super(length);
			this.target = target;
		}

		@Override
		public void tick() {
			if (target.isActive()) {
				target.tick();
			}
			age++;
		}

		@Override
		public boolean isActive() {
			return age <= length || target.isActive();
		}

		@Override
		public Vec3f get3DTransform(String part, TransformType type,
				float tickDelta, Vec3f fallback) {
			Vec3f value = target.get3DTransform(
					part, type, tickDelta, fallback);
			return blend(fallback, value, progress(tickDelta));
		}

		@Override
		public void setupAnim(float tickDelta) {
			target.setupAnim(tickDelta);
		}

		@Override
		public FirstPersonMode getFirstPersonMode(float tickDelta) {
			return target.getFirstPersonMode(tickDelta);
		}

		@Override
		public FirstPersonConfiguration getFirstPersonConfiguration(
				float tickDelta) {
			return target.getFirstPersonConfiguration(tickDelta);
		}
	}

	private static final class FadeOut extends TimedTransition {
		private final IAnimation source;

		private FadeOut(IAnimation source, int length) {
			super(length);
			this.source = source;
		}

		@Override
		public void tick() {
			if (source.isActive()) {
				source.tick();
			}
			age++;
		}

		@Override
		public boolean isActive() {
			return age <= length;
		}

		@Override
		public Vec3f get3DTransform(String part, TransformType type,
				float tickDelta, Vec3f fallback) {
			Vec3f value = source.get3DTransform(
					part, type, tickDelta, fallback);
			return blend(value, fallback, progress(tickDelta));
		}

		@Override
		public void setupAnim(float tickDelta) {
			source.setupAnim(tickDelta);
		}

		@Override
		public FirstPersonMode getFirstPersonMode(float tickDelta) {
			return source.getFirstPersonMode(tickDelta);
		}

		@Override
		public FirstPersonConfiguration getFirstPersonConfiguration(
				float tickDelta) {
			return source.getFirstPersonConfiguration(tickDelta);
		}
	}

	private static final class Crossfade extends TimedTransition {
		private final IAnimation source;
		private final IAnimation target;

		private Crossfade(IAnimation source, IAnimation target, int length) {
			super(length);
			this.source = source;
			this.target = target;
		}

		@Override
		public void tick() {
			if (source.isActive()) {
				source.tick();
			}
			if (target.isActive()) {
				target.tick();
			}
			age++;
		}

		@Override
		public boolean isActive() {
			return age <= length || target.isActive();
		}

		@Override
		public Vec3f get3DTransform(String part, TransformType type,
				float tickDelta, Vec3f fallback) {
			Vec3f from = source.get3DTransform(
					part, type, tickDelta, fallback);
			Vec3f to = target.get3DTransform(
					part, type, tickDelta, fallback);
			return blend(from, to, progress(tickDelta));
		}

		@Override
		public void setupAnim(float tickDelta) {
			source.setupAnim(tickDelta);
			target.setupAnim(tickDelta);
		}

		@Override
		public FirstPersonMode getFirstPersonMode(float tickDelta) {
			return target.getFirstPersonMode(tickDelta);
		}

		@Override
		public FirstPersonConfiguration getFirstPersonConfiguration(
				float tickDelta) {
			return target.getFirstPersonConfiguration(tickDelta);
		}
	}

	private PlayerAnimationTransitions() {
	}
}
