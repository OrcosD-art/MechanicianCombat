package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.entity.MoltenGolemEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class MoltenGolemGeoModel extends GeoModel<MoltenGolemEntity> {
	private static final Identifier MODEL = TkGun.id("geo/entity/molten_golem.geo.json");
	private static final Identifier TEXTURE = TkGun.id("textures/entity/molten_golem.png");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/molten_golem.animation.json");

	@Override
	public Identifier getModelResource(MoltenGolemEntity animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(MoltenGolemEntity animatable) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(MoltenGolemEntity animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(MoltenGolemEntity animatable, Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
