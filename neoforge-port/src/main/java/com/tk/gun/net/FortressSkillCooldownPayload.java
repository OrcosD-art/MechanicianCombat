package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Preserves the current client cooldown progress while shortening its duration. */
public record FortressSkillCooldownPayload(int remainingTicks)
		implements CustomPayload {
	public static final CustomPayload.Id<FortressSkillCooldownPayload> ID =
			new CustomPayload.Id<>(TkGun.id("fortress_skill_cooldown"));

	public static final PacketCodec<RegistryByteBuf, FortressSkillCooldownPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> buf.writeVarInt(Math.max(0, value.remainingTicks())),
					buf -> new FortressSkillCooldownPayload(buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
