package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Requests a dismount from the active dressing station seat. */
public record ArmorDressingDismountPayload() implements CustomPayload {
	public static final CustomPayload.Id<ArmorDressingDismountPayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_dressing_dismount"));
	public static final PacketCodec<RegistryByteBuf, ArmorDressingDismountPayload> CODEC =
			PacketCodec.unit(new ArmorDressingDismountPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
