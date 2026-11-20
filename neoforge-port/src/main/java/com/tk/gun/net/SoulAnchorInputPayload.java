package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client hold/cancel state for the unified Soul Anchor controls. */
public record SoulAnchorInputPayload(boolean held, boolean cancel,
									 boolean sprinting)
		implements CustomPayload {
	public static final CustomPayload.Id<SoulAnchorInputPayload> ID =
			new CustomPayload.Id<>(TkGun.id("soul_anchor_input"));
	public static final PacketCodec<RegistryByteBuf, SoulAnchorInputPayload>
			CODEC = PacketCodec.of(
					(value, buf) -> {
						buf.writeBoolean(value.held());
						buf.writeBoolean(value.cancel());
						buf.writeBoolean(value.sprinting());
					},
					buf -> new SoulAnchorInputPayload(
							buf.readBoolean(), buf.readBoolean(),
							buf.readableBytes() > 0 && buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
