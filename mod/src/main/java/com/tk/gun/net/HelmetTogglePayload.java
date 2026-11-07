package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client request to expand or fold the integrated Mechanician helmet. */
public record HelmetTogglePayload() implements CustomPayload {
	public static final CustomPayload.Id<HelmetTogglePayload> ID =
			new CustomPayload.Id<>(TkGun.id("helmet_toggle"));

	public static final PacketCodec<RegistryByteBuf, HelmetTogglePayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
					},
					buffer -> new HelmetTogglePayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
