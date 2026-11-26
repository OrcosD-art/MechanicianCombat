package com.tk.gun.item;

import com.tk.gun.TkGun;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Netherite-stat armor with a dedicated transparent vanilla armor layer.
 *
 * <p>Using {@code ArmorMaterials.NETHERITE} directly makes every vanilla-style
 * armor renderer resolve the netherite player texture. The authored Gecko
 * geometry is rendered separately, so the vanilla layer must remain visually
 * empty while retaining the same combat attributes as netherite.</p>
 */
public final class MechanicianArmorItem extends ArmorItem implements GeoItem {
	private static final int NETHERITE_DURABILITY_MULTIPLIER = 37;
	private static final RegistryEntry<ArmorMaterial> MECHANICIAN_MATERIAL =
			new RegistryEntry.Direct<>(createMaterial());
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");
	private final AnimatableInstanceCache cache =
			GeckoLibUtil.createInstanceCache(this);
	private final MechanicianArmorPiece piece;

	public MechanicianArmorItem(MechanicianArmorPiece piece, Type type,
								Settings settings) {
		super(MECHANICIAN_MATERIAL, type, settings.maxCount(1)
				.maxDamage(type.getMaxDamage(NETHERITE_DURABILITY_MULTIPLIER)));
		this.piece = piece;
	}

	public MechanicianArmorPiece piece() {
		return piece;
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
	public void appendTooltip(ItemStack stack, TooltipContext context,
							  List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		tooltip.add(Text.translatable("tooltip.mechaniciancombat.mechanician_"
				+ piece.modelName()));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "armor", 0,
				state -> state.setAndContinue(IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private static ArmorMaterial createMaterial() {
		Map<ArmorItem.Type, Integer> defense =
				new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 3);
		defense.put(ArmorItem.Type.LEGGINGS, 6);
		defense.put(ArmorItem.Type.CHESTPLATE, 8);
		defense.put(ArmorItem.Type.HELMET, 3);
		defense.put(ArmorItem.Type.BODY, 11);
		return new ArmorMaterial(
				defense,
				15,
				SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
				() -> Ingredient.ofItems(Items.NETHERITE_INGOT),
				List.of(new ArmorMaterial.Layer(
						TkGun.id("mechanician_empty"))),
				3.0F,
				0.1F);
	}
}
