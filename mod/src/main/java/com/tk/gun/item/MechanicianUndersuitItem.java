package com.tk.gun.item;

import com.tk.gun.TkGun;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoryItem;
import io.wispforest.accessories.api.components.AccessoryItemAttributeModifiers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Accessory under-layer that replaces the player's body while worn. */
public final class MechanicianUndersuitItem extends AccessoryItem implements GeoItem {
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");
	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);

	public MechanicianUndersuitItem(Settings settings) {
		super(settings.maxCount(1));
	}

	public static boolean isEquipped(LivingEntity entity) {
		return AccessoriesCapability.getOptionally(entity)
				.map(capability -> capability.isEquipped(
						stack -> stack.getItem() instanceof MechanicianUndersuitItem))
				.orElse(false);
	}

	/** Full authored armor set, including the Accessories undersuit. */
	public static boolean isWearingFullSet(LivingEntity entity) {
		return isEquipped(entity)
				&& isPiece(entity.getEquippedStack(EquipmentSlot.CHEST),
						MechanicianArmorPiece.CHESTPLATE)
				&& isPiece(entity.getEquippedStack(EquipmentSlot.LEGS),
						MechanicianArmorPiece.LEGGINGS)
				&& isPiece(entity.getEquippedStack(EquipmentSlot.FEET),
						MechanicianArmorPiece.BOOTS);
	}

	/**
	 * The core backpack supplies the dodge actuator while the complete suit
	 * protects the wearer, so this remains available outside gun states.
	 */
	public static boolean canUseArmorDodge(LivingEntity entity) {
		if (!isWearingFullSet(entity)) {
			return false;
		}
		ItemStack backpack = CombatBackpackItem.equippedStack(entity);
		return BackpackUpgradeData.hasCore(backpack);
	}

	private static boolean isPiece(ItemStack stack, MechanicianArmorPiece piece) {
		return stack.getItem() instanceof MechanicianArmorItem armor
				&& armor.piece() == piece;
	}

	@Override
	public void getStaticModifiers(Item item,
								   AccessoryItemAttributeModifiers.Builder builder) {
		int armor = ArmorMaterials.NETHERITE.value()
				.getProtection(ArmorItem.Type.HELMET);
		builder.addForAny(EntityAttributes.GENERIC_ARMOR,
				new EntityAttributeModifier(TkGun.id("undersuit_armor"),
						armor, EntityAttributeModifier.Operation.ADD_VALUE), true);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "undersuit", 0,
				state -> state.setAndContinue(IDLE)));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context,
							  List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable(
				"tooltip.tkgun.mechanician_undersuit"));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
