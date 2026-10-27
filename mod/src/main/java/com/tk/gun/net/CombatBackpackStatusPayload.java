package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Authoritative backpack phase, charge count and timed-window state. */
public record CombatBackpackStatusPayload(int ownerEntityId, int targetEntityId,
										  int phase, int charges,
										  int remainingTicks)
		implements CustomPayload {
	public static final int PHASE_READY = 0;
	public static final int PHASE_MARKED = 1;
	public static final int PHASE_COOLDOWN = 2;
	public static final int PHASE_AUTO_ACTIVE = 3;
	public static final int PHASE_AUTO_PAUSED = 4;
	public static final int PHASE_SUMMON_DORMANT = 5;
	public static final int PHASE_SUMMON_READY = 6;
	public static final int PHASE_SUMMON_ARMED = 7;
	public static final int PHASE_SUMMON_MARKED = 8;
	public static final int PHASE_SUMMON_CAST = 9;

	public static final CustomPayload.Id<CombatBackpackStatusPayload> ID =
			new CustomPayload.Id<>(TkGun.id("backpack_status"));

	public static final PacketCodec<RegistryByteBuf, CombatBackpackStatusPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.ownerEntityId());
						buf.writeVarInt(value.targetEntityId());
						buf.writeByte(value.phase());
						buf.writeByte(value.charges());
						buf.writeVarInt(value.remainingTicks());
					},
					buf -> new CombatBackpackStatusPayload(
							buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(),
							buf.readUnsignedByte(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
