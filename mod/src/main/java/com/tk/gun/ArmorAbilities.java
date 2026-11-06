package com.tk.gun;

import com.tk.gun.item.ArmorPartItem;
import com.tk.gun.item.ArmorUpgradeData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Runtime effects supplied by modules installed in the Mechanician chestplate. */
public final class ArmorAbilities {
	public static final int SHIELD_COOLDOWN_TICKS = 40;
	public static final int SHIELD_DEPLETED_COOLDOWN_TICKS = 300;
	public static final int SHIELD_RECHARGE_INTERVAL_TICKS = 100;
	public static final int SHIELD_RECHARGE_KILLS = 3;
	public static final int TOTEM_COOLDOWN_TICKS = 1200;

	private ArmorAbilities() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(
				ArmorAbilities::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(
				ArmorAbilities::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(
				ArmorAbilities::recoverShieldCharge);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			boolean send = server.getTicks() % 10 == 0;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				boolean changed = tickShieldRecovery(player);
				if (send || changed) {
					sendSnapshot(player);
				}
			}
		});
	}

	private static boolean allowDamage(
			net.minecraft.entity.LivingEntity entity,
			net.minecraft.entity.damage.DamageSource source,
			float amount) {
		if (amount <= 0.0F || !(entity instanceof ServerPlayerEntity player)
				|| source.isIn(DamageTypeTags.BYPASSES_SHIELD)) {
			return true;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return true;
		}
		long now = player.getWorld().getTime();
		if (ArmorUpgradeData.shieldCooldownUntil(chest) > now
				|| ArmorUpgradeData.shieldCharges(chest) <= 0) {
			return true;
		}

		int nextCharges = ArmorUpgradeData.shieldCharges(chest) - 1;
		ArmorUpgradeData.setShieldCharges(chest, nextCharges);
		if (nextCharges == 0) {
			ArmorUpgradeData.setShieldRechargeKills(chest, 0);
			ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
			ArmorUpgradeData.setShieldCooldownUntil(
					chest, now + SHIELD_DEPLETED_COOLDOWN_TICKS);
		} else {
			ArmorUpgradeData.setShieldCooldownUntil(
					chest, now + SHIELD_COOLDOWN_TICKS);
			ArmorUpgradeData.setShieldRechargeAt(
					chest, now + SHIELD_RECHARGE_INTERVAL_TICKS);
		}
		player.getItemCooldownManager().set(
				TkGunServer.PAULDRON_LEFT, SHIELD_COOLDOWN_TICKS);
		ServerWorld world = player.getServerWorld();
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS,
				1.0F, 1.0F);
		world.spawnParticles(ParticleTypes.CRIT,
				player.getX() - Math.sin(Math.toRadians(player.getYaw())),
				player.getY() + 1.25D,
				player.getZ() + Math.cos(Math.toRadians(player.getYaw())),
				10, 0.25D, 0.25D, 0.25D, 0.05D);
		player.sendMessage(
				Text.translatable("message.tkgun.shield_blocked"), true);
		sendSnapshot(player);
		return false;
	}

	private static boolean allowDeath(
			net.minecraft.entity.LivingEntity entity,
			net.minecraft.entity.damage.DamageSource source,
			float amount) {
		if (!(entity instanceof ServerPlayerEntity player)) {
			return true;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C1)) {
			return true;
		}
		long now = player.getWorld().getTime();
		if (ArmorUpgradeData.totemCooldownUntil(chest) > now) {
			return true;
		}

		ArmorUpgradeData.setTotemCooldownUntil(
				chest, now + TOTEM_COOLDOWN_TICKS);
		player.getItemCooldownManager().set(
				TkGunServer.UNDYING_TOTEM_PART, TOTEM_COOLDOWN_TICKS);
		player.setHealth(1.0F);
		player.clearStatusEffects();
		player.addStatusEffect(new StatusEffectInstance(
				StatusEffects.ABSORPTION, 100, 1));
		player.addStatusEffect(new StatusEffectInstance(
				StatusEffects.REGENERATION, 900, 1));
		player.addStatusEffect(new StatusEffectInstance(
				StatusEffects.FIRE_RESISTANCE, 800, 0));
		ServerWorld world = player.getServerWorld();
		world.sendEntityStatus(player, (byte) 35);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS,
				1.0F, 1.0F);
		player.sendMessage(
				Text.translatable("message.tkgun.totem_saved"), true);
		sendSnapshot(player);
		return false;
	}

	private static void recoverShieldCharge(
			net.minecraft.entity.LivingEntity killed,
			net.minecraft.entity.damage.DamageSource source) {
		if (killed instanceof PlayerEntity
				|| !(source.getAttacker() instanceof ServerPlayerEntity player)) {
			return;
		}
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return;
		}
		int charges = ArmorUpgradeData.shieldCharges(chest);
		if (charges != 0) {
			return;
		}
		long now = player.getWorld().getTime();
		if (ArmorUpgradeData.shieldCooldownUntil(chest) <= now) {
			return;
		}
		int kills = ArmorUpgradeData.shieldRechargeKills(chest) + 1;
		if (kills >= SHIELD_RECHARGE_KILLS) {
			ArmorUpgradeData.setShieldRechargeKills(chest, 0);
			ArmorUpgradeData.setShieldCooldownUntil(chest, 0L);
			ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
			ArmorUpgradeData.setShieldCharges(
					chest, ArmorUpgradeData.MAX_SHIELD_CHARGES);
		} else {
			ArmorUpgradeData.setShieldRechargeKills(chest, kills);
		}
		sendSnapshot(player);
	}

	private static boolean tickShieldRecovery(ServerPlayerEntity player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		if (!ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2)) {
			return false;
		}
		int charges = ArmorUpgradeData.shieldCharges(chest);
		long now = player.getWorld().getTime();
		long cooldown = ArmorUpgradeData.shieldCooldownUntil(chest);
		boolean changed = false;
		if (cooldown > 0L && now >= cooldown) {
			ArmorUpgradeData.setShieldCooldownUntil(chest, 0L);
			changed = true;
		}
		if (charges == 0) {
			if (cooldown > 0L && now >= cooldown) {
				ArmorUpgradeData.setShieldRechargeKills(chest, 0);
				ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
				ArmorUpgradeData.setShieldCharges(
						chest, ArmorUpgradeData.MAX_SHIELD_CHARGES);
			}
			return changed;
		}
		if (charges >= ArmorUpgradeData.MAX_SHIELD_CHARGES) {
			if (ArmorUpgradeData.shieldRechargeAt(chest) > 0L) {
				ArmorUpgradeData.setShieldRechargeAt(chest, 0L);
				changed = true;
			}
			return changed;
		}
		long rechargeAt = ArmorUpgradeData.shieldRechargeAt(chest);
		if (rechargeAt <= 0L) {
			ArmorUpgradeData.setShieldRechargeAt(
					chest, now + SHIELD_RECHARGE_INTERVAL_TICKS);
			return true;
		}
		if (now >= rechargeAt) {
			int nextCharges = charges;
			if (player.getHealth() == player.getMaxHealth()) {
				nextCharges++;
				ArmorUpgradeData.setShieldCharges(chest, nextCharges);
			}
			ArmorUpgradeData.setShieldRechargeAt(
					chest,
					nextCharges < ArmorUpgradeData.MAX_SHIELD_CHARGES
							? now + SHIELD_RECHARGE_INTERVAL_TICKS : 0L);
			changed = true;
		}
		return changed;
	}

	public static void sendSnapshot(ServerPlayerEntity player) {
		ItemStack chest = ArmorUpgradeData.equippedChestplate(player);
		long now = player.getWorld().getTime();
		ServerPlayNetworking.send(player, new com.tk.gun.net.ArmorAbilityPayload(
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C2),
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C1),
				ArmorUpgradeData.hasPart(chest, ArmorPartItem.Kind.C3),
				ArmorUpgradeData.shieldCharges(chest),
				ArmorUpgradeData.shieldRechargeKills(chest),
				(int) Math.max(0, ArmorUpgradeData.shieldCooldownUntil(chest) - now),
				(int) Math.max(0, ArmorUpgradeData.totemCooldownUntil(chest) - now)));
	}
}
