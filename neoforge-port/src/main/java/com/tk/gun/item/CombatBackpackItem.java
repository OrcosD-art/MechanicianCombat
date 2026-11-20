package com.tk.gun.item;

import com.tk.gun.TkGun;
import io.wispforest.accessories.api.AccessoryItem;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
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
	private static final RawAnimation READY_TO_FLY =
			RawAnimation.begin().thenPlayAndHold("ready_to_fly");
	private static final RawAnimation FLYING =
			RawAnimation.begin().thenPlayAndHold("flying");
	private static final RawAnimation CLOSE =
			RawAnimation.begin().thenPlayAndHold("close");
	private static final RawAnimation WAR_BANNER_READY =
			RawAnimation.begin().thenPlayAndHold("war_banner_ready");
	private static final RawAnimation WAR_BANNER_AIM =
			RawAnimation.begin().thenPlayAndHold("war_banner_aim");
	private static final RawAnimation WAR_BANNER_CD =
			RawAnimation.begin().thenPlayAndHold("war_banner_cd");
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
				.map(CombatBackpackItem::liveEquippedStack)
				.orElse(ItemStack.EMPTY);
	}

	/**
	 * Read the live accessory container rather than the Accessories lookup
	 * cache. The modification table replaces component data on the equipped
	 * stack in place; a cached SlotEntryReference can otherwise retain the
	 * pre-modification stack and hide launcher-specific UI until relog.
	 */
	private static ItemStack liveEquippedStack(AccessoriesCapability capability) {
		for (AccessoriesContainer container :
				capability.getContainers().values()) {
			var inventory = container.getAccessories();
			for (int slot = 0; slot < container.getSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty()
						&& stack.getItem() instanceof CombatBackpackItem) {
					// Callers also persist component changes (stored ammunition,
					// installed modules and cooldown data) back through this
					// stack. Returning a copy makes those writes disappear when
					// the menu performs its next content synchronization.
					return stack;
				}
			}
		}
		return java.util.Optional.ofNullable(capability.getFirstEquipped(
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
				state -> CombatBackpackAnimation.manualJumpRendering()
						? PlayState.STOP
						: state.setAndContinue(switch (CombatBackpackAnimation.renderingPhase()) {
					case CD -> CD;
					case READY -> READY;
					case MAINTENANCE -> MAINTENANCE;
					case READY_TO_FLY -> READY_TO_FLY;
					case FLYING -> FLYING;
					case CLOSE -> CLOSE;
					case BANNER_READY -> WAR_BANNER_READY;
					case BANNER_AIM -> WAR_BANNER_AIM;
					case BANNER_CD -> WAR_BANNER_CD;
					default -> AIM;
				})));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
