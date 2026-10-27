package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** 服务端 -> 客户端：打开/关闭自动演示（按原版键位跑一遍所有动作并截图）。 */
public record GunDemoPayload(boolean on) implements CustomPayload {
	public static final CustomPayload.Id<GunDemoPayload> ID =
			new CustomPayload.Id<>(TkGun.id("demo"));

	public static final PacketCodec<RegistryByteBuf, GunDemoPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeBoolean(value.on()),
			buf -> new GunDemoPayload(buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
