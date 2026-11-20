package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerPlayNetworking {
	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPayload> {
		void receive(T payload, Context context);
	}

	public record Context(MinecraftServer server, ServerPlayerEntity player) {
	}

	private static final Map<CustomPayload.Id<?>, PlayPayloadHandler<?>> RECEIVERS =
			new ConcurrentHashMap<>();

	private ServerPlayNetworking() {
	}

	public static <T extends CustomPayload> void registerGlobalReceiver(
			CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
		RECEIVERS.put(id, handler);
	}

	public static void send(ServerPlayerEntity player, CustomPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void dispatch(CustomPayload payload,
			net.neoforged.neoforge.network.handling.IPayloadContext context) {
		PlayPayloadHandler handler = RECEIVERS.get(payload.getId());
		if (handler != null && context.player() instanceof ServerPlayerEntity player) {
			handler.receive(payload, new Context(player.getServer(), player));
		}
	}
}
