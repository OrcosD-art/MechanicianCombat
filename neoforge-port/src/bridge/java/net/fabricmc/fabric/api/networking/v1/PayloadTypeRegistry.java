package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PayloadTypeRegistry {
	public record Registration<T extends CustomPayload>(
			CustomPayload.Id<T> id,
			PacketCodec<? super RegistryByteBuf, T> codec) {
	}

	private static final Map<CustomPayload.Id<?>, Registration<?>> C2S =
			new LinkedHashMap<>();
	private static final Map<CustomPayload.Id<?>, Registration<?>> S2C =
			new LinkedHashMap<>();

	private PayloadTypeRegistry() {
	}

	public static Registry<CustomPayload> playC2S() {
		return new Registry<>(C2S);
	}

	public static Registry<CustomPayload> playS2C() {
		return new Registry<>(S2C);
	}

	public static Map<CustomPayload.Id<?>, Registration<?>> c2s() {
		return C2S;
	}

	public static Map<CustomPayload.Id<?>, Registration<?>> s2c() {
		return S2C;
	}

	public static final class Registry<T extends CustomPayload> {
		private final Map<CustomPayload.Id<?>, Registration<?>> registrations;

		private Registry(Map<CustomPayload.Id<?>, Registration<?>> registrations) {
			this.registrations = registrations;
		}

		public <P extends T> void register(CustomPayload.Id<P> id,
				PacketCodec<? super RegistryByteBuf, P> codec) {
			registrations.put(id, new Registration<>(id, codec));
		}
	}
}
