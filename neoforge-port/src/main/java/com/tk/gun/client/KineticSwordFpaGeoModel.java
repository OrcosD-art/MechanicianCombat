package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.KineticSwordFpaItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Reuses the Kinetic Sword rig geometry, texture and animation set. */
public final class KineticSwordFpaGeoModel
		extends GeoModel<KineticSwordFpaItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/kinetic_sword_rig.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/entity/kinetic_sword_sword.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/kinetic_sword_rig.animation.json");

	@Override
	public Identifier getModelResource(KineticSwordFpaItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(KineticSwordFpaItem animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(KineticSwordFpaItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(KineticSwordFpaItem animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
