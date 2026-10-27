package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server acknowledgement used to release the client fire-request latch. */
public record CombatBackpackFireResultPayload() implements CustomPayload {
	public static final CustomPayload.Id<CombatBackpackFireResultPayload> ID =
			new CustomPayload.Id<>(TkGun.id("backpack_firework_result"));

	public static final PacketCodec<RegistryByteBuf, CombatBackpackFireResultPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
					},
					buf -> new CombatBackpackFireResultPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
