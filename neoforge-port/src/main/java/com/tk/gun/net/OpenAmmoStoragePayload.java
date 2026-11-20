package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** 客户端 -> 服务端：打开机械师弹药环 UI。 */
public record OpenAmmoStoragePayload() implements CustomPayload {
	public static final CustomPayload.Id<OpenAmmoStoragePayload> ID =
			new CustomPayload.Id<>(TkGun.id("open_ammo_storage"));

	public static final PacketCodec<RegistryByteBuf, OpenAmmoStoragePayload> CODEC = PacketCodec.of(
			(value, buf) -> { },
			buf -> new OpenAmmoStoragePayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
