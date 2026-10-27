package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.api.GunDefinition;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * 玩家身体那一份：几何 = {@code geo/entity/bowgun_rig.geo.json}
 * （31 根骨骼都在——动画要靠它们；只有身体那 6 块带方块，UV 是标准 64x64 皮肤版式），
 * 贴图 = **玩家自己的皮肤**（动态取，所以每个人显示自己的样子）。
 *
 * <p>动画同名：{@code animations/entity/bowgun_rig.animation.json}（作者那 8 段，骨名即作者骨名）。
 * 枪那一份由 {@link BowgunGunLayer} 逐骨骼补画（另一张贴图）。
 */
public class BowgunRigModel extends DefaultedEntityGeoModel<BowgunRig> {
	public BowgunRigModel() {
		super(Identifier.of("tkgun", "bowgun_rig"));
	}

	private static GunDefinition currentDefinition() {
		return TkGun.definition(BowgunRig.CURRENT_PLAYER);
	}

	@Override
	public Identifier getModelResource(BowgunRig animatable, GeoRenderer<BowgunRig> renderer) {
		return currentDefinition().modelResource();
	}

	@Override
	public Identifier getModelResource(BowgunRig animatable) {
		return currentDefinition().modelResource();
	}

	@Override
	public Identifier getAnimationResource(BowgunRig animatable) {
		return currentDefinition().animationResource();
	}

	@Override
	public Identifier getTextureResource(BowgunRig animatable, GeoRenderer<BowgunRig> renderer) {
		if (renderer instanceof BowgunRigRenderer rig && rig.getCurrentEntity() instanceof AbstractClientPlayerEntity player) {
			return player.getSkinTextures().texture();
		}
		return Identifier.of("minecraft", "textures/entity/steve.png");
	}
}
