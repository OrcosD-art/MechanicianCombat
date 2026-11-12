package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Save or delete request from the visual recipe editor. */
public record RecipeEditorActionPayload(boolean shaped, String recipeId,
										boolean delete) implements CustomPayload {
	private static final int MAX_RECIPE_ID_LENGTH = 128;
	public static final Id<RecipeEditorActionPayload> ID =
			new Id<>(TkGun.id("recipe_editor_action"));
	public static final PacketCodec<RegistryByteBuf, RecipeEditorActionPayload> CODEC =
			PacketCodec.of(RecipeEditorActionPayload::write,
					RecipeEditorActionPayload::read);

	private static void write(RecipeEditorActionPayload payload,
			RegistryByteBuf buffer) {
		buffer.writeBoolean(payload.shaped());
		buffer.writeString(payload.recipeId(), MAX_RECIPE_ID_LENGTH);
		buffer.writeBoolean(payload.delete());
	}

	private static RecipeEditorActionPayload read(RegistryByteBuf buffer) {
		return new RecipeEditorActionPayload(buffer.readBoolean(),
				buffer.readString(MAX_RECIPE_ID_LENGTH), buffer.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
