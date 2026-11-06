package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-visible points for a resolved A -> B -> C soul-chain strike. */
public record SoulChainPayload(
		int aEntityId,
		int bEntityId,
		int cEntityId,
		double ax, double ay, double az,
		double bx, double by, double bz,
		double cx, double cy, double cz,
		boolean hasC,
		int durationTicks) implements CustomPayload {
	public static final CustomPayload.Id<SoulChainPayload> ID =
			new CustomPayload.Id<>(TkGun.id("soul_chain_effect"));

	public static final PacketCodec<RegistryByteBuf, SoulChainPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.aEntityId());
						buf.writeVarInt(value.bEntityId());
						buf.writeVarInt(value.cEntityId());
						writePoint(buf, value.ax(), value.ay(), value.az());
						writePoint(buf, value.bx(), value.by(), value.bz());
						writePoint(buf, value.cx(), value.cy(), value.cz());
						buf.writeBoolean(value.hasC());
						buf.writeVarInt(Math.max(1, value.durationTicks()));
					},
					buf -> {
						int aEntityId = buf.readVarInt();
						int bEntityId = buf.readVarInt();
						int cEntityId = buf.readVarInt();
						double[] a = readPoint(buf);
						double[] b = readPoint(buf);
						double[] c = readPoint(buf);
						return new SoulChainPayload(
								aEntityId, bEntityId, cEntityId,
								a[0], a[1], a[2],
								b[0], b[1], b[2],
								c[0], c[1], c[2],
								buf.readBoolean(), buf.readVarInt());
					});

	private static void writePoint(RegistryByteBuf buf,
								   double x, double y, double z) {
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	private static double[] readPoint(RegistryByteBuf buf) {
		return new double[]{buf.readDouble(), buf.readDouble(), buf.readDouble()};
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
