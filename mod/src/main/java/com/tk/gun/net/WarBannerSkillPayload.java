package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Authoritative War Banner player-animation start. */
public record WarBannerSkillPayload(
		int playerEntityId,
		long startedAt,
		int durationTicks) implements CustomPayload {
	public static final CustomPayload.Id<WarBannerSkillPayload> ID =
			new CustomPayload.Id<>(TkGun.id("war_banner_skill"));

	public static final PacketCodec<RegistryByteBuf, WarBannerSkillPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeVarInt(value.playerEntityId());
						buffer.writeLong(value.startedAt());
						buffer.writeVarInt(value.durationTicks());
					},
					buffer -> new WarBannerSkillPayload(
							buffer.readVarInt(), buffer.readLong(),
							buffer.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
