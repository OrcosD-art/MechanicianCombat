package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Authoritative start of the hunter spear deployment animation. */
public record HunterSpearSkillPayload(
		int playerEntityId, long startedAt, int durationTicks)
		implements CustomPayload {
	public static final CustomPayload.Id<HunterSpearSkillPayload> ID =
			new CustomPayload.Id<>(TkGun.id("hunter_spear_skill"));

	public static final PacketCodec<RegistryByteBuf, HunterSpearSkillPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeVarInt(value.playerEntityId());
						buffer.writeLong(value.startedAt());
						buffer.writeVarInt(value.durationTicks());
					},
					buffer -> new HunterSpearSkillPayload(
							buffer.readVarInt(), buffer.readLong(),
							buffer.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
