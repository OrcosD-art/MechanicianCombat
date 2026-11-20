package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.CombatBackpackItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class CombatBackpackGeoModel extends GeoModel<CombatBackpackItem> {
	private static final Identifier MODEL =
			TkGun.id("geo/entity/combat_backpack.geo.json");
	private static final Identifier ANIMATION =
			TkGun.id("animations/entity/combat_backpack.animation.json");

	@Override
	public Identifier getModelResource(CombatBackpackItem animatable) {
		return MODEL;
	}

	@Override
	public Identifier getTextureResource(CombatBackpackItem animatable) {
		return animatable.textureLocation();
	}

	@Override
	public Identifier getAnimationResource(CombatBackpackItem animatable) {
		return ANIMATION;
	}

	@Override
	public RenderLayer getRenderType(CombatBackpackItem animatable, Identifier texture) {
		return RenderLayer.getEntityCutoutNoCull(texture);
	}
}
