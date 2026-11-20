package com.tk.gun.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Short-lived visual marker for a turret bolt penetrating a living entity. */
public final class SteelArrowImpactEntity extends Entity {
	public static final int LIFETIME_TICKS = 9;

	public SteelArrowImpactEntity(
			EntityType<? extends SteelArrowImpactEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient() && age >= LIFETIME_TICKS) {
			discard();
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public void move(MovementType movementType, Vec3d movement) {
		// Visual-only marker.
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
