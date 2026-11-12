package com.tk.gun.entity;

import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import com.tk.gun.TkGunParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** Immovable, invulnerable banner that grants a short allied aura. */
public final class WarBannerEntity extends Entity implements GeoEntity {
	public static final int CLOSE_TICKS = 10;
	public static final int OPEN_TICKS = 10;
	public static final int LIFETIME_TICKS = 300;
	public static final double AURA_RADIUS = 8.0D;
	private static final int SPARK_INTERVAL_TICKS = 2;
	private static final int SPARKS_PER_INTERVAL = 3;
	private static final int SPARK_MAX_AGE_TICKS = 16;
	/**
	 * Locator authored under the {@code P2} bone in {@code 战旗.bbmodel}.
	 * The entity root sits below it so this point can stay on the ground.
	 */
	public static final double LOCATOR_HEIGHT = 39.0D / 16.0D;

	private static final TrackedData<Float> VISUAL_YAW =
			DataTracker.registerData(
					WarBannerEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final RawAnimation CLOSE =
			RawAnimation.begin().thenPlayAndHold("close");
	private static final RawAnimation OPEN =
			RawAnimation.begin().thenPlayAndHold("open");
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");

	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);
	private UUID ownerUuid;
	private boolean expirationHandled;

	public WarBannerEntity(EntityType<? extends WarBannerEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
		setInvulnerable(true);
	}

	public void setOwnerUuid(UUID ownerUuid) {
		this.ownerUuid = ownerUuid;
	}

	public UUID getOwnerUuid() {
		return ownerUuid;
	}

	public void setVisualYaw(float yaw) {
		dataTracker.set(VISUAL_YAW, MathHelper.wrapDegrees(yaw));
	}

	/**
	 * Uses a dedicated float instead of the entity spawn-packet angle so every
	 * renderer receives the authored direction without horizontal quantization.
	 */
	public float visualYaw() {
		float yaw = dataTracker.get(VISUAL_YAW);
		return Float.isFinite(yaw) ? yaw : MathHelper.wrapDegrees(getYaw());
	}

	@Override
	public void tick() {
		super.tick();
		setVelocity(0.0D, 0.0D, 0.0D);
		if (getWorld().isClient()) {
			return;
		}
		if (getWorld() instanceof ServerWorld world
				&& age >= CLOSE_TICKS + OPEN_TICKS
				&& age < LIFETIME_TICKS - SPARK_MAX_AGE_TICKS
				&& age % SPARK_INTERVAL_TICKS == 0) {
			spawnAuraSparks(world);
		}
		if (age % 5 == 0) {
			applyAura();
		}
		if (age >= LIFETIME_TICKS && !expirationHandled) {
			expirationHandled = true;
			TkGunServer.onWarBannerExpired(this);
			discard();
		}
	}

	private void spawnAuraSparks(ServerWorld world) {
		Vec3d center = new Vec3d(
				getX(), getY() + LOCATOR_HEIGHT, getZ());
		for (int index = 0; index < SPARKS_PER_INTERVAL; index++) {
			double angle = random.nextDouble() * MathHelper.TAU;
			// Square-root radial sampling distributes sparks uniformly over
			// the aura disk instead of clustering around the banner pole.
			double radius = Math.sqrt(random.nextDouble()) * AURA_RADIUS;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			double y = center.y + 0.03D + random.nextDouble() * 0.18D;
			world.spawnParticles(TkGunParticles.WAR_BANNER_SPARK,
					x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private void applyAura() {
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		int remaining = Math.max(1, LIFETIME_TICKS - age);
		for (LivingEntity target : world.getEntitiesByClass(
				LivingEntity.class,
				getBoundingBox().expand(AURA_RADIUS),
				this::isFriendly)) {
			Vec3d auraCenter = new Vec3d(
					getX(), getY() + LOCATOR_HEIGHT, getZ());
			if (target.getPos().squaredDistanceTo(auraCenter)
					> AURA_RADIUS * AURA_RADIUS) {
				continue;
			}
			target.addStatusEffect(new StatusEffectInstance(
					StatusEffects.ABSORPTION, remaining, 0,
					true, false, true));
			target.addStatusEffect(new StatusEffectInstance(
					StatusEffects.STRENGTH, remaining, 0,
					true, false, true));
		}
	}

	private boolean isFriendly(LivingEntity candidate) {
		if (!candidate.isAlive() || candidate.isSpectator()) {
			return false;
		}
		if (candidate instanceof PlayerEntity) {
			return true;
		}
		PlayerEntity owner = owner();
		if (owner == null) {
			return false;
		}
		if (candidate.isTeammate(owner)) {
			return true;
		}
		if (candidate instanceof TameableEntity pet
				&& ownerUuid != null
				&& ownerUuid.equals(pet.getOwnerUuid())) {
			return true;
		}
		return candidate instanceof MoltenGolemEntity golem
				&& golem.isOwner(owner);
	}

	private PlayerEntity owner() {
		if (!(getWorld() instanceof ServerWorld world) || ownerUuid == null) {
			return null;
		}
		ServerPlayerEntity player = world.getServer()
				.getPlayerManager().getPlayer(ownerUuid);
		return player != null && player.isAlive() ? player : null;
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
	protected void initDataTracker(
			net.minecraft.entity.data.DataTracker.Builder builder) {
		builder.add(VISUAL_YAW, Float.NaN);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		ownerUuid = nbt.contains("Owner")
				? NbtHelper.toUuid(nbt.get("Owner")) : null;
		age = nbt.getInt("BannerAge");
		if (nbt.contains("BannerYaw")) {
			setVisualYaw(nbt.getFloat("BannerYaw"));
		}
		expirationHandled = false;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (ownerUuid != null) {
			nbt.put("Owner", NbtHelper.fromUuid(ownerUuid));
		}
		nbt.putInt("BannerAge", age);
		nbt.putFloat("BannerYaw", visualYaw());
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "banner", 0,
				this::animationPredicate));
	}

	private PlayState animationPredicate(
			AnimationState<WarBannerEntity> state) {
		if (age < CLOSE_TICKS) {
			return state.setAndContinue(CLOSE);
		}
		if (age < CLOSE_TICKS + OPEN_TICKS) {
			return state.setAndContinue(OPEN);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
