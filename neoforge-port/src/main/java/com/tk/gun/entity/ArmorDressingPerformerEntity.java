package com.tk.gun.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Fixed-model, fixed-animation performer for the dressing sequence. */
public final class ArmorDressingPerformerEntity extends Entity
		implements GeoEntity {
	private static final TrackedData<Float> DISPLAY_TIME =
			DataTracker.registerData(ArmorDressingPerformerEntity.class,
					TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Long> START_TICK =
			DataTracker.registerData(ArmorDressingPerformerEntity.class,
					TrackedDataHandlerRegistry.LONG);
	private static final TrackedData<Integer> PLAYER_ID =
			DataTracker.registerData(ArmorDressingPerformerEntity.class,
					TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<String> ANIMATION_NAME =
			DataTracker.registerData(ArmorDressingPerformerEntity.class,
					TrackedDataHandlerRegistry.STRING);
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);

	public ArmorDressingPerformerEntity(
			EntityType<? extends ArmorDressingPerformerEntity> type,
			World world) {
		super(type, world);
		setNoGravity(true);
		setSilent(true);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		// Rendering evaluates the authored clip directly from entity age.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
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
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(DISPLAY_TIME, -1.0F);
		builder.add(START_TICK, -1L);
		builder.add(PLAYER_ID, -1);
		builder.add(ANIMATION_NAME, "盔甲着装");
	}

	public void setDisplayTime(float seconds) {
		dataTracker.set(DISPLAY_TIME, seconds);
	}

	public float displayTime() {
		return dataTracker.get(DISPLAY_TIME);
	}

	public void setStartTick(long tick) {
		dataTracker.set(START_TICK, tick);
	}

	public long startTick() {
		return dataTracker.get(START_TICK);
	}

	public void setPlayerId(int playerId) {
		dataTracker.set(PLAYER_ID, playerId);
	}

	public int playerId() {
		return dataTracker.get(PLAYER_ID);
	}

	public void setAnimationName(String animationName) {
		dataTracker.set(ANIMATION_NAME, animationName);
	}

	public String animationName() {
		return dataTracker.get(ANIMATION_NAME);
	}

	public double animationSeconds(float partialTick) {
		if (displayTime() >= 0.0F) {
			return displayTime();
		}
		if (startTick() >= 0L && getWorld() != null) {
			return (Math.max(0L,
					getWorld().getTime() - startTick()) + partialTick)
					/ 20.0D;
		}
		return (age + partialTick) / 20.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
