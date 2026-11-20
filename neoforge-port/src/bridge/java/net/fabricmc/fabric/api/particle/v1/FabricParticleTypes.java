package net.fabricmc.fabric.api.particle.v1;

import net.minecraft.particle.SimpleParticleType;

public final class FabricParticleTypes {
	private FabricParticleTypes() {
	}

	public static SimpleParticleType simple(boolean alwaysShow) {
		return new SimpleParticle(alwaysShow);
	}

	private static final class SimpleParticle extends SimpleParticleType {
		private SimpleParticle(boolean alwaysShow) {
			super(alwaysShow);
		}
	}
}
