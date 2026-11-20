package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.WarBannerFpaItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Camera-space banner model used only by the first-person skill animation. */
public final class WarBannerFpaGeoModel
		extends GeoModel<WarBannerFpaItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/war_banner_fpa.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/war_banner_fpa.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/war_banner_fpa.animation.json");

	@Override
	public Identifier getModelResource(WarBannerFpaItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(WarBannerFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(WarBannerFpaItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(WarBannerFpaItem animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
