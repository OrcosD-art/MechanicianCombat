package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Shooter-only camera feedback that never freezes an entity or animation timeline. */
public record CameraShakePayload(int ticks, boolean critical,
								 float intensityScale)
		implements CustomPayload {
	public static final Id<CameraShakePayload> ID =
			new Id<>(TkGun.id("camera_shake"));

	public static final PacketCodec<RegistryByteBuf, CameraShakePayload> CODEC =
			PacketCodec.of(CameraShakePayload::write, CameraShakePayload::read);

	private static void write(CameraShakePayload payload, RegistryByteBuf buffer) {
		buffer.writeVarInt(payload.ticks());
		buffer.writeBoolean(payload.critical());
		buffer.writeFloat(payload.intensityScale());
	}

	private static CameraShakePayload read(RegistryByteBuf buffer) {
		return new CameraShakePayload(buffer.readVarInt(), buffer.readBoolean(),
				buffer.readFloat());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
