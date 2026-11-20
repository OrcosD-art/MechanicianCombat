package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.CustomPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayNetworking {
	@FunctionalInterface
	public interface PlayPayloadHandler<T extends CustomPayload> {
		void receive(T payload, Context context);
	}

	public record Context(MinecraftClient client) {
	}

	private static final Map<CustomPayload.Id<?>, PlayPayloadHandler<?>> RECEIVERS =
			new ConcurrentHashMap<>();

	private ClientPlayNetworking() {
	}

	public static <T extends CustomPayload> void registerGlobalReceiver(
			CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
		RECEIVERS.put(id, handler);
	}

	public static void send(CustomPayload payload) {
		PacketDistributor.sendToServer(payload);
	}

	public static boolean canSend(CustomPayload.Id<?> id) {
		return true;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void dispatch(CustomPayload payload) {
		PlayPayloadHandler handler = RECEIVERS.get(payload.getId());
		if (handler != null) {
			handler.receive(payload, new Context(MinecraftClient.getInstance()));
		}
	}
}
