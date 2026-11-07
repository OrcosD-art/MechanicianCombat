package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.entity.WarBannerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class WarBannerGeoModel extends GeoModel<WarBannerEntity> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/war_banner.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/war_banner.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/war_banner.animation.json");

	@Override
	public Identifier getModelResource(WarBannerEntity animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(WarBannerEntity animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(WarBannerEntity animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(WarBannerEntity animatable,
									 Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
