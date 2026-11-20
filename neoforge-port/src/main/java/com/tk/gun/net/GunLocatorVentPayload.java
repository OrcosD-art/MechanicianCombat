package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Starts the Silent Storm heat-vent effect at the rendered gun locator. */
public record GunLocatorVentPayload(int playerId, double x, double y, double z)
		implements CustomPayload {
	public static final CustomPayload.Id<GunLocatorVentPayload> ID =
			new CustomPayload.Id<>(TkGun.id("gun_locator_vent"));

	public static final PacketCodec<RegistryByteBuf, GunLocatorVentPayload> CODEC =
			PacketCodec.of((value, buffer) -> {
				buffer.writeVarInt(value.playerId());
				buffer.writeDouble(value.x());
				buffer.writeDouble(value.y());
				buffer.writeDouble(value.z());
			}, buffer -> new GunLocatorVentPayload(
					buffer.readVarInt(), buffer.readDouble(),
					buffer.readDouble(), buffer.readDouble()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
