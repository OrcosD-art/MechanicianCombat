package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Extended display-side interaction for the oversized dressing station shape. */
public record ArmorDressingInteractPayload(BlockPos stationPos, int page)
		implements CustomPayload {
	public static final CustomPayload.Id<ArmorDressingInteractPayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_dressing_interact"));
	public static final PacketCodec<RegistryByteBuf, ArmorDressingInteractPayload> CODEC =
			PacketCodec.of(
					(value, buffer) -> {
						buffer.writeBlockPos(value.stationPos());
						buffer.writeVarInt(Math.max(0, value.page()));
					},
					buffer -> new ArmorDressingInteractPayload(
							buffer.readBlockPos(), buffer.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
