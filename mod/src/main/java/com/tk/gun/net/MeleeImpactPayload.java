package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server-authoritative close-combat impact feedback.
 *
 * <p>{@code lockAttacker} is false for the dash attack: its authored hard-stop
 * already owns the movement timeline, while the target still freezes and the
 * camera still shakes.</p>
 */
public record MeleeImpactPayload(int attackerId, int[] targetIds, int ticks,
								 boolean critical, boolean lockAttacker,
								 boolean cameraShake)
		implements CustomPayload {
	public static final Id<MeleeImpactPayload> ID =
			new Id<>(TkGun.id("melee_impact"));

	public static final PacketCodec<RegistryByteBuf, MeleeImpactPayload> CODEC =
			PacketCodec.of(MeleeImpactPayload::write, MeleeImpactPayload::read);

	private static void write(MeleeImpactPayload payload, RegistryByteBuf buffer) {
		buffer.writeVarInt(payload.attackerId());
		buffer.writeVarInt(payload.targetIds().length);
		for (int targetId : payload.targetIds()) {
			buffer.writeVarInt(targetId);
		}
		buffer.writeVarInt(payload.ticks());
		buffer.writeBoolean(payload.critical());
		buffer.writeBoolean(payload.lockAttacker());
		buffer.writeBoolean(payload.cameraShake());
	}

	private static MeleeImpactPayload read(RegistryByteBuf buffer) {
		int attackerId = buffer.readVarInt();
		int count = buffer.readVarInt();
		int[] targetIds = new int[count];
		for (int index = 0; index < count; index++) {
			targetIds[index] = buffer.readVarInt();
		}
		return new MeleeImpactPayload(attackerId, targetIds, buffer.readVarInt(),
				buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
