package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Press/release edges for the server-authoritative close-combat combo. */
public record GunMeleePayload(boolean pressed, boolean movingForward,
		boolean special, double effectX, double effectY, double effectZ)
		implements CustomPayload {
	public static final CustomPayload.Id<GunMeleePayload> ID =
			new CustomPayload.Id<>(TkGun.id("melee_attack"));

	public static final PacketCodec<RegistryByteBuf, GunMeleePayload> CODEC =
			PacketCodec.of((value, buf) -> {
				buf.writeBoolean(value.pressed());
				buf.writeBoolean(value.movingForward());
				buf.writeBoolean(value.special());
				buf.writeDouble(value.effectX());
				buf.writeDouble(value.effectY());
				buf.writeDouble(value.effectZ());
			}, buf -> new GunMeleePayload(buf.readBoolean(), buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() >= Double.BYTES ? buf.readDouble() : 0.0D,
					buf.readableBytes() >= Double.BYTES ? buf.readDouble() : 0.0D,
					buf.readableBytes() >= Double.BYTES ? buf.readDouble() : 0.0D));

	public GunMeleePayload(boolean pressed, boolean movingForward) {
		this(pressed, movingForward, false, 0.0D, 0.0D, 0.0D);
	}

	public GunMeleePayload(boolean pressed, boolean movingForward,
			boolean special) {
		this(pressed, movingForward, special, 0.0D, 0.0D, 0.0D);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
