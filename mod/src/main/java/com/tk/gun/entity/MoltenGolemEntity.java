package com.tk.gun.entity;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import com.tk.gun.TkGunParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MoltenGolemEntity extends IronGolemEntity implements GeoEntity {
	private static final int LANDING_TICKS = 70;
	private static final int ATK2_COOLDOWN_TICKS = 100;
	private static final int ATK3_COOLDOWN_TICKS = 400;
	private static final double MONSTER_SCAN_RADIUS = 3.0D;
	private static final TrackedData<Integer> VISUAL_STATE =
			DataTracker.registerData(MoltenGolemEntity.class,
					TrackedDataHandlerRegistry.INTEGER);

	private static final SoundEvent SWING_SOUND = SoundEvent.of(TkGun.id("golem_swing"));
	private static final SoundEvent HAMMER_SOUND = SoundEvent.of(TkGun.id("golem_hammer"));
	private static final SoundEvent EXPLOSION_SOUND = SoundEvent.of(TkGun.id("golem_explosion"));
	private static final SoundEvent ELECTRIC_SOUND = SoundEvent.of(TkGun.id("golem_electric"));
	private static final Map<Integer, LocatorSample> CLIENT_LOCATORS = new HashMap<>();
	private static final Map<Integer, Integer> CLIENT_CONVERGE_GENERATIONS = new HashMap<>();

	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);
	private int summonTicks = LANDING_TICKS;
	private int atk2Cooldown;
	private int atk3Cooldown;
	private int attackElapsed;
	private boolean attackDamageDone;
	private AttackKind attackKind = AttackKind.NONE;
	private UUID ownerUuid;
	private VisualState clientVisualState = VisualState.IDLE;
	private int clientVisualTicks;
	private int clientAttackEffectMask;

	private record LocatorSample(Vec3d position, long tick) {
	}

	public MoltenGolemEntity(EntityType<? extends IronGolemEntity> type, World world) {
		super(type, world);
		setPlayerCreated(true);
		setVisualState(VisualState.LANDING);
		setAiDisabled(true);
		goalSelector.add(3, new FollowSummonerGoal(this, 1.0D, 6.0D, 3.0D));
	}

	public enum VisualState {
		IDLE,
		WALK,
		ATTACK_1,
		ATTACK_2,
		ATTACK_3,
		LANDING,
		PURSUIT
	}

	private enum AttackKind {
		NONE(0, Integer.MAX_VALUE),
		ATK1(20, 8),
		ATK2(40, 24),
		ATK3(58, 38);

		final int duration;
		final int damageTick;

		AttackKind(int duration, int damageTick) {
			this.duration = duration;
			this.damageTick = damageTick;
		}

		VisualState visualState() {
			return switch (this) {
				case ATK1 -> VisualState.ATTACK_1;
				case ATK2 -> VisualState.ATTACK_2;
				case ATK3 -> VisualState.ATTACK_3;
				default -> VisualState.IDLE;
			};
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VISUAL_STATE, VisualState.LANDING.ordinal());
	}

	public VisualState getVisualState() {
		int ordinal = dataTracker.get(VISUAL_STATE);
		VisualState[] values = VisualState.values();
		return values[MathHelper.clamp(ordinal, 0, values.length - 1)];
	}

	private void setVisualState(VisualState state) {
		if (getVisualState() != state) {
			dataTracker.set(VISUAL_STATE, state.ordinal());
		}
	}

	public boolean isSummoning() {
		return getWorld().isClient()
				? getVisualState() == VisualState.LANDING : summonTicks > 0;
	}

	public boolean isCombatAttacking() {
		return attackKind != AttackKind.NONE;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient()) {
			VisualState visual = getVisualState();
			if (visual != clientVisualState) {
				clientVisualState = visual;
				clientVisualTicks = 0;
				clientAttackEffectMask = 0;
			} else {
				clientVisualTicks++;
			}
			return;
		}
		if (ownerUuid == null) {
			bindNearestSummoner();
		}
		if (atk3Cooldown > 0) {
			atk3Cooldown--;
		}
		if (atk2Cooldown > 0) {
			atk2Cooldown--;
		}
		if (summonTicks > 0) {
			tickSummoning();
			return;
		}
		if (attackKind != AttackKind.NONE) {
			tickAttack();
			return;
		}
		updatePassiveAnimation();
	}

	private void tickSummoning() {
		stopHorizontalMovement();
		summonTicks--;
		int elapsed = LANDING_TICKS - summonTicks;
		if (elapsed == 10) {
			playWorldSound(EXPLOSION_SOUND, 1.1F, 1.0F);
			performLandingExplosion();
		}
		if (summonTicks <= 0) {
			setAiDisabled(false);
			updatePassiveAnimation();
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		if (getWorld().isClient() || target == null || attackKind != AttackKind.NONE
				|| summonTicks > 0 || CombatTargeting.isGolem(target)) {
			return false;
		}
		AttackKind next = chooseAttack();
		attackKind = next;
		attackElapsed = 0;
		attackDamageDone = false;
		setAiDisabled(true);
		stopHorizontalMovement();
		getNavigation().stop();
		getLookControl().lookAt(target, 30.0F, 30.0F);
		setVisualState(next.visualState());
		if (next == AttackKind.ATK3) {
			atk3Cooldown = ATK3_COOLDOWN_TICKS;
		} else if (next == AttackKind.ATK2) {
			atk2Cooldown = ATK2_COOLDOWN_TICKS;
		}
		return true;
	}

	private AttackKind chooseAttack() {
		if (atk3Cooldown <= 0) {
			return AttackKind.ATK3;
		}
		if (atk2Cooldown <= 0 && hasNearbyMonster()) {
			return AttackKind.ATK2;
		}
		return AttackKind.ATK1;
	}

	private boolean hasNearbyMonster() {
		Box area = Box.of(getPos().add(0.0D, 1.0D, 0.0D),
				MONSTER_SCAN_RADIUS * 2.0D, 4.0D, MONSTER_SCAN_RADIUS * 2.0D);
		return !getWorld().getEntitiesByClass(LivingEntity.class, area,
				entity -> entity != this && entity.isAlive()
						&& entity instanceof Monster
						&& entity.squaredDistanceTo(this)
						<= MONSTER_SCAN_RADIUS * MONSTER_SCAN_RADIUS).isEmpty();
	}

	private void tickAttack() {
		stopHorizontalMovement();
		getNavigation().stop();
		attackElapsed++;
		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			getLookControl().lookAt(target, 35.0F, 35.0F);
		}

		if (attackKind == AttackKind.ATK3) {
			if (attackElapsed == 11) {
				playWorldSound(ELECTRIC_SOUND, 1.0F, 1.0F);
			}
		}

		if (!attackDamageDone && attackElapsed >= attackKind.damageTick) {
			attackDamageDone = true;
			applyAttackDamage(attackKind);
		}
		if (attackElapsed >= attackKind.duration) {
			attackKind = AttackKind.NONE;
			setAiDisabled(false);
			updatePassiveAnimation();
		}
	}

	private void applyAttackDamage(AttackKind kind) {
		if (!(getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		switch (kind) {
			case ATK1 -> {
				playWorldSound(SWING_SOUND, 1.0F, 1.0F);
				damageInFront(2.0D, 6.0F);
			}
			case ATK2 -> {
				playWorldSound(HAMMER_SOUND, 1.1F, 0.9F);
				damageAround(getPos().add(0.0D, 1.0D, 0.0D), 5.0D, 8.0F);
			}
			case ATK3 -> {
				Vec3d impact = attackImpactPosition();
				playWorldSound(EXPLOSION_SOUND, 1.2F, 0.85F);
				damageAround(impact, 6.0D, 15.0F);
			}
			default -> {
			}
		}
	}

	private void damageInFront(double radius, float damage) {
		Vec3d forward = new Vec3d(-Math.sin(Math.toRadians(getYaw())), 0.0D,
				Math.cos(Math.toRadians(getYaw()))).normalize();
		double reach = radius + getWidth() * 0.5D;
		Box area = getBoundingBox().expand(reach, 1.5D, reach);
		Vec3d origin = getBoundingBox().getCenter();
		Set<Integer> attempted = new HashSet<>();
		int damaged = 0;
		for (LivingEntity target : getWorld().getEntitiesByClass(LivingEntity.class, area,
				this::canDamage)) {
			attempted.add(target.getId());
			Vec3d offset = target.getBoundingBox().getCenter().subtract(origin);
			Vec3d horizontal = new Vec3d(offset.x, 0.0D, offset.z);
			double edgeDistance = Math.max(0.0D,
					horizontal.length() - target.getWidth() * 0.5D);
			// Very close targets can sit outside the stale body-facing cone while
			// the golem's head is already looking at them.
			boolean overlapping = edgeDistance <= getWidth() * 0.5D + 0.75D;
			if (edgeDistance <= reach
					&& (overlapping || horizontal.lengthSquared() < 1.0E-6D
							|| horizontal.normalize().dotProduct(forward) >= 0.0D)) {
				if (damageTarget(target, damage)) {
					damaged++;
				}
			}
		}
		LivingEntity primary = getTarget();
		if (primary != null && !attempted.contains(primary.getId())
				&& canDamage(primary)) {
			double distance = primary.getBoundingBox().getCenter().distanceTo(origin);
			if (distance <= reach + primary.getWidth() * 0.5D + 0.75D
					&& damageTarget(primary, damage)) {
				damaged++;
			}
		}
		if (damaged == 0 && !attempted.isEmpty()) {
			TkGun.LOGGER.warn("[tkgun] 铸造傀儡 ATK1 未造成伤害：候选={} 当前目标={} 位置={}",
					attempted.size(), primary == null ? "无" : primary.getName().getString(),
					getBlockPos());
		}
	}

	private void damageAround(Vec3d center, double radius, float damage) {
		Box area = Box.of(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
		for (LivingEntity target : getWorld().getEntitiesByClass(LivingEntity.class, area,
				entity -> canDamage(entity)
						&& entity.getBoundingBox().getCenter().squaredDistanceTo(center)
						<= radius * radius)) {
			damageTarget(target, damage);
		}
	}

	private boolean canDamage(LivingEntity entity) {
		if (entity == this || !entity.isAlive() || entity.isSpectator()) {
			return false;
		}
		if (CombatTargeting.isGolem(entity)) {
			return false;
		}
		if (ownerUuid != null && ownerUuid.equals(entity.getUuid())) {
			return false;
		}
		LivingEntity currentTarget = getTarget();
		return entity instanceof Monster || entity == currentTarget
				|| entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
	}

	private boolean damageTarget(LivingEntity target, float damage) {
		boolean damaged = target.damage(getDamageSources().mobAttack(this), damage);
		if (getTarget() == null && target instanceof Monster) {
			setTarget(target);
		}
		return damaged;
	}

	private Vec3d attackImpactPosition() {
		LivingEntity target = getTarget();
		return target != null && target.isAlive()
				? target.getBoundingBox().getCenter()
				: getEyePos().add(getRotationVec(1.0F).multiply(2.5D));
	}

	private void updatePassiveAnimation() {
		boolean moving = getVelocity().horizontalLengthSquared() > 1.0E-4D
				|| getNavigation().isFollowingPath();
		if (!moving) {
			setVisualState(VisualState.IDLE);
		} else if (getTarget() != null && getNavigation().isFollowingPath()) {
			setVisualState(VisualState.PURSUIT);
		} else {
			setVisualState(VisualState.WALK);
		}
	}

	private double walkAnimationSpeed() {
		if (getVisualState() != VisualState.WALK) {
			return 1.0D;
		}
		double referenceSpeed = Math.max(0.05D, getMovementSpeed() * 0.45D);
		double ratio = getVelocity().horizontalLength() / referenceSpeed;
		return MathHelper.clamp(ratio, 0.35D, 1.0D);
	}

	private void stopHorizontalMovement() {
		Vec3d velocity = getVelocity();
		setVelocity(0.0D, velocity.y, 0.0D);
	}

	private void playWorldSound(SoundEvent event, float volume, float pitch) {
		getWorld().playSound(null, getX(), getY(), getZ(),
				event, SoundCategory.HOSTILE, volume, pitch);
	}

	public static void recordRenderedLocator(int entityId, Vec3d position, long tick) {
		CLIENT_LOCATORS.put(entityId, new LocatorSample(position, tick));
	}

	public static Vec3d clientLocatorPosition(int entityId, long tick) {
		LocatorSample sample = CLIENT_LOCATORS.get(entityId);
		if (sample == null || Math.abs(tick - sample.tick()) > 2L) {
			return null;
		}
		return sample.position();
	}

	public static int currentConvergeGeneration(int entityId) {
		return CLIENT_CONVERGE_GENERATIONS.getOrDefault(entityId, 0);
	}

	private static int beginConverge2(int entityId) {
		int generation = currentConvergeGeneration(entityId) + 1;
		CLIENT_CONVERGE_GENERATIONS.put(entityId, generation);
		return generation;
	}

	/**
	 * GeckoLib's particle event is shared with the animation controller rather
	 * than being an entity-owned effect queue. Drive the authored ATK3 event
	 * times from each entity's own visual clock so one golem can never emit
	 * particles through another golem's locator.
	 */
	public void emitClientAttackLocatorEffects(Vec3d position) {
		if (!getWorld().isClient() || getVisualState() != VisualState.ATTACK_3) {
			return;
		}
		int tick = clientVisualTicks;
		if (tick >= 11 && markAttackEffect(0)) {
			spawnConverge(position, false);
		}
		if (tick >= 17 && markAttackEffect(1)) {
			spawnConverge(position, false);
		}
		if (tick >= 20 && markAttackEffect(2)) {
			spawnConverge(position, false);
		}
		if (tick >= 23 && markAttackEffect(3)) {
			spawnConverge(position, false);
		}
		if (tick >= 28 && markAttackEffect(4)) {
			spawnConverge(position, true);
		}
		if (tick >= 38 && markAttackEffect(5)) {
			getWorld().addParticle(TkGunParticles.ENERGY_XPLO,
					position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
		}
	}

	private boolean markAttackEffect(int flag) {
		int bit = 1 << flag;
		if ((clientAttackEffectMask & bit) != 0) {
			return false;
		}
		clientAttackEffectMask |= bit;
		return true;
	}

	private void spawnConverge(Vec3d position, boolean secondStage) {
		int generation = secondStage
				? beginConverge2(getId()) : currentConvergeGeneration(getId());
		float scale = secondStage ? 2.0F : 1.35F;
		getWorld().addParticle(secondStage
						? TkGunParticles.GOLEM_ENERGY_CONVERGE_2
						: TkGunParticles.GOLEM_ENERGY_CONVERGE,
				position.x, position.y, position.z, getId(), scale, generation);
	}

	private void bindNearestSummoner() {
		PlayerEntity nearest = getWorld().getClosestPlayer(this, 16.0D);
		if (nearest != null && nearest.isAlive()) {
			ownerUuid = nearest.getUuid();
		}
	}

	private PlayerEntity getSummoner() {
		if (ownerUuid == null || !(getWorld() instanceof ServerWorld serverWorld)) {
			return null;
		}
		return serverWorld.getPlayerByUuid(ownerUuid);
	}

	public void setSummoner(UUID summoner) {
		this.ownerUuid = summoner;
	}

	public boolean isSummonedBy(UUID summoner) {
		return summoner != null && summoner.equals(ownerUuid);
	}

	public void beginRelocationSummon(double x, double y, double z, float yaw) {
		setVelocity(0.0D, 0.0D, 0.0D);
		requestTeleport(x, y, z);
		refreshPositionAndAngles(x, y, z, yaw, 0.0F);
		heal(getMaxHealth() * 0.2F);
		attackKind = AttackKind.NONE;
		attackElapsed = 0;
		attackDamageDone = false;
		summonTicks = LANDING_TICKS;
		setAiDisabled(true);
		setVisualState(VisualState.LANDING);
	}

	private void performLandingExplosion() {
		Vec3d center = getPos().add(0.0D, 1.0D, 0.0D);
		if (getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
					center.x, center.y, center.z, 1,
					0.0D, 0.0D, 0.0D, 0.0D);
		}
		double radius = 6.0D;
		Box area = Box.of(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
		for (LivingEntity target : getWorld().getEntitiesByClass(LivingEntity.class, area,
				entity -> entity != this && entity.isAlive() && !entity.isSpectator()
						&& !CombatTargeting.isGolem(entity))) {
			Vec3d targetCenter = target.getBoundingBox().getCenter();
			double distance = targetCenter.distanceTo(center);
			if (distance > radius) {
				continue;
			}
			double falloff = 1.0D - distance / radius;
			float damage = (float) (20.0D * falloff);
			if (damage > 0.0F) {
				target.damage(getDamageSources().mobAttack(this), damage);
			}
			Vec3d push = targetCenter.subtract(center);
			Vec3d horizontal = new Vec3d(push.x, 0.0D, push.z);
			if (horizontal.lengthSquared() < 1.0E-6D) {
				horizontal = new Vec3d(1.0D, 0.0D, 0.0D);
			} else {
				horizontal = horizontal.normalize();
			}
			target.takeKnockback(1.4D * falloff, horizontal.x, horizontal.z);
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (CombatTargeting.isTkGunDamage(source)) {
			return false;
		}
		if (!getWorld().isClient() && summonTicks > 0) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return isSummoning() || super.isInvulnerableTo(source);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("SummonTicks", summonTicks);
		nbt.putInt("Atk2Cooldown", atk2Cooldown);
		nbt.putInt("Atk3Cooldown", atk3Cooldown);
		nbt.putInt("AttackElapsed", attackElapsed);
		nbt.putBoolean("AttackDamageDone", attackDamageDone);
		nbt.putString("AttackKind", attackKind.name());
		nbt.putInt("VisualState", getVisualState().ordinal());
		if (ownerUuid != null) {
			nbt.put("Owner", NbtHelper.fromUuid(ownerUuid));
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		summonTicks = nbt.contains("SummonTicks") ? nbt.getInt("SummonTicks") : 0;
		atk2Cooldown = nbt.getInt("Atk2Cooldown");
		atk3Cooldown = nbt.getInt("Atk3Cooldown");
		attackElapsed = nbt.getInt("AttackElapsed");
		attackDamageDone = nbt.getBoolean("AttackDamageDone");
		ownerUuid = nbt.contains("Owner") ? NbtHelper.toUuid(nbt.get("Owner")) : null;
		try {
			attackKind = AttackKind.valueOf(nbt.getString("AttackKind"));
		} catch (IllegalArgumentException exception) {
			attackKind = AttackKind.NONE;
		}
		VisualState[] values = VisualState.values();
		int visual = MathHelper.clamp(nbt.getInt("VisualState"), 0, values.length - 1);
		setVisualState(values[visual]);
		setAiDisabled(summonTicks > 0 || attackKind != AttackKind.NONE);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Landing starts at y=1600 in the authored clip. A non-zero transition
		// blends from the ground-level bind pose for the first client frames.
		controllers.add(new AnimationController<>(this, "golem", 0,
				this::animationPredicate)
				.setAnimationSpeedHandler(ignored -> walkAnimationSpeed()));
	}

	private PlayState animationPredicate(AnimationState<MoltenGolemEntity> state) {
		return switch (getVisualState()) {
			case IDLE -> state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
			case WALK -> state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
			case PURSUIT -> state.setAndContinue(RawAnimation.begin().thenLoop("walk"));
			case ATTACK_1 -> state.setAndContinue(
					RawAnimation.begin().thenPlayAndHold("attack_1"));
			case ATTACK_2 -> state.setAndContinue(
					RawAnimation.begin().thenPlayAndHold("attack_2"));
			case ATTACK_3 -> state.setAndContinue(
					RawAnimation.begin().thenPlayAndHold("attack_3"));
			case LANDING -> state.setAndContinue(
					RawAnimation.begin().thenPlayAndHold("landing"));
		};
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	private static final class FollowSummonerGoal extends Goal {
		private final MoltenGolemEntity golem;
		private final double speed;
		private final double startDistance;
		private final double stopDistance;
		private PlayerEntity owner;

		private FollowSummonerGoal(MoltenGolemEntity golem, double speed,
								   double startDistance, double stopDistance) {
			this.golem = golem;
			this.speed = speed;
			this.startDistance = startDistance;
			this.stopDistance = stopDistance;
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			if (golem.isSummoning() || golem.isCombatAttacking()) {
				return false;
			}
			owner = golem.getSummoner();
			if (owner == null || !owner.isAlive() || owner.isSpectator()) {
				return false;
			}
			double distance = golem.squaredDistanceTo(owner);
			return distance > startDistance * startDistance
					&& distance < 32.0D * 32.0D;
		}

		@Override
		public boolean shouldContinue() {
			return owner != null && owner.isAlive()
					&& !golem.isSummoning() && !golem.isCombatAttacking()
					&& golem.squaredDistanceTo(owner) > stopDistance * stopDistance;
		}

		@Override
		public void start() {
			golem.getNavigation().startMovingTo(owner, speed);
		}

		@Override
		public void tick() {
			golem.getLookControl().lookAt(owner, 10.0F, 10.0F);
			if (golem.getNavigation().isIdle()) {
				golem.getNavigation().startMovingTo(owner, speed);
			}
		}

		@Override
		public void stop() {
			golem.getNavigation().stop();
			owner = null;
		}
	}
}
