package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.BackpackPartItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Player-rig model containing only the banner held during flag_skill. */
public final class WarBannerHeldGeoModel
		extends GeoModel<BackpackPartItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/war_banner_held.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/war_banner_held.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/war_banner_held.animation.json");

	@Override
	public Identifier getModelResource(BackpackPartItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(BackpackPartItem animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(BackpackPartItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(BackpackPartItem animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
