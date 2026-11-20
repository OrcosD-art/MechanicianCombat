package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-authoritative armor module charges and cooldowns for the right HUD. */
public record ArmorAbilityPayload(
		boolean shieldInstalled,
		boolean totemInstalled,
		boolean soulChainInstalled,
		int shieldCharges,
		int shieldRechargeKills,
		int shieldRechargeWindowTicks,
		int shieldCooldownTicks,
		int totemCooldownTicks) implements CustomPayload {
	public static final CustomPayload.Id<ArmorAbilityPayload> ID =
			new CustomPayload.Id<>(TkGun.id("armor_ability"));

	public static final PacketCodec<RegistryByteBuf, ArmorAbilityPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeBoolean(value.shieldInstalled());
						buf.writeBoolean(value.totemInstalled());
						buf.writeBoolean(value.soulChainInstalled());
						buf.writeVarInt(Math.max(0, value.shieldCharges()));
						buf.writeVarInt(Math.max(0, value.shieldRechargeKills()));
						buf.writeVarInt(Math.max(0,
								value.shieldRechargeWindowTicks()));
						buf.writeVarInt(Math.max(0, value.shieldCooldownTicks()));
						buf.writeVarInt(Math.max(0, value.totemCooldownTicks()));
					},
					buf -> new ArmorAbilityPayload(
							buf.readBoolean(),
							buf.readBoolean(),
							buf.readBoolean(),
							buf.readVarInt(),
							buf.readVarInt(),
							buf.readVarInt(),
							buf.readVarInt(),
							buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
