package com.tk.gun.api;

import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Immutable gameplay and render definition for one gun family.
 *
 * <p>The state machine and projectile rules remain shared. A new gun supplies its
 * own magazines, cadence, damage and GeckoLib assets while keeping the animation
 * names expected by {@code BowgunRig}.
 */
public record GunDefinition(
		Identifier id,
		int hipMagazineCapacity,
		int focusMagazineCapacity,
		int maxShotIndex,
		int fireTicks,
		int hipShotCycleTicks,
		int focusCycleStepTicks,
		int focusCycleMinTicks,
		float baseDamage,
		Identifier model,
		Identifier animation,
		Identifier gunTexture,
		int textureWidth,
		int textureHeight,
		String nameTranslationKey,
		String descriptionTranslationKey
) {
	public GunDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(animation, "animation");
		Objects.requireNonNull(gunTexture, "gunTexture");
		Objects.requireNonNull(nameTranslationKey, "nameTranslationKey");
		Objects.requireNonNull(descriptionTranslationKey, "descriptionTranslationKey");
		if (hipMagazineCapacity < 1 || focusMagazineCapacity < 1) {
			throw new IllegalArgumentException("magazine capacities must be positive");
		}
		if (maxShotIndex < 1 || fireTicks < 1 || hipShotCycleTicks < 1 || focusCycleMinTicks < 1) {
			throw new IllegalArgumentException("timing values must be positive");
		}
		if (focusCycleStepTicks < 0 || baseDamage <= 0.0F) {
			throw new IllegalArgumentException("cycle step must be non-negative and damage must be positive");
		}
		if (textureWidth < 1 || textureHeight < 1) {
			throw new IllegalArgumentException("geometry texture dimensions must be positive");
		}
		if (gunTexture.getPath().indexOf('/') < 0) {
			throw new IllegalArgumentException("gunTexture must be a full texture path");
		}
	}

	public static Builder builder(Identifier id) {
		return new Builder(id);
	}

	/** Example: {@code tkgun:new_gun_rig -> tkgun:geo/entity/new_gun_rig.geo.json}. */
	public Identifier modelResource() {
		return withPathPrefixSuffix(model, "geo/entity/", ".geo.json");
	}

	/** Example: {@code tkgun:new_gun_rig -> tkgun:animations/entity/new_gun_rig.animation.json}. */
	public Identifier animationResource() {
		return withPathPrefixSuffix(animation, "animations/entity/", ".animation.json");
	}

	/** First focused shot uses the hip cadence, then shortens by the configured step. */
	public int focusCycleTicks(int ramp) {
		int shortened = hipShotCycleTicks - Math.max(0, ramp) * focusCycleStepTicks;
		return Math.max(focusCycleMinTicks, shortened);
	}

	private static Identifier withPathPrefixSuffix(Identifier source, String prefix, String suffix) {
		String path = source.getPath();
		if (path.startsWith(prefix)) {
			path = path.substring(prefix.length());
		}
		if (path.endsWith(suffix)) {
			path = path.substring(0, path.length() - suffix.length());
		}
		return source.withPath(prefix + path + suffix);
	}

	/**
	 * Fluent constructor. Defaults are the current gun behavior and derive conventional
	 * GeckoLib resource IDs from the gun ID.
	 */
	public static final class Builder {
		private final Identifier id;
		private int hipMagazineCapacity = 6;
		private int focusMagazineCapacity = 10;
		private int maxShotIndex = 10;
		private int fireTicks = 30;
		private int hipShotCycleTicks = 12;
		private int focusCycleStepTicks = 5;
		private int focusCycleMinTicks = 5;
		private float baseDamage = 9.6F;
		private Identifier model;
		private Identifier animation;
		private Identifier gunTexture;
		private int textureWidth = 64;
		private int textureHeight = 64;
		private String nameTranslationKey;
		private String descriptionTranslationKey;

		private Builder(Identifier id) {
			this.id = Objects.requireNonNull(id, "id");
			this.model = id.withPath(id.getPath() + "_rig");
			this.animation = id.withPath(id.getPath() + "_rig");
			this.gunTexture = Identifier.of(id.getNamespace(),
					"textures/entity/" + id.getPath() + "_gun.png");
			this.nameTranslationKey = "item." + id.getNamespace() + "." + id.getPath();
			this.descriptionTranslationKey =
					"tooltip." + id.getNamespace() + "." + id.getPath() + ".description";
		}

		public Builder magazines(int hip, int focus) {
			this.hipMagazineCapacity = hip;
			this.focusMagazineCapacity = focus;
			return this;
		}

		public Builder shotAnimationMax(int maxShotIndex) {
			this.maxShotIndex = maxShotIndex;
			return this;
		}

		public Builder timing(int fireTicks, int hipCycleTicks, int focusStepTicks, int focusMinTicks) {
			this.fireTicks = fireTicks;
			this.hipShotCycleTicks = hipCycleTicks;
			this.focusCycleStepTicks = focusStepTicks;
			this.focusCycleMinTicks = focusMinTicks;
			return this;
		}

		public Builder baseDamage(float baseDamage) {
			this.baseDamage = baseDamage;
			return this;
		}

		public Builder assets(Identifier model, Identifier animation, Identifier gunTexture) {
			this.model = model;
			this.animation = animation;
			this.gunTexture = gunTexture;
			return this;
		}

		public Builder textureSize(int width, int height) {
			this.textureWidth = width;
			this.textureHeight = height;
			return this;
		}

		public Builder translations(String nameKey, String descriptionKey) {
			this.nameTranslationKey = nameKey;
			this.descriptionTranslationKey = descriptionKey;
			return this;
		}

		public GunDefinition build() {
			return new GunDefinition(id, hipMagazineCapacity, focusMagazineCapacity,
					maxShotIndex, fireTicks, hipShotCycleTicks, focusCycleStepTicks,
					focusCycleMinTicks, baseDamage, model, animation, gunTexture,
					textureWidth, textureHeight, nameTranslationKey, descriptionTranslationKey);
		}
	}
}
