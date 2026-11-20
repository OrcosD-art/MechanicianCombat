package net.fabricmc.fabric.api.object.builder.v1.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class FabricDefaultAttributeRegistry {
	private static final Map<EntityType<? extends LivingEntity>,
			Supplier<DefaultAttributeContainer>>
			DEFAULTS = new LinkedHashMap<>();

	private FabricDefaultAttributeRegistry() {
	}

	public static <T extends LivingEntity> void register(EntityType<T> type,
			DefaultAttributeContainer attributes) {
		DEFAULTS.put(type, () -> attributes);
	}

	public static <T extends LivingEntity> void register(EntityType<T> type,
			DefaultAttributeContainer.Builder attributes) {
		DEFAULTS.put(type, attributes::build);
	}

	public static <T extends LivingEntity> void register(EntityType<T> type,
			Supplier<DefaultAttributeContainer> attributes) {
		DEFAULTS.put(type, attributes);
	}

	public static Map<EntityType<? extends LivingEntity>,
			Supplier<DefaultAttributeContainer>>
			defaults() {
		return DEFAULTS;
	}
}
