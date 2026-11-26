package com.tk.gun.item;

import com.tk.gun.TkGun;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/** Helmet-slot under-layer that replaces the player's body while worn. */
public final class MechanicianUndersuitItem extends Item
		implements Equipment, GeoItem {
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");
	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);

	public MechanicianUndersuitItem(Settings settings) {
		super(settings);
	}

	public static Settings createSettings() {
		AttributeModifiersComponent modifiers =
				AttributeModifiersComponent.builder()
						.add(EntityAttributes.GENERIC_ARMOR,
								new EntityAttributeModifier(
										TkGun.id("undersuit_armor"),
										ArmorMaterials.NETHERITE.value()
												.getProtection(
														ArmorItem.Type.HELMET),
										EntityAttributeModifier.Operation.ADD_VALUE),
								AttributeModifierSlot.HEAD)
						.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
								new EntityAttributeModifier(
										TkGun.id("undersuit_toughness"),
										ArmorMaterials.NETHERITE.value()
												.toughness(),
										EntityAttributeModifier.Operation.ADD_VALUE),
								AttributeModifierSlot.HEAD)
						.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
								new EntityAttributeModifier(
										TkGun.id("undersuit_knockback_resistance"),
										ArmorMaterials.NETHERITE.value()
												.knockbackResistance(),
										EntityAttributeModifier.Operation.ADD_VALUE),
								AttributeModifierSlot.HEAD)
						.build();
		return new Settings().maxCount(1).maxDamage(407)
				.attributeModifiers(modifiers);
	}

	public static boolean isEquipped(LivingEntity entity) {
		return entity.getEquippedStack(EquipmentSlot.HEAD).getItem()
				instanceof MechanicianUndersuitItem;
	}

	/** Full authored armor set, including the helmet-slot undersuit. */
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
	 * Client-safe visual condition. The server removes all outer armor whenever
	 * the undersuit is absent, so the three armor slots are authoritative during
	 * rendering without requiring an Accessories capability lookup.
	 */
	public static boolean isWearingOuterSet(LivingEntity entity) {
		return isPiece(entity.getEquippedStack(EquipmentSlot.CHEST),
					MechanicianArmorPiece.CHESTPLATE)
				&& isPiece(entity.getEquippedStack(EquipmentSlot.LEGS),
					MechanicianArmorPiece.LEGGINGS)
				&& isPiece(entity.getEquippedStack(EquipmentSlot.FEET),
					MechanicianArmorPiece.BOOTS);
	}

	public static boolean isLockedOuterSet(LivingEntity entity) {
		return isEquipped(entity)
				&& ArmorUpgradeData.isStationLocked(
						entity.getEquippedStack(EquipmentSlot.CHEST))
				&& ArmorUpgradeData.isStationLocked(
						entity.getEquippedStack(EquipmentSlot.LEGS))
				&& ArmorUpgradeData.isStationLocked(
						entity.getEquippedStack(EquipmentSlot.FEET));
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
	public EquipmentSlot getSlotType() {
		return EquipmentSlot.HEAD;
	}

	@Override
	public RegistryEntry<SoundEvent> getEquipSound() {
		return ArmorMaterials.NETHERITE.value().equipSound();
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user,
			Hand hand) {
		if (!world.isClient()) {
			user.sendMessage(Text.translatable(
					"message.mechaniciancombat.armor_station_only"), true);
		}
		return TypedActionResult.fail(user.getStackInHand(hand));
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
				"tooltip.mechaniciancombat.mechanician_undersuit"));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
