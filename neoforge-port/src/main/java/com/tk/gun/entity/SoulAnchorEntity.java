package com.tk.gun.entity;

import com.tk.gun.TkGunServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Fishing-bobber-like soul anchor with flight, anchored and retracting states. */
public final class SoulAnchorEntity extends PersistentProjectileEntity
		implements GeoEntity {
	private final AnimatableInstanceCache animationCache =
			GeckoLibUtil.createInstanceCache(this);
	public enum State {
		FLYING,
		ANCHORED,
		RETRACTING
	}

	private static final TrackedData<Integer> STATE =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> OWNER_ENTITY_ID =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HOOKED_ENTITY_ID =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> FLIGHT_DIR_X =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> FLIGHT_DIR_Y =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> FLIGHT_DIR_Z =
			DataTracker.registerData(SoulAnchorEntity.class,
					TrackedDataHandlerRegistry.FLOAT);

	public SoulAnchorEntity(EntityType<? extends SoulAnchorEntity> type,
			World world) {
		super(type, world);
		this.pickupType = PickupPermission.DISALLOWED;
	}

	public SoulAnchorEntity(EntityType<? extends SoulAnchorEntity> type,
			World world, LivingEntity owner) {
		this(type, world);
		setOwner(owner);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, State.FLYING.ordinal());
		builder.add(OWNER_ENTITY_ID, -1);
		builder.add(HOOKED_ENTITY_ID, -1);
		builder.add(FLIGHT_DIR_X, 0.0F);
		builder.add(FLIGHT_DIR_Y, 0.0F);
		builder.add(FLIGHT_DIR_Z, 1.0F);
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		if (!getWorld().isClient) {
			getDataTracker().set(OWNER_ENTITY_ID,
					owner == null ? -1 : owner.getId());
		}
	}

	public Entity trackedOwner() {
		int ownerId = getDataTracker().get(OWNER_ENTITY_ID);
		return ownerId < 0 ? getOwner() : getWorld().getEntityById(ownerId);
	}

	public State anchorState() {
		int ordinal = MathHelper.clamp(getDataTracker().get(STATE),
				0, State.values().length - 1);
		return State.values()[ordinal];
	}

	public boolean isRetracting() {
		return anchorState() == State.RETRACTING;
	}

	public Entity hookedEntity() {
		int entityId = getDataTracker().get(HOOKED_ENTITY_ID);
		return entityId < 0 ? null : getWorld().getEntityById(entityId);
	}

	public int hookedEntityId() {
		return getDataTracker().get(HOOKED_ENTITY_ID);
	}

	public Vec3d flightDirection() {
		Vec3d direction = new Vec3d(
				getDataTracker().get(FLIGHT_DIR_X),
				getDataTracker().get(FLIGHT_DIR_Y),
				getDataTracker().get(FLIGHT_DIR_Z));
		return direction.lengthSquared() < 1.0E-6D
				? new Vec3d(0.0D, 0.0D, 1.0D) : direction.normalize();
	}

	@Override
	public void tick() {
		if (anchorState() == State.ANCHORED) {
			age++;
			setVelocity(Vec3d.ZERO);
			velocityModified = true;
			Entity hooked = hookedEntity();
			if (hooked != null && hooked.isAlive()) {
				Vec3d hookedCenter = hooked.getBoundingBox().getCenter();
				setPosition(hookedCenter.x, hookedCenter.y, hookedCenter.z);
				refreshPosition();
			}
			return;
		}
		if (anchorState() == State.RETRACTING) {
			Entity owner = trackedOwner();
			if (owner == null || !owner.isAlive()) {
				discard();
				return;
			}
			Entity hooked = hookedEntity();
			if (hooked != null && hooked.isAlive()) {
				Vec3d hookedCenter = hooked.getBoundingBox().getCenter();
				setPosition(hookedCenter.x, hookedCenter.y, hookedCenter.z);
				refreshPosition();
				setVelocity(Vec3d.ZERO);
				velocityModified = true;
				pullHookedEntity(owner, hooked);
				if (owner.squaredDistanceTo(hooked) <= 6.25D) {
					discard();
				}
				return;
			}
			Vec3d target = owner.getEyePos().add(0.0D,
					-owner.getStandingEyeHeight() * 0.35D, 0.0D);
			Vec3d delta = target.subtract(getPos());
			if (delta.lengthSquared() <= 2.25D) {
				discard();
				return;
			}
			setNoClip(true);
			setVelocity(delta.normalize().multiply(3.2D));
			velocityModified = true;
			super.tick();
			return;
		}

		Entity owner = trackedOwner();
		if (!getWorld().isClient
				&& (owner == null || !owner.isAlive() || age > 200)) {
			if (owner != null && owner.isAlive()) {
				retrieve(owner);
			} else {
				discard();
			}
			return;
		}
		super.tick();
	}

	@Override
	protected void onBlockHit(net.minecraft.util.hit.BlockHitResult hit) {
		anchorAt(hit.getPos().subtract(getVelocity().normalize()
				.multiply(0.08D)), -1);
	}

	@Override
	protected void onEntityHit(net.minecraft.util.hit.EntityHitResult hit) {
		anchorAt(hit.getPos().subtract(getVelocity().normalize()
				.multiply(0.2D)), hit.getEntity().getId());
	}

	private void anchorAt(Vec3d position, int hookedEntityId) {
		if (getWorld().isClient) {
			return;
		}
		Vec3d direction = getVelocity();
		if (direction.lengthSquared() > 1.0E-6D) {
			direction = direction.normalize();
			getDataTracker().set(FLIGHT_DIR_X, (float) direction.x);
			getDataTracker().set(FLIGHT_DIR_Y, (float) direction.y);
			getDataTracker().set(FLIGHT_DIR_Z, (float) direction.z);
		}
		setPosition(position.x, position.y, position.z);
		setVelocity(Vec3d.ZERO);
		velocityModified = true;
		setNoClip(true);
		getDataTracker().set(HOOKED_ENTITY_ID, hookedEntityId);
		getDataTracker().set(STATE, State.ANCHORED.ordinal());
	}

	private void pullHookedEntity(Entity owner, Entity hooked) {
		// Vanilla fishing bobbers add one tenth of the owner-to-hook vector to
		// the hooked entity's existing velocity.
		Vec3d pull = owner.getPos().subtract(getPos()).multiply(0.1D);
		hooked.setVelocity(hooked.getVelocity().add(pull));
		hooked.velocityModified = true;
		hooked.fallDistance = 0.0F;
	}

	public void retrieve(Entity owner) {
		if (getWorld().isClient
				|| anchorState() == State.RETRACTING) {
			return;
		}
		setOwner(owner);
		setNoClip(true);
		getDataTracker().set(STATE, State.RETRACTING.ordinal());
		Vec3d delta = owner.getEyePos().subtract(getPos());
		if (delta.lengthSquared() > 1.0E-6D) {
			setVelocity(delta.normalize().multiply(3.2D));
			velocityModified = true;
		}
	}

	/** Cancel any entity link, then return the anchor without pulling the target. */
	public void cancelRetrieve(Entity owner) {
		if (getWorld().isClient) {
			return;
		}
		getDataTracker().set(HOOKED_ENTITY_ID, -1);
		if (anchorState() != State.RETRACTING) {
			retrieve(owner);
		}
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		// This is a fishing-hook equivalent, not a throwable item. Returning an
		// empty stack prevents vanilla projectile pickup/drop paths from ever
		// producing a second test tool when the anchor is recalled.
		return ItemStack.EMPTY;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putByte("AnchorState", (byte) anchorState().ordinal());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		int state = MathHelper.clamp(nbt.getByte("AnchorState"),
				0, State.values().length - 1);
		getDataTracker().set(STATE, state);
	}

	@Override
	public boolean canHit() {
		return anchorState() == State.FLYING;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// The projectile is drawn from a fixed authored pose.
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	@Override
	protected double getGravity() {
		// Vanilla fishing bobbers apply 0.03 blocks/tick downward gravity.
		return 0.06D;
	}
}
