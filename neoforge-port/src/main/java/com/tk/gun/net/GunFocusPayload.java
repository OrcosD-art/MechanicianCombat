package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端在 Z 键按下沿发送专注切换请求。dodgeCombo 表示本次同时配合翻滚/滑铲，
 * 服务端会在对应动作开始后兑现快速专注。
 */
public record GunFocusPayload(boolean dodgeCombo) implements CustomPayload {
	public static final CustomPayload.Id<GunFocusPayload> ID =
			new CustomPayload.Id<>(TkGun.id("focus_toggle"));

	public static final PacketCodec<RegistryByteBuf, GunFocusPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeBoolean(value.dodgeCombo()),
			// 宽读：旧服务端/旧客户端没有这一位时按普通 Z 处理。
			buf -> new GunFocusPayload(buf.readableBytes() > 0 && buf.readBoolean()));

	public GunFocusPayload() {
		this(false);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
