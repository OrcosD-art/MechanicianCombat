package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Page, save, or delete request from the visual recipe editor. */
public record RecipeEditorActionPayload(int action, int page, boolean shaped,
										String recipeId, float experience,
										int cookingTime) implements CustomPayload {
	public static final int ACTION_SET_PAGE = 0;
	public static final int ACTION_SAVE = 1;
	public static final int ACTION_DELETE = 2;
	private static final int MAX_RECIPE_ID_LENGTH = 128;
	public static final Id<RecipeEditorActionPayload> ID =
			new Id<>(TkGun.id("recipe_editor_action"));
	public static final PacketCodec<RegistryByteBuf, RecipeEditorActionPayload> CODEC =
			PacketCodec.of(RecipeEditorActionPayload::write,
					RecipeEditorActionPayload::read);

	private static void write(RecipeEditorActionPayload payload,
			RegistryByteBuf buffer) {
		buffer.writeVarInt(payload.action());
		buffer.writeVarInt(payload.page());
		buffer.writeBoolean(payload.shaped());
		buffer.writeString(payload.recipeId(), MAX_RECIPE_ID_LENGTH);
		buffer.writeFloat(payload.experience());
		buffer.writeVarInt(payload.cookingTime());
	}

	private static RecipeEditorActionPayload read(RegistryByteBuf buffer) {
		return new RecipeEditorActionPayload(buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readBoolean(),
				buffer.readString(MAX_RECIPE_ID_LENGTH),
				buffer.readFloat(),
				buffer.readVarInt());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
