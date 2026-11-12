package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-rendered War Banner locator world position and camera yaw. */
public record WarBannerAnchorPayload(
		double x, double y, double z, float cameraYaw)
		implements CustomPayload {
	public static final CustomPayload.Id<WarBannerAnchorPayload> ID =
			new CustomPayload.Id<>(TkGun.id("war_banner_anchor"));

	public static final PacketCodec<RegistryByteBuf, WarBannerAnchorPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeDouble(value.x());
						buffer.writeDouble(value.y());
						buffer.writeDouble(value.z());
						buffer.writeFloat(value.cameraYaw());
					},
					buffer -> new WarBannerAnchorPayload(
							buffer.readDouble(), buffer.readDouble(),
							buffer.readDouble(), buffer.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
