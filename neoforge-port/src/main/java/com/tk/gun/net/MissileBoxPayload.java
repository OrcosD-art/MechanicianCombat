package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Start/release input and current missile-bay mount positions from the client. */
public record MissileBoxPayload(
		boolean release,
		double cameraX, double cameraY, double cameraZ,
		double cameraYaw, double cameraPitch,
		double halfHorizontalTan, double halfVerticalTan,
		double[] ports) implements CustomPayload {
	public static final CustomPayload.Id<MissileBoxPayload> ID =
			new CustomPayload.Id<>(TkGun.id("missile_box_action"));

	public static final PacketCodec<RegistryByteBuf, MissileBoxPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeBoolean(value.release());
						writeVector(buf, value.cameraX(), value.cameraY(),
								value.cameraZ());
						buf.writeDouble(value.cameraYaw());
						buf.writeDouble(value.cameraPitch());
						buf.writeDouble(value.halfHorizontalTan());
						buf.writeDouble(value.halfVerticalTan());
						for (int index = 0; index < 18; index++) {
							buf.writeDouble(value.ports().length > index
									? value.ports()[index] : 0.0D);
						}
					},
					buf -> {
						boolean release = buf.readBoolean();
						double[] camera = readVector(buf);
						double yaw = buf.readDouble();
						double pitch = buf.readDouble();
						double horizontal = buf.readDouble();
						double vertical = buf.readDouble();
						double[] ports = new double[18];
						for (int index = 0; index < ports.length; index++) {
							ports[index] = buf.readDouble();
						}
						return new MissileBoxPayload(
								release,
								camera[0], camera[1], camera[2],
								yaw, pitch, horizontal, vertical, ports);
					});

	private static void writeVector(RegistryByteBuf buf,
									double x, double y, double z) {
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	private static double[] readVector(RegistryByteBuf buf) {
		return new double[]{buf.readDouble(), buf.readDouble(), buf.readDouble()};
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
