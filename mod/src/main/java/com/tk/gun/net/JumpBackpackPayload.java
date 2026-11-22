package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client input for jump control and the camera-relative horizontal thrust vector. */
public record JumpBackpackPayload(boolean pressed, float directionX,
		float directionZ) implements CustomPayload {
	public static final CustomPayload.Id<JumpBackpackPayload> ID =
			new CustomPayload.Id<>(TkGun.id("jump_backpack"));

	public static final PacketCodec<RegistryByteBuf, JumpBackpackPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeBoolean(value.pressed());
						buffer.writeFloat(value.directionX());
						buffer.writeFloat(value.directionZ());
					},
					buffer -> new JumpBackpackPayload(buffer.readBoolean(),
							buffer.readFloat(), buffer.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
