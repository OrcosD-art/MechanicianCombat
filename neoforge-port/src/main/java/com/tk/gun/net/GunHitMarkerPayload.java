package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-to-shooter pulse that appears together with the impact confirmation sound. */
public record GunHitMarkerPayload() implements CustomPayload {
	public static final CustomPayload.Id<GunHitMarkerPayload> ID =
			new CustomPayload.Id<>(TkGun.id("hit_marker"));

	public static final PacketCodec<RegistryByteBuf, GunHitMarkerPayload> CODEC =
			PacketCodec.of((value, buf) -> {
			}, buf -> new GunHitMarkerPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
