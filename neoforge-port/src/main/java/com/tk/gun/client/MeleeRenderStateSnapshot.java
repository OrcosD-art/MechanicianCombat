package com.tk.gun.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Mutable entity fields saved while a frozen target is rendered. */
public record MeleeRenderStateSnapshot(double x, double y, double z,
		double prevX, double prevY, double prevZ,
		float yaw, float pitch, float prevYaw, float prevPitch,
		float headYaw, float bodyYaw, float prevHeadYaw, float prevBodyYaw) {
	public static MeleeRenderStateSnapshot capture(Entity entity) {
		return new MeleeRenderStateSnapshot(entity.getX(), entity.getY(), entity.getZ(),
				entity.prevX, entity.prevY, entity.prevZ,
				entity.getYaw(), entity.getPitch(), entity.prevYaw, entity.prevPitch,
				entity instanceof LivingEntity living ? living.getHeadYaw() : entity.getYaw(),
				entity instanceof LivingEntity living ? living.getBodyYaw() : entity.getYaw(),
				entity instanceof LivingEntity living ? living.prevHeadYaw : entity.prevYaw,
				entity instanceof LivingEntity living ? living.prevBodyYaw : entity.prevYaw);
	}
}
