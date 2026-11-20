package net.fabricmc.fabric.api.entity.event.v1;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerLivingEntityEvents {
	public interface AllowDamage {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}

	public interface AllowDeath {
		boolean allowDeath(LivingEntity entity, DamageSource source, float amount);
	}

	public interface AfterDeath {
		void afterDeath(LivingEntity entity, DamageSource source);
	}

	public interface AfterDamage {
		void afterDamage(LivingEntity entity, DamageSource source, float baseDamage,
				float damageTaken, boolean blocked);
	}

	public static final Event<AllowDamage> ALLOW_DAMAGE = new Event<>();
	public static final Event<AllowDeath> ALLOW_DEATH = new Event<>();
	public static final Event<AfterDeath> AFTER_DEATH = new Event<>();
	public static final Event<AfterDamage> AFTER_DAMAGE = new Event<>();

	private ServerLivingEntityEvents() {
	}

	public static final class Event<T> {
		private final List<T> listeners = new CopyOnWriteArrayList<>();

		public void register(T listener) {
			listeners.add(listener);
		}

		public List<T> listeners() {
			return listeners;
		}
	}
}
