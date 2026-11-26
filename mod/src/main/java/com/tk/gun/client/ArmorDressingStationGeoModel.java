package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.block.ArmorDressingStationBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class ArmorDressingStationGeoModel
		extends GeoModel<ArmorDressingStationBlockEntity> {
	private static final Identifier MODEL =
			TkGun.id("geo/block/armor_dressing_station.geo.json");
	private static final Identifier INACTIVE_MODEL =
			TkGun.id("geo/block/armor_dressing_station_inactive.geo.json");
	private static final Identifier TEXTURE =
			TkGun.id("textures/block/armor_dressing_station.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/block/armor_dressing_station.animation.json");

	@Override
	public Identifier getModelResource(ArmorDressingStationBlockEntity animatable) {
		// DRESSING and ACTIVE must share one skeleton. The compact active
		// geometry uses different pivots for 空, 标志 and 改装桌, which makes
		// the authored tail jump when the phase changes.
		return switch (animatable.phase()) {
			case INACTIVE -> INACTIVE_MODEL;
			case DRESSING -> MODEL;
			case ACTIVE -> MODEL;
		};
	}

	@Override
	public Identifier getTextureResource(ArmorDressingStationBlockEntity animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(ArmorDressingStationBlockEntity animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(ArmorDressingStationBlockEntity animatable,
			Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
