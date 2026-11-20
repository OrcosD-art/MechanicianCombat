package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server request for Silent Storm's accelerated jump skill.
 * The empty payload is intentional: the server validates the gun, state and
 * movement window from its own authoritative player data.
 */
public record GunJumpPayload() implements CustomPayload {
	public static final CustomPayload.Id<GunJumpPayload> ID =
			new CustomPayload.Id<>(TkGun.id("skill_jump"));

	public static final PacketCodec<RegistryByteBuf, GunJumpPayload> CODEC =
			PacketCodec.of((value, buf) -> { }, buf -> new GunJumpPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
