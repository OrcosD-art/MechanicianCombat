package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.option.KeyBinding;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KeyBindingHelper {
	public static final List<KeyBinding> KEY_BINDINGS = new CopyOnWriteArrayList<>();

	private KeyBindingHelper() {
	}

	public static KeyBinding registerKeyBinding(KeyBinding binding) {
		KEY_BINDINGS.add(binding);
		return binding;
	}
}
