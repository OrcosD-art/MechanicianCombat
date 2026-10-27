package com.tk.gun.item;

import com.tk.gun.TkGunServer;
import com.tk.gun.entity.MoltenTridentEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 熔铁弩矢：可堆叠 64 的三叉戟式弹药。
 *
 * <p>保留三叉戟的投掷、近战和回收行为，但不消耗耐久；
 * 同时刻意忽略激流，避免右键变成冲刺旋转攻击。
 */
public class MoltenTridentItem extends TridentItem {
	public MoltenTridentItem(Item.Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof PlayerEntity player)) {
			return;
		}
		int useTicks = getMaxUseTime(stack, user) - remainingUseTicks;
		if (useTicks < 10 || world.isClient) {
			return;
		}
		MoltenTridentEntity trident = new MoltenTridentEntity(
				TkGunServer.MOLTEN_TRIDENT_ENTITY, world, player, stack.copyWithCount(1));
		trident.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.5F, 1.0F);
		if (player.isInCreativeMode()) {
			trident.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
		}
		world.spawnEntity(trident);
		world.playSoundFromEntity(null, trident, SoundEvents.ITEM_TRIDENT_THROW.value(),
				SoundCategory.PLAYERS, 1.0F, 1.0F);
		if (!player.isInCreativeMode()) {
			player.getInventory().removeOne(stack);
		}
	}

	@Override
	public void postDamageEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		// 堆叠 64 的弹药不做耐久消耗。
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public int getEnchantability() {
		return 0;
	}

	@Override
	public void postProcessComponents(ItemStack stack) {
		super.postProcessComponents(stack);
		tkgun$stripEnchantments(stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity,
			int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		tkgun$stripEnchantments(stack);
	}

	private static void tkgun$stripEnchantments(ItemStack stack) {
		stack.remove(DataComponentTypes.ENCHANTMENTS);
		stack.remove(DataComponentTypes.STORED_ENCHANTMENTS);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		super.appendTooltip(stack, context, tooltip, type);
		BulletTooltips.addDamage(tooltip, "tooltip.tkgun.bolt.damage");
		BulletTooltips.addEffect(tooltip, "tooltip.tkgun.bolt.effect");
	}
}
