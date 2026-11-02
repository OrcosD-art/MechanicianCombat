package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client request to pre-input Z during Silent Storm's jump and queue the
 * authored fall attack at the transition from ascent to descent.
 */
public record GunFallAttackPayload() implements CustomPayload {
	public static final CustomPayload.Id<GunFallAttackPayload> ID =
			new CustomPayload.Id<>(TkGun.id("fall_attack_input"));

	public static final PacketCodec<RegistryByteBuf, GunFallAttackPayload> CODEC =
			PacketCodec.of((value, buf) -> { }, buf -> new GunFallAttackPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
