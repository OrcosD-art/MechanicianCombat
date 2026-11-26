package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.entity.ArmorDressingPerformerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ArmorDressingPerformerGeoModel
		extends GeoModel<ArmorDressingPerformerEntity> {
	private static final Identifier MODEL =
			TkGun.id("geo/block/armor_dressing_station.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/block/armor_dressing_station.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/block/armor_dressing_station.animation.json");

	@Override
	public Identifier getModelResource(ArmorDressingPerformerEntity animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(ArmorDressingPerformerEntity animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(ArmorDressingPerformerEntity animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(ArmorDressingPerformerEntity animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
