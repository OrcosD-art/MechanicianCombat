package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.SoulAnchorFpaItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Camera-space Soul Anchor model used while aiming or throwing. */
public final class SoulAnchorFpaGeoModel
		extends GeoModel<SoulAnchorFpaItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/soul_anchor_fpa.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/soul_anchor_fpa.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/soul_anchor_fpa.animation.json");

	@Override
	public Identifier getModelResource(SoulAnchorFpaItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(SoulAnchorFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(SoulAnchorFpaItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(SoulAnchorFpaItem animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
