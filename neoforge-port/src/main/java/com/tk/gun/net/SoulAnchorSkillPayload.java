package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-visible phase for the chestplate Soul Anchor skill. */
public record SoulAnchorSkillPayload(int playerEntityId, int phase,
		int durationTicks, boolean gunHeld) implements CustomPayload {
	public static final int THROW = 0;
	public static final int DRAG = 1;
	public static final int DRAG_ENTITY = 2;
	public static final int GUN_THROW = 3;
	public static final int GUN_DRAG_ENTITY = 4;
	public static final int READY = 5;
	public static final int RECALL = 6;
	public static final int GUN_RECALL = 7;
	public static final int ANCHOR_STALL = 8;
	public static final int ANCHOR_FLIGHT = 9;
	public static final int GUN_SLIDING = 10;
	public static final int GUN_SLIDING_TO_IDLE = 11;
	public static final int GUN_SLIDING_TO_RUN = 12;
	public static final int END = -1;

	public static final CustomPayload.Id<SoulAnchorSkillPayload> ID =
			new CustomPayload.Id<>(TkGun.id("soul_anchor_skill"));
	public static final PacketCodec<RegistryByteBuf, SoulAnchorSkillPayload>
			CODEC = PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.playerEntityId());
						buf.writeVarInt(value.phase());
						buf.writeVarInt(Math.max(0, value.durationTicks()));
						buf.writeBoolean(value.gunHeld());
					},
					buf -> new SoulAnchorSkillPayload(
							buf.readVarInt(), buf.readVarInt(),
							buf.readVarInt(), buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
