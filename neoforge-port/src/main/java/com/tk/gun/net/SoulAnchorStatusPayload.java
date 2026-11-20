package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Ready-target particle and anchor-hit glow synchronization. */
public record SoulAnchorStatusPayload(
		int ownerEntityId,
		int targetEntityId,
		int action,
		int durationTicks,
		int color) implements CustomPayload {
	public static final int READY_TARGET = 0;
	public static final int CLEAR_READY_TARGET = 1;
	public static final int GLOW_TARGET = 2;
	public static final int CLEAR_GLOW_TARGET = 3;

	public static final CustomPayload.Id<SoulAnchorStatusPayload> ID =
			new CustomPayload.Id<>(TkGun.id("soul_anchor_status"));
	public static final PacketCodec<RegistryByteBuf, SoulAnchorStatusPayload>
			CODEC = PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.ownerEntityId());
						buf.writeVarInt(value.targetEntityId());
						buf.writeVarInt(value.action());
						buf.writeVarInt(Math.max(0, value.durationTicks()));
						buf.writeInt(value.color());
					},
					buf -> new SoulAnchorStatusPayload(
							buf.readVarInt(), buf.readVarInt(),
							buf.readVarInt(), buf.readVarInt(),
							buf.readInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
