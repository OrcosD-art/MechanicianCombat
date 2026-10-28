package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Latest client camera pose used by the automatic backpack launcher. */
public record CombatBackpackViewPayload(
		double cameraX, double cameraY, double cameraZ,
		double cameraYaw, double cameraPitch)
		implements CustomPayload {
	public static final CustomPayload.Id<CombatBackpackViewPayload> ID =
			new CustomPayload.Id<>(TkGun.id("backpack_view"));

	public static final PacketCodec<RegistryByteBuf, CombatBackpackViewPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeDouble(value.cameraX());
						buf.writeDouble(value.cameraY());
						buf.writeDouble(value.cameraZ());
						buf.writeDouble(value.cameraYaw());
						buf.writeDouble(value.cameraPitch());
					},
					buf -> new CombatBackpackViewPayload(
							buf.readDouble(), buf.readDouble(), buf.readDouble(),
							buf.readDouble(), buf.readDouble()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
