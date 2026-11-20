package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public interface ArmorRenderer {
	List<ArmorRenderer> RENDERERS = new CopyOnWriteArrayList<>();
	Map<net.minecraft.item.Item, ArmorRenderer> BY_ITEM =
			new IdentityHashMap<>();

	void render(MatrixStack matrices, VertexConsumerProvider consumers,
			ItemStack stack, LivingEntity entity, EquipmentSlot slot,
			int light, BipedEntityModel<LivingEntity> baseModel);

	static void register(ArmorRenderer renderer, net.minecraft.item.Item... items) {
		RENDERERS.add(renderer);
		for (net.minecraft.item.Item item : items) {
			BY_ITEM.put(item, renderer);
		}
	}
}
