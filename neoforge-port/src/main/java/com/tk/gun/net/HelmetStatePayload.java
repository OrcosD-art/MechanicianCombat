package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/** Server-authoritative helmet animation state broadcast to every nearby viewer. */
public record HelmetStatePayload(
		UUID playerUuid,
		boolean expanded,
		long transitionStartedAt,
		int transitionDurationTicks) implements CustomPayload {
	public static final CustomPayload.Id<HelmetStatePayload> ID =
			new CustomPayload.Id<>(TkGun.id("helmet_state"));

	public static final PacketCodec<RegistryByteBuf, HelmetStatePayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeUuid(value.playerUuid());
						buffer.writeBoolean(value.expanded());
						buffer.writeLong(value.transitionStartedAt());
						buffer.writeVarInt(Math.max(0,
								value.transitionDurationTicks()));
					},
					buffer -> new HelmetStatePayload(
							buffer.readUuid(),
							buffer.readBoolean(),
							buffer.readLong(),
							buffer.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
