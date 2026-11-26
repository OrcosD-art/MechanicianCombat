package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Client mirror for the player mounted at an active dressing station. */
public record ArmorDressingMountPayload(
		BlockPos stationPos,
		int playerId,
		boolean mounted) implements CustomPayload {
	public static final CustomPayload.Id<ArmorDressingMountPayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_dressing_mount"));
	public static final PacketCodec<RegistryByteBuf, ArmorDressingMountPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeBlockPos(value.stationPos());
						buffer.writeVarInt(value.playerId());
						buffer.writeBoolean(value.mounted());
					},
					buffer -> new ArmorDressingMountPayload(
							buffer.readBlockPos(), buffer.readVarInt(),
							buffer.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
