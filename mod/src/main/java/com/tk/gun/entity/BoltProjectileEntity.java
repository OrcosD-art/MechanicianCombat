package com.tk.gun.entity;

import com.tk.gun.CombatTargeting;
import com.tk.gun.TkGun;
import com.tk.gun.TkGunServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 熔铁弩矢的**弹丸实体**（tkgun:bolt_projectile）。
 *
 * <p>⚠️ 2026-10-12 用户反复强调："**不要用弓箭当弹药**"。之前偷懒直接复用 {@code minecraft:arrow}，
 * 结果实体类型、死亡信息、掉落物全是"箭"。现在换成我们自己的类型：
 * <ul>
 *   <li>实体 = {@code tkgun:bolt_projectile}（用 {@code /data get entity @e[type=minecraft:arrow]} 查不到）；</li>
 *   <li>命中伤害走**自己的 damage_type**（{@code tkgun:bolt}）→ 死亡信息是"被熔铁弩矢打穿"，不再出现"箭"；</li>
 *   <li>客户端渲染器什么都不画（见 {@code client/entity/BoltProjectileRenderer}），飞行表现全靠曳光粒子。</li>
 * </ul>
 * 伤害口径与原版箭一致：**速度 × damage 字段**（{@link PersistentProjectileEntity#getDamage()}）。
 */
public class BoltProjectileEntity extends PersistentProjectileEntity {
	public BoltProjectileEntity(EntityType<? extends BoltProjectileEntity> type, World world) {
		super(type, world);
	}

	public BoltProjectileEntity(EntityType<? extends BoltProjectileEntity> type, LivingEntity owner, World world,
								ItemStack stack, ItemStack weapon) {
		super(type, owner, world, stack, weapon);
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		return new ItemStack(TkGunServer.BOLT);
	}

	/** 弩矢自己的伤害类型（数据在 data/tkgun/damage_type/bolt.json，文案在 lang 里）。 */
	public static RegistryKey<DamageType> damageTypeKey() {
		return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(TkGun.MOD_ID, "bolt"));
	}

	/** DamageSources.create(...) 是 private，所以直接 new DamageSource(entry, 弹丸, 射手)。 */
	private DamageSource boltDamageSource() {
		Entity owner = getOwner();
		RegistryEntry<DamageType> entry = getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE)
				.getEntry(damageTypeKey()).map(e -> (RegistryEntry<DamageType>) e).orElse(null);
		if (entry == null) {
			return getDamageSources().arrow(this, owner);   // 数据没加载出来时的兜底
		}
		return new DamageSource(entry, this, owner == null ? this : owner);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		Entity target = result.getEntity();
		if (CombatTargeting.isGolem(target)) {
			return;
		}
		DamageSource source = boltDamageSource();
		// 口径照抄原版箭：伤害 = 速度 × damage 字段（我们生成弹丸时已经折算过）
		double speed = getVelocity().length();
		int amount = MathHelper.ceil(MathHelper.clamp(speed * getDamage(), 0.0D, Integer.MAX_VALUE));
		if (target instanceof LivingEntity living) {
			this.knockback(living, source);
		}
		target.damage(source, (float) amount);
		// 子弹打进目标就没了（不做穿透），下一 tick 由服务端的曳光表补命中爆裂粒子
		this.remove(RemovalReason.KILLED);
	}
}
