package com.tk.gun.neoforge;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

public final class NeoForgeClientNetworkBridge {
	private NeoForgeClientNetworkBridge() {
	}

	public static void dispatch(CustomPayload payload) {
		ClientPlayNetworking.dispatch(payload);
	}
}
