package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.CombatPropFpaItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Resource bundle for one authored first-person combat prop. */
public final class CombatPropFpaGeoModel extends GeoModel<CombatPropFpaItem> {
	private final Identifier model;
	private final Identifier texture;
	private final Identifier animation;

	public CombatPropFpaGeoModel(String assetName) {
		model = TkGun.id("geo/entity/" + assetName + ".geo.json");
		texture = TkGun.id("textures/entity/" + assetName + ".png");
		animation = TkGun.id(
				"animations/entity/" + assetName + ".animation.json");
	}

	@Override
	public Identifier getModelResource(CombatPropFpaItem animatable) {
		return model;
	}

	@Override
	public Identifier getTextureResource(CombatPropFpaItem animatable) {
		return texture;
	}

	@Override
	public Identifier getAnimationResource(CombatPropFpaItem animatable) {
		return animation;
	}

	@Override
	public RenderLayer getRenderType(CombatPropFpaItem animatable,
			Identifier currentTexture) {
		return RenderLayer.getEntityCutoutNoCull(currentTexture);
	}
}
