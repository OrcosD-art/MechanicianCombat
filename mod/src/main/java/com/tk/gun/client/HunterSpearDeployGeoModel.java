package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.MechanicianHunterSpearItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Player-rig geometry containing the hunter spear during deployment. */
public final class HunterSpearDeployGeoModel
		extends GeoModel<MechanicianHunterSpearItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/hunter_spear_deploy.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/hunter_spear_deploy.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/hunter_spear_deploy.animation.json");

	@Override
	public Identifier getModelResource(MechanicianHunterSpearItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(MechanicianHunterSpearItem animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(MechanicianHunterSpearItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(MechanicianHunterSpearItem animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
