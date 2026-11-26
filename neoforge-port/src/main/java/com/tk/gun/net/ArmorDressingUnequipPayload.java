package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/** Requests a station-authorised removal of the complete Mechanician suit. */
public record ArmorDressingUnequipPayload(BlockPos stationPos)
		implements CustomPayload {
	public static final CustomPayload.Id<ArmorDressingUnequipPayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_dressing_unequip"));
	public static final PacketCodec<RegistryByteBuf, ArmorDressingUnequipPayload>
			CODEC = PacketCodec.of(
					(value, buffer) -> buffer.writeBlockPos(
							value.stationPos()),
					buffer -> new ArmorDressingUnequipPayload(
							buffer.readBlockPos()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
