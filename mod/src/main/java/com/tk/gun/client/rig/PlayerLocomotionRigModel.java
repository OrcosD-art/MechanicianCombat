package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Hidden GeckoLib hierarchy used only to evaluate ordinary player poses. */
public final class PlayerLocomotionRigModel
		extends DefaultedEntityGeoModel<PlayerLocomotionRig> {
	public PlayerLocomotionRigModel() {
		super(TkGun.id("player_locomotion_rig"));
	}

	@Override
	public Identifier getTextureResource(PlayerLocomotionRig animatable,
			GeoRenderer<PlayerLocomotionRig> renderer) {
		AbstractClientPlayerEntity player = animatable.player();
		return player.getSkinTextures().texture();
	}
}
