package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Client-visible ordered points for a resolved soul-chain strike. */
public record SoulChainPayload(List<ChainNode> nodes,
							  int durationTicks) implements CustomPayload {
	public record ChainNode(int entityId, double x, double y, double z) {
	}

	public static final CustomPayload.Id<SoulChainPayload> ID =
			new CustomPayload.Id<>(TkGun.id("soul_chain_effect"));

	public static final PacketCodec<RegistryByteBuf, SoulChainPayload> CODEC =
			PacketCodec.of(
					(value, buf) -> {
						buf.writeVarInt(value.nodes().size());
						for (ChainNode node : value.nodes()) {
							buf.writeVarInt(node.entityId());
							buf.writeDouble(node.x());
							buf.writeDouble(node.y());
							buf.writeDouble(node.z());
						}
						buf.writeVarInt(Math.max(1, value.durationTicks()));
					},
					buf -> {
						int count = buf.readVarInt();
						List<ChainNode> nodes = new ArrayList<>(count);
						for (int index = 0; index < count; index++) {
							nodes.add(new ChainNode(
									buf.readVarInt(),
									buf.readDouble(),
									buf.readDouble(),
									buf.readDouble()));
						}
						return new SoulChainPayload(
								List.copyOf(nodes), buf.readVarInt());
					});

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
