package com.tk.gun.client.entity;

import com.tk.gun.TkGun;
import com.tk.gun.entity.SoulAnchorEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Raw bbmodel per-face UV geometry for the soul-anchor projectile. */
public final class SoulAnchorProjectileGeoModel
		extends GeoModel<SoulAnchorEntity> {
	@Override
	public Identifier getModelResource(SoulAnchorEntity animatable) {
		return TkGun.id("geo/entity/soul_anchor_projectile.geo.json");
	}

	@Override
	public Identifier getTextureResource(SoulAnchorEntity animatable) {
		return TkGun.id("textures/item/soul_anchor_projectile.png");
	}

	@Override
	public Identifier getAnimationResource(SoulAnchorEntity animatable) {
		return TkGun.id(
				"animations/entity/soul_anchor_projectile.animation.json");
	}
}
