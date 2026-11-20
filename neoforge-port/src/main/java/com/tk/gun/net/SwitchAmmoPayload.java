package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** 客户端 -> 服务端：滚轮切换当前枪械弹仓中的选中槽。 */
public record SwitchAmmoPayload(int direction) implements CustomPayload {
	public static final CustomPayload.Id<SwitchAmmoPayload> ID =
			new CustomPayload.Id<>(TkGun.id("switch_ammo"));

	public static final PacketCodec<RegistryByteBuf, SwitchAmmoPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeVarInt(value.direction()),
			buf -> new SwitchAmmoPayload(buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
