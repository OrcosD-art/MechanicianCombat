package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.entity.HunterTurretEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib assets for the fixed hunter turret. */
public final class HunterTurretGeoModel extends GeoModel<HunterTurretEntity> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/hunter_turret.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/hunter_turret.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/hunter_turret.animation.json");

	@Override
	public Identifier getModelResource(HunterTurretEntity animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(HunterTurretEntity animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(HunterTurretEntity animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(HunterTurretEntity animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
