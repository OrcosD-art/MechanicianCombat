package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-authored WASD intent used by the Kinetic Sword movement-bound boost. */
public record KineticSwordMovementPayload(boolean moving) implements CustomPayload {
	public static final CustomPayload.Id<KineticSwordMovementPayload> ID =
			new CustomPayload.Id<>(TkGun.id("kinetic_sword_movement"));
	public static final PacketCodec<RegistryByteBuf, KineticSwordMovementPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> buf.writeBoolean(value.moving()),
					buf -> new KineticSwordMovementPayload(buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
