package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Authoritative start/end notification for a workstation dressing session. */
public record ArmorDressingStatePayload(
		BlockPos stationPos,
		int playerId,
		boolean active) implements CustomPayload {
	public static final CustomPayload.Id<ArmorDressingStatePayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_dressing_state"));

	public static final PacketCodec<RegistryByteBuf, ArmorDressingStatePayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeBlockPos(value.stationPos());
						buffer.writeVarInt(value.playerId());
						buffer.writeBoolean(value.active());
					},
					buffer -> new ArmorDressingStatePayload(
							buffer.readBlockPos(),
							buffer.readVarInt(),
							buffer.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
