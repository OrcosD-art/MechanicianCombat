package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：**手动装填** —— 绑在 **F 键**上。
 *
 * <p>用户 2026-10-17："将装弹单独绑定为 F 按键" + "在任何状态下空弹匣不会自动装填"。
 * 所以装填从"自动/点击触发"改成**只认这个键**：打空了不会自己插弹夹，得按 F。
 */
public record GunReloadPayload() implements CustomPayload {
	public static final CustomPayload.Id<GunReloadPayload> ID =
			new CustomPayload.Id<>(TkGun.id("reload_key"));

	public static final PacketCodec<RegistryByteBuf, GunReloadPayload> CODEC = PacketCodec.of(
			(value, buf) -> { },
			buf -> new GunReloadPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
