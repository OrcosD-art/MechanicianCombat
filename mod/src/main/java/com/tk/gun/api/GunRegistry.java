package com.tk.gun.api;

import com.tk.gun.item.BowgunItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Public registration point for gun types.
 *
 * <p>Call {@link #register(GunDefinition, Item.Settings)} during mod initialization.
 * Replacing only render assets can be done with {@link #replace(GunDefinition)} while
 * keeping the same identifier; already-created ItemStacks then resolve the new data.
 */
public final class GunRegistry {
	private static final Map<Identifier, GunDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();

	public static Item register(GunDefinition definition, Item.Settings settings) {
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(settings, "settings");
		if (DEFINITIONS.containsKey(definition.id())
				|| Registries.ITEM.containsId(definition.id())) {
			throw new IllegalArgumentException("gun id already registered: " + definition.id());
		}
		Item item = new BowgunItem(definition.id(), settings);
		DEFINITIONS.put(definition.id(), definition);
		ITEMS.put(definition.id(), item);
		try {
			Registry.register(Registries.ITEM, definition.id(), item);
		} catch (RuntimeException failure) {
			DEFINITIONS.remove(definition.id());
			ITEMS.remove(definition.id());
			throw failure;
		}
		return item;
	}

	/** Replaces gameplay/render data without replacing the registered Item. */
	public static GunDefinition replace(GunDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		if (!DEFINITIONS.containsKey(definition.id())) {
			throw new IllegalArgumentException("gun is not registered: " + definition.id());
		}
		return DEFINITIONS.put(definition.id(), definition);
	}

	public static GunDefinition get(Identifier id) {
		GunDefinition definition = id == null ? null : DEFINITIONS.get(id);
		if (definition != null) {
			return definition;
		}
		if (FortressDestroyerGun.DEFINITION.id().equals(id)) {
			return FortressDestroyerGun.DEFINITION;
		}
		throw new IllegalArgumentException("gun is not registered: " + id);
	}

	public static GunDefinition get(ItemStack stack) {
		if (stack.getItem() instanceof BowgunItem gun) {
			return get(gun.gunId());
		}
		return null;
	}

	public static Item item(Identifier id) {
		return ITEMS.get(id);
	}

	public static Collection<Item> items() {
		return Collections.unmodifiableCollection(ITEMS.values());
	}

	private GunRegistry() {
	}
}
