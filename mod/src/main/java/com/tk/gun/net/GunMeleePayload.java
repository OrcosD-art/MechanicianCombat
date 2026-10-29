package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Press/release edges for the server-authoritative Veritas close-combat combo. */
public record GunMeleePayload(boolean pressed, boolean movingForward)
		implements CustomPayload {
	public static final CustomPayload.Id<GunMeleePayload> ID =
			new CustomPayload.Id<>(TkGun.id("melee_attack"));

	public static final PacketCodec<RegistryByteBuf, GunMeleePayload> CODEC =
			PacketCodec.of((value, buf) -> {
				buf.writeBoolean(value.pressed());
				buf.writeBoolean(value.movingForward());
			}, buf -> new GunMeleePayload(buf.readBoolean(), buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
