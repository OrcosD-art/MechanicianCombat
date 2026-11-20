package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client input for the jump backpack's jump-key start and hold-to-climb control. */
public record JumpBackpackPayload(boolean pressed) implements CustomPayload {
	public static final CustomPayload.Id<JumpBackpackPayload> ID =
			new CustomPayload.Id<>(TkGun.id("jump_backpack"));

	public static final PacketCodec<RegistryByteBuf, JumpBackpackPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> buffer.writeBoolean(value.pressed()),
					buffer -> new JumpBackpackPayload(buffer.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
