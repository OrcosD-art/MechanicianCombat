package com.tk.gun.entity;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGunServer;
import com.tk.gun.mixin.TridentEntityAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 熔铁弩矢专用三叉戟实体：继承原版三叉戟的伤害、拾取、忠诚等逻辑，
 * 但使用独立的实体类型，以便客户端渲染资源模型。
 */
public class MoltenTridentEntity extends TridentEntity {
	private static final TrackedData<Boolean> GUN_FIRED =
			DataTracker.registerData(MoltenTridentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> STOPPED =
			DataTracker.registerData(MoltenTridentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private final Set<UUID> tkgun$hitEntities = new HashSet<>();

	public MoltenTridentEntity(EntityType<? extends MoltenTridentEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(GUN_FIRED, false);
		builder.add(STOPPED, false);
	}

	public void setGunFired(boolean gunFired) {
		this.getDataTracker().set(GUN_FIRED, gunFired);
	}

	public boolean isGunFired() {
		return this.getDataTracker().get(GUN_FIRED);
	}

	public boolean isStopped() {
		return this.getDataTracker().get(STOPPED);
	}

	private void setStopped(boolean stopped) {
		this.getDataTracker().set(STOPPED, stopped);
	}

	@Override
	protected EntityHitResult getEntityCollision(Vec3d currentPosition, Vec3d nextPosition) {
		// 关闭原版单体碰撞，实体命中统一走下面的 3 格直径胶囊判定，才能穿透。
		return null;
	}

	@Override
	protected void onBlockHit(BlockHitResult blockHitResult) {
		boolean firstBlockHit = !isStopped();
		super.onBlockHit(blockHitResult);
		setStopped(true);
		if (firstBlockHit) {
			sendGunHitMarker();
		}
		// 只在服务端判定一次；命中方块停止后 50% 消失，否则留在原地可拾取。
		if (!getWorld().isClient && getRandom().nextFloat() < 0.5F) {
			discard();
		}
	}

	@Override
	public void tick() {
		Vec3d before = this.getPos();
		super.tick();
		if (this.getWorld().isClient || this.isRemoved()) {
			return;
		}
		Vec3d after = this.getPos();
		if (before.squaredDistanceTo(after) < 1.0E-8D) {
			return;
		}
		Box search = new Box(before, after).expand(2.0D);
		for (Entity entity : this.getWorld().getOtherEntities(this, search,
				e -> e.isAlive() && !e.isSpectator() && e != this.getOwner()
						&& e.canHit() && !CombatTargeting.isGolem(e))) {
			if (this.tkgun$hitEntities.contains(entity.getUuid())) {
				continue;
			}
			if (tkgun$horizontalDistanceToSegment(
					entity.getBoundingBox().getCenter(), before, after) > 2.0D) {
				continue;
			}
			this.tkgun$hitEntities.add(entity.getUuid());
			Vec3d velocity = this.getVelocity();
			this.onEntityHit(new EntityHitResult(entity, entity.getBoundingBox().getCenter()));
			sendGunHitMarker();
			// onEntityHit 会设置 dealtDamage=true；穿透期间清掉，
			// 避免带忠诚附魔时第一只实体命中后立刻折返。
			((TridentEntityAccessor) (Object) this).tkgun$setDealtDamage(false);
			this.setVelocity(velocity);
		}
	}

	private void sendGunHitMarker() {
		if (!getWorld().isClient && isGunFired()
				&& getOwner() instanceof ServerPlayerEntity owner) {
			ServerPlayNetworking.send(owner, new com.tk.gun.net.GunHitMarkerPayload());
		}
	}

	private static double tkgun$horizontalDistanceToSegment(Vec3d point, Vec3d start, Vec3d end) {
		Vec3d delta = end.subtract(start);
		double lengthSquared = delta.x * delta.x + delta.z * delta.z;
		if (lengthSquared < 1.0E-8D) {
			double dx = point.x - start.x;
			double dz = point.z - start.z;
			return Math.sqrt(dx * dx + dz * dz);
		}
		double t = MathHelper.clamp(
				((point.x - start.x) * delta.x + (point.z - start.z) * delta.z)
						/ lengthSquared,
				0.0D, 1.0D);
		double dx = point.x - (start.x + delta.x * t);
		double dz = point.z - (start.z + delta.z * t);
		return Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("TkGunFired", isGunFired());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setGunFired(nbt.getBoolean("TkGunFired"));
		setStopped(this.inGround);
	}

	public MoltenTridentEntity(EntityType<? extends MoltenTridentEntity> type, World world,
							   LivingEntity owner, ItemStack stack) {
		super(type, world);
		this.setOwner(owner);
		ItemStack projectileStack = stack.copyWithCount(1);
		projectileStack.remove(DataComponentTypes.ENCHANTMENTS);
		projectileStack.remove(DataComponentTypes.STORED_ENCHANTMENTS);
		this.setStack(projectileStack);
		this.refreshPositionAndAngles(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ(),
				owner.getYaw(), owner.getPitch());
		this.tkgun$syncStackData(projectileStack);
	}

	@Override
	public ItemStack getWeaponStack() {
		ItemStack weapon = super.getWeaponStack();
		if (weapon.contains(DataComponentTypes.ENCHANTMENTS)
				|| weapon.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
			weapon = weapon.copy();
			weapon.remove(DataComponentTypes.ENCHANTMENTS);
			weapon.remove(DataComponentTypes.STORED_ENCHANTMENTS);
		}
		return weapon;
	}

	private void tkgun$syncStackData(ItemStack stack) {
		TridentEntityAccessor accessor = (TridentEntityAccessor) (Object) this;
		byte loyalty = (byte) MathHelper.clamp(accessor.tkgun$getLoyalty(stack), 0, 127);
		this.getDataTracker().set(TridentEntityAccessor.tkgun$getLoyaltyData(), loyalty);
		this.getDataTracker().set(TridentEntityAccessor.tkgun$getEnchantedData(), stack.hasGlint());
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		return TkGunServer.BOLT == null ? ItemStack.EMPTY : new ItemStack(TkGunServer.BOLT);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		Entity hitEntity = result.getEntity();
		if (CombatTargeting.isGolem(hitEntity)) {
			return;
		}
		float totalHealthBefore = hitEntity instanceof LivingEntity living
				? living.getHealth() + living.getAbsorptionAmount() : Float.NaN;
		int hurtTimeBefore = hitEntity instanceof LivingEntity living
				? living.hurtTime : -1;
		super.onEntityHit(result);
		if (hitEntity instanceof LivingEntity living
				&& getWorld() instanceof ServerWorld world
				&& (living.getHealth() + living.getAbsorptionAmount() < totalHealthBefore
				|| living.hurtTime > hurtTimeBefore || !living.isAlive())) {
			Vec3d impact = result.getPos();
			world.spawnParticles(com.tk.gun.TkGunParticles.GRAPESHOT_IMPACT,
					impact.x, impact.y, impact.z, 1,
					0.0D, 0.0D, 0.0D, 0.0D);
		}
		// 自定义实体类型不满足原版引雷的 direct_attacker=minecraft:trident 条件，这里补上。
		tkgun$applyChanneling(result.getEntity(), null, getWeaponStack());
	}

	@Override
	protected void onBlockHitEnchantmentEffects(ServerWorld world, BlockHitResult blockHitResult,
												ItemStack weaponStack) {
		// 不调用 vanilla 的 block-hit consumer；它可能触发 replace/discard，
		// 让自定义箭矢命中方块后随机消失。引雷效果由下面手动结算。
		tkgun$applyChanneling(null, blockHitResult, weaponStack);
	}

	private void tkgun$applyChanneling(Entity target, BlockHitResult blockHit, ItemStack weaponStack) {
		if (!(getWorld() instanceof ServerWorld serverWorld) || !serverWorld.isThundering()) {
			return;
		}
		RegistryEntry<Enchantment> channeling = serverWorld.getRegistryManager()
				.get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.CHANNELING).orElse(null);
		if (channeling == null || EnchantmentHelper.getLevel(channeling, weaponStack) <= 0) {
			return;
		}
		BlockPos pos;
		if (target != null) {
			pos = target.getBlockPos();
		} else if (blockHit != null
				&& serverWorld.getBlockState(blockHit.getBlockPos()).isOf(Blocks.LIGHTNING_ROD)) {
			pos = blockHit.getBlockPos();
		} else {
			return;
		}
		if (!serverWorld.isSkyVisibleAllowingSea(pos)) {
			return;
		}
		LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(serverWorld);
		if (lightning != null) {
			lightning.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
					0.0F, 0.0F);
			serverWorld.spawnEntity(lightning);
		}
		serverWorld.playSound(null, pos, SoundEvents.ITEM_TRIDENT_THUNDER.value(),
				SoundCategory.PLAYERS, 5.0F, 1.0F);
	}
}
