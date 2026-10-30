package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Starts a locator-driven backpack particle burst on every nearby client. */
public record CombatBackpackParticlePayload(int playerId, double x, double y, double z)
		implements CustomPayload {
	public static final Id<CombatBackpackParticlePayload> ID =
			new Id<>(TkGun.id("backpack_particle"));

	public static final PacketCodec<RegistryByteBuf, CombatBackpackParticlePayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeVarInt(value.playerId());
						buffer.writeDouble(value.x());
						buffer.writeDouble(value.y());
						buffer.writeDouble(value.z());
					},
					buffer -> new CombatBackpackParticlePayload(
							buffer.readVarInt(), buffer.readDouble(),
							buffer.readDouble(), buffer.readDouble()));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
