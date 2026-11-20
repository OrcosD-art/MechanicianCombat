package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-side spawn point plus the current camera frustum used for target marking. */
public record CombatBackpackFirePayload(double x, double y, double z,
										double cameraX, double cameraY, double cameraZ,
										double cameraYaw, double cameraPitch,
										double halfHorizontalTan, double halfVerticalTan)
		implements CustomPayload {
	public static final CustomPayload.Id<CombatBackpackFirePayload> ID =
			new CustomPayload.Id<>(TkGun.id("backpack_firework"));

	public static final PacketCodec<RegistryByteBuf, CombatBackpackFirePayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeDouble(value.x());
						buf.writeDouble(value.y());
						buf.writeDouble(value.z());
						buf.writeDouble(value.cameraX());
						buf.writeDouble(value.cameraY());
						buf.writeDouble(value.cameraZ());
						buf.writeDouble(value.cameraYaw());
						buf.writeDouble(value.cameraPitch());
						buf.writeDouble(value.halfHorizontalTan());
						buf.writeDouble(value.halfVerticalTan());
					},
					buf -> new CombatBackpackFirePayload(
							buf.readDouble(), buf.readDouble(), buf.readDouble(),
							buf.readDouble(), buf.readDouble(), buf.readDouble(),
							buf.readDouble(), buf.readDouble(),
							buf.readDouble(), buf.readDouble()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
