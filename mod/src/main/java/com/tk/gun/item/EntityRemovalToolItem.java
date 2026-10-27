package com.tk.gun.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/** Temporary development tool: removes a non-player entity on right click. */
public final class EntityRemovalToolItem extends Item {
	public EntityRemovalToolItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user,
									LivingEntity entity, Hand hand) {
		if (user.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (entity == user || entity instanceof PlayerEntity || !entity.isAlive()) {
			return ActionResult.PASS;
		}
		if (user.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.POOF,
					entity.getX(), entity.getBodyY(0.5D), entity.getZ(),
					12, 0.3D, 0.4D, 0.3D, 0.02D);
			world.playSound(null, entity.getBlockPos(),
					SoundEvents.ENTITY_ENDERMAN_TELEPORT,
					SoundCategory.PLAYERS, 0.6F, 1.4F);
		}
		entity.discard();
		return ActionResult.SUCCESS;
	}
}
