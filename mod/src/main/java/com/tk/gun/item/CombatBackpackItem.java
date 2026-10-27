package com.tk.gun.item;

import com.tk.gun.TkGun;
import io.wispforest.accessories.api.AccessoryItem;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class CombatBackpackItem extends AccessoryItem implements GeoItem {
	private static final RawAnimation AIM =
			RawAnimation.begin().thenPlayAndHold("aim");
	private static final RawAnimation CD =
			RawAnimation.begin().thenPlayAndHold("CD");
	private static final RawAnimation READY =
			RawAnimation.begin().thenPlayAndHold("ready");
	private static final RawAnimation MAINTENANCE =
			RawAnimation.begin().thenPlayAndHold("Maintenance");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final String textureName;

	public CombatBackpackItem(Settings settings) {
		this(settings, "combat_backpack");
	}

	public CombatBackpackItem(Settings settings, String textureName) {
		super(settings);
		this.textureName = textureName;
	}

	public static ItemStack equippedStack(LivingEntity entity) {
		return AccessoriesCapability.getOptionally(entity)
				.map(capability -> capability.getFirstEquipped(
						stack -> stack.getItem() instanceof CombatBackpackItem))
				.map(SlotEntryReference::stack)
				.orElse(ItemStack.EMPTY);
	}

	public Identifier textureLocation() {
		return TkGun.id("textures/entity/" + textureName + ".png");
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "equipped", 0,
				state -> state.setAndContinue(switch (CombatBackpackAnimation.renderingPhase()) {
					case CD -> CD;
					case READY -> READY;
					case MAINTENANCE -> MAINTENANCE;
					default -> AIM;
				})));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
