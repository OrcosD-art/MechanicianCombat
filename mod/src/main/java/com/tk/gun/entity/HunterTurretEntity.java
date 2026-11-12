package com.tk.gun.entity;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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

/** Invulnerable fixed turret deployed by the Mechanician hunter spear. */
public final class HunterTurretEntity extends Entity implements GeoEntity {
	/** Allows five ticks for the first client render before switching to idle. */
	public static final int SUMMON_TICKS = 30;
	public static final int MAX_SHOTS = 8;
	/** Both authored fire clips are 0.5 seconds (10 ticks). */
	public static final int FIRE_INTERVAL_TICKS =
			(int) Math.ceil(0.5D * 20.0D);
	public static final double TARGET_RADIUS = 15.0D;
	public static final float TURN_DEGREES_PER_TICK = 4.0F;
	private static final int[] SUMMON_SOUND_TICKS = {0, 10, 17, 20};

	private static final TrackedData<Float> SHOULDER_YAW =
			DataTracker.registerData(
					HunterTurretEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> FOREARM_PITCH =
			DataTracker.registerData(
					HunterTurretEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> FIRE_MODE =
			DataTracker.registerData(
					HunterTurretEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> FIRE_SEQUENCE =
			DataTracker.registerData(
					HunterTurretEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SUMMONING =
			DataTracker.registerData(
					HunterTurretEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	private static final RawAnimation SUMMON =
			RawAnimation.begin().thenPlayAndHold("summon");
	private static final RawAnimation IDLE =
			RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation FIRE_1 =
			RawAnimation.begin().thenPlayAndHold("fire_1");
	private static final RawAnimation FIRE_2 =
			RawAnimation.begin().thenPlayAndHold("fire_2");

	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);
	private UUID ownerUuid;
	private LivingEntity target;
	private double shoulderYaw;
	private double forearmPitch;
	private int shotsFired;
	private long nextShotAt;
	private long fireModeUntil;
	private long finishAt;
	private int fireSequence;
	private int lastAnimationSequence = -1;
	private int summonSoundMask;
	private boolean summoned;
	private boolean finished;

	public HunterTurretEntity(EntityType<? extends HunterTurretEntity> type,
			World world) {
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

	public float shoulderYaw() {
		return dataTracker.get(SHOULDER_YAW);
	}

	public float forearmPitch() {
		return dataTracker.get(FOREARM_PITCH);
	}

	public boolean isSummoning() {
		return dataTracker.get(SUMMONING);
	}

	public boolean isFiringAnimation() {
		int mode = dataTracker.get(FIRE_MODE);
		return mode == 2 || mode == 3;
	}

	public int fireMode() {
		return dataTracker.get(FIRE_MODE);
	}

	public int fireSequence() {
		return dataTracker.get(FIRE_SEQUENCE);
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (!held.isEmpty()
				&& (TkGunServer.HUNTER_SPEAR == null
				|| !held.isOf(TkGunServer.HUNTER_SPEAR))) {
			return ActionResult.PASS;
		}
		if (getWorld().isClient()) {
			return !isSummoning()
					? ActionResult.SUCCESS : ActionResult.FAIL;
		}
		boolean ready = summoned && !isSummoning() && !finished
				&& ownerUuid != null
				&& ownerUuid.equals(player.getUuid());
		if (!ready) {
			return ActionResult.PASS;
		}
		finishAndReturn("manually recovered");
		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		return interact(player, hand);
	}

	@Override
	public void tick() {
		super.tick();
		setVelocity(Vec3d.ZERO);
		if (getWorld().isClient() || finished) {
			return;
		}
		ServerWorld world = (ServerWorld) getWorld();
		ServerPlayerEntity owner = owner(world);
		if (owner == null) {
			return;
		}
		if (owner.getServerWorld() != world) {
			finishAndReturn("owner changed dimension");
			return;
		}
		if (squaredDistanceTo(owner) > TARGET_RADIUS * TARGET_RADIUS) {
			finishAndReturn("owner left range");
			return;
		}
		if (!summoned) {
			playSummonSounds(world);
			if (age < SUMMON_TICKS) {
				return;
			}
			summoned = true;
			dataTracker.set(SUMMONING, false);
		}
		long now = world.getTime();
		if (fireModeUntil > 0 && now >= fireModeUntil) {
			fireModeUntil = 0;
			dataTracker.set(FIRE_MODE, 0);
		}
		if (finishAt > 0 && now >= finishAt) {
			finishAndReturn("ammunition depleted");
			return;
		}
		if (target == null || !validTarget(target, owner)) {
			target = findTarget(world, owner);
		}
		if (target == null) {
			return;
		}
		Vec3d origin = turretEyePosition();
		Vec3d targetPoint = target.getBoundingBox().getCenter();
		Vec3d delta = targetPoint.subtract(origin);
		double desiredYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		double desiredPitch = Math.toDegrees(
				Math.atan2(delta.y, horizontal));
		shoulderYaw = approachYaw(shoulderYaw, desiredYaw,
				TURN_DEGREES_PER_TICK);
		forearmPitch = approachPitch(forearmPitch, desiredPitch,
				TURN_DEGREES_PER_TICK);
		dataTracker.set(SHOULDER_YAW, (float) shoulderYaw);
		dataTracker.set(FOREARM_PITCH, (float) forearmPitch);

		boolean aligned = Math.abs(MathHelper.wrapDegrees(
				(float) (desiredYaw - shoulderYaw))) <= 4.0F
				&& Math.abs(desiredPitch - forearmPitch) <= 4.0F;
		if (aligned && shotsFired < MAX_SHOTS && now >= nextShotAt
				&& hasLineOfSight(world, origin, targetPoint)) {
			fire(world, owner, shotsFired + 1);
		}
	}

	private void playSummonSounds(ServerWorld world) {
		for (int index = 0; index < SUMMON_SOUND_TICKS.length; index++) {
			int bit = 1 << index;
			if ((summonSoundMask & bit) != 0
					|| age < SUMMON_SOUND_TICKS[index]) {
				continue;
			}
			summonSoundMask |= bit;
			net.minecraft.sound.SoundEvent sound = switch (index) {
				case 0 -> net.minecraft.sound.SoundEvent.of(
						TkGun.SND_HUNTER_MECH4);
				case 1 -> net.minecraft.sound.SoundEvent.of(
						TkGun.SND_HUNTER_BOLT3);
				case 2 -> net.minecraft.sound.SoundEvent.of(
						TkGun.SND_HUNTER_BOLT);
				default -> net.minecraft.sound.SoundEvent.of(
						TkGun.SND_MISSILE_COLD_LAUNCH);
			};
			world.playSound(null, getX(), getY() + 1.0D, getZ(),
					sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
		}
	}

	private ServerPlayerEntity owner(ServerWorld world) {
		if (ownerUuid == null) {
			return null;
		}
		return world.getServer().getPlayerManager().getPlayer(ownerUuid);
	}

	private LivingEntity findTarget(ServerWorld world,
			ServerPlayerEntity owner) {
		Box search = getBoundingBox().expand(TARGET_RADIUS);
		Vec3d origin = turretEyePosition();
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : world.getEntitiesByClass(
				LivingEntity.class, search,
				entity -> validTarget(entity, owner))) {
			Vec3d point = candidate.getBoundingBox().getCenter();
			if (!hasLineOfSight(world, origin, point)) {
				continue;
			}
			double distance = squaredDistanceTo(candidate);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private boolean validTarget(LivingEntity candidate,
			ServerPlayerEntity owner) {
		if (candidate == owner || !candidate.isAlive()
				|| candidate.isSpectator() || !candidate.canHit()
				|| !(candidate instanceof Monster)
				|| CombatTargeting.isGolem(candidate)
				|| candidate.isTeammate(owner)) {
			return false;
		}
		return squaredDistanceTo(candidate)
				<= TARGET_RADIUS * TARGET_RADIUS;
	}

	private boolean hasLineOfSight(ServerWorld world, Vec3d from,
			Vec3d targetPoint) {
		var hit = world.raycast(new RaycastContext(
				from, targetPoint, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, this));
		return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS
				|| from.distanceTo(hit.getPos())
						>= from.distanceTo(targetPoint) - 0.05D;
	}

	private void fire(ServerWorld world, ServerPlayerEntity owner,
			int shotNumber) {
		int muzzleIndex = (shotNumber - 1) % 2;
		Vec3d muzzle = muzzlePosition(muzzleIndex);
		Vec3d direction = target.getBoundingBox().getCenter()
				.subtract(muzzle).normalize();
		ItemStack bolt = new ItemStack(TkGunServer.BOLT);
		MoltenTridentEntity projectile = new MoltenTridentEntity(
				TkGunServer.MOLTEN_TRIDENT_ENTITY, world, owner, bolt);
		projectile.refreshPositionAndAngles(
				muzzle.x, muzzle.y, muzzle.z,
				(float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
				(float) Math.toDegrees(Math.asin(-direction.y)));
		projectile.setVelocity(direction.multiply(2.5D));
		projectile.setGunFired(true);
		projectile.setTurretProjectile(owner);
		world.spawnEntity(projectile);
		world.playSound(null, muzzle.x, muzzle.y, muzzle.z,
				net.minecraft.sound.SoundEvent.of(
						TkGun.SND_GUNSHOT),
				SoundCategory.HOSTILE, 1.0F, 1.0F);

		shotsFired = shotNumber;
		nextShotAt = world.getTime() + FIRE_INTERVAL_TICKS;
		fireModeUntil = world.getTime() + FIRE_INTERVAL_TICKS;
		fireSequence++;
		dataTracker.set(FIRE_MODE, muzzleIndex == 0 ? 2 : 3);
		dataTracker.set(FIRE_SEQUENCE, fireSequence);
		if (shotsFired >= MAX_SHOTS) {
			finishAt = world.getTime() + FIRE_INTERVAL_TICKS;
		}
	}

	private Vec3d muzzlePosition(int index) {
		double yaw = Math.toRadians(shoulderYaw);
		double pitch = Math.toRadians(forearmPitch);
		// The projectile entity origin sits at the barrel axis rather than at
		// the glow quad's local origin. Keep the particle on the locator and
		// compensate the projectile one model block downward.
		double muzzleY = index == 0 ? 17.5D : 14.5D;
		double muzzleZ = -14.5D;
		double forearmY = 16.0D;
		double forearmZ = 2.5D;
		double relativeY = muzzleY - forearmY;
		double relativeZ = muzzleZ - forearmZ;
		double rotatedY = relativeY * Math.cos(pitch)
				- relativeZ * Math.sin(pitch);
		double rotatedZ = relativeY * Math.sin(pitch)
				+ relativeZ * Math.cos(pitch);
		double localY = forearmY + rotatedY - 3.0D;
		double localZ = forearmZ + rotatedZ;
		localY /= 16.0D;
		localZ /= 16.0D;
		return new Vec3d(
				getX() - Math.sin(yaw) * localZ,
				getY() + localY,
				getZ() + Math.cos(yaw) * localZ);
	}

	private Vec3d turretEyePosition() {
		return getPos().add(0.0D, 1.0D, 0.0D);
	}

	private static double approachYaw(double current, double target,
			double maxStep) {
		double delta = MathHelper.wrapDegrees((float) (target - current));
		return MathHelper.wrapDegrees(
				(float) (current + MathHelper.clamp(
						delta, -maxStep, maxStep)));
	}

	private static double approachPitch(double current, double target,
			double maxStep) {
		double delta = target - current;
		return MathHelper.clamp(
				current + MathHelper.clamp(delta, -maxStep, maxStep),
				-89.0D, 89.0D);
	}

	public void finishAndReturn(String reason) {
		if (finished) {
			return;
		}
		finished = true;
		TkGun.LOGGER.info(
				"[mechaniciancombat] hunter turret finished: {} pos={} shots={}",
				reason, getPos(), shotsFired);
		TkGunServer.onHunterTurretFinished(this);
		discard();
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
		// Lets the vanilla crosshair ray select the turret for interaction.
		// It still cannot be damaged or attacked because isAttackable is false.
		return true;
	}

	@Override
	public void move(MovementType movementType, Vec3d movement) {
		// Fixed emplacement.
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SHOULDER_YAW, 0.0F);
		builder.add(FOREARM_PITCH, 0.0F);
		builder.add(FIRE_MODE, 0);
		builder.add(FIRE_SEQUENCE, 0);
		builder.add(SUMMONING, true);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		ownerUuid = nbt.contains("Owner")
				? NbtHelper.toUuid(nbt.get("Owner")) : null;
		shoulderYaw = nbt.getDouble("ShoulderYaw");
		forearmPitch = nbt.getDouble("ForearmPitch");
		shotsFired = nbt.getInt("ShotsFired");
		fireSequence = nbt.getInt("FireSequence");
		// Any turret loaded from a saved world has already completed its
		// deployment; do not replay the summon animation after a relog.
		summoned = true;
		dataTracker.set(SHOULDER_YAW, (float) shoulderYaw);
		dataTracker.set(FOREARM_PITCH, (float) forearmPitch);
		dataTracker.set(FIRE_SEQUENCE, fireSequence);
		dataTracker.set(SUMMONING, false);
		if (shotsFired >= MAX_SHOTS && finishAt <= 0L) {
			finishAt = getWorld().getTime() + FIRE_INTERVAL_TICKS;
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		if (ownerUuid != null) {
			nbt.put("Owner", NbtHelper.fromUuid(ownerUuid));
		}
		nbt.putDouble("ShoulderYaw", shoulderYaw);
		nbt.putDouble("ForearmPitch", forearmPitch);
		nbt.putInt("ShotsFired", shotsFired);
		nbt.putInt("FireSequence", fireSequence);
		nbt.putBoolean("Summoned", summoned);
	}

	@Override
	public void registerControllers(
			AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "turret", 0,
				this::animationPredicate)
				.setSoundKeyframeHandler(event -> { })
				.setCustomInstructionKeyframeHandler(event -> { })
				.setParticleKeyframeHandler(event -> { }));
	}

	private PlayState animationPredicate(
			AnimationState<HunterTurretEntity> state) {
		if (dataTracker.get(SUMMONING)) {
			return state.setAndContinue(SUMMON);
		}
		int mode = dataTracker.get(FIRE_MODE);
		int sequence = dataTracker.get(FIRE_SEQUENCE);
		if (mode == 2 || mode == 3) {
			if (sequence != lastAnimationSequence) {
				lastAnimationSequence = sequence;
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(mode == 2 ? FIRE_1 : FIRE_2);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}
}
