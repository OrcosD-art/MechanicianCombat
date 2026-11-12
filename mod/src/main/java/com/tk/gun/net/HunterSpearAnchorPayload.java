package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-authored deployment marker from the spear animation. */
public record HunterSpearAnchorPayload(
		double x, double y, double z, float yaw) implements CustomPayload {
	public static final CustomPayload.Id<HunterSpearAnchorPayload> ID =
			new CustomPayload.Id<>(TkGun.id("hunter_spear_anchor"));

	public static final PacketCodec<RegistryByteBuf, HunterSpearAnchorPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeDouble(value.x());
						buffer.writeDouble(value.y());
						buffer.writeDouble(value.z());
						buffer.writeFloat(value.yaw());
					},
					buffer -> new HunterSpearAnchorPayload(
							buffer.readDouble(), buffer.readDouble(),
							buffer.readDouble(), buffer.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
