package com.tk.gun.client.screen;

import com.tk.gun.screen.AmmoStorageScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** 上方独立弹药环 + 下方玩家背包。 */
public final class AmmoStorageScreen extends HandledScreen<AmmoStorageScreenHandler> {
	private static final Identifier TEXTURE =
			Identifier.of("tkgun", "textures/gui/ammo_storage.png");
	private final List<int[]> ringCenters = new ArrayList<>();

	public AmmoStorageScreen(AmmoStorageScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 212;
		for (int index = 0; index < AmmoStorageScreenHandler.SIZE; index++) {
			ringCenters.add(new int[]{
					AmmoStorageScreenHandler.slotX(index) + 9,
					AmmoStorageScreenHandler.slotY(index) + 9});
		}
		double cx = ringCenters.stream().mapToInt(v -> v[0]).average().orElse(88.0D);
		double cy = ringCenters.stream().mapToInt(v -> v[1]).average().orElse(56.0D);
		ringCenters.sort(Comparator.comparingDouble(v -> Math.atan2(v[1] - cy, v[0] - cx)));
	}

	@Override
	protected void init() {
		super.init();
		// 整个面板（背景、槽位、点击区域共用 x/y）向左、向上各偏移 1 像素。
		x -= 1;
		y -= 1;
	}

	/**
	 * `HandledScreens.register` 在 1.21.1 是 private；这里用动态代理实现它的 private
	 * {@code Provider} 接口，避免再给原版类加一个只用于注册的 mixin。
	 */
	public static void registerClientScreen() {
		try {
			// 生产环境会重映射类名/方法名，不能硬编码 register/Provider。
			// 通过结构寻找：一个只含 create(handler, inventory, title) 的私有接口 + 接收它的静态方法。
			Class<?> providerType = Arrays.stream(
							net.minecraft.client.gui.screen.ingame.HandledScreens.class.getDeclaredClasses())
					.filter(Class::isInterface)
					.filter(type -> Arrays.stream(type.getDeclaredMethods())
							.anyMatch(method -> "create".equals(method.getName())
									|| method.getParameterCount() == 3))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("HandledScreens.Provider not found"));
			Object provider = Proxy.newProxyInstance(
					AmmoStorageScreen.class.getClassLoader(),
					new Class<?>[]{providerType},
					(proxy, method, args) -> {
						if ("create".equals(method.getName())) {
							return new AmmoStorageScreen(
									(AmmoStorageScreenHandler) args[0],
									(PlayerInventory) args[1],
									(Text) args[2]);
						}
						return null;
					});
			Method register = Arrays.stream(
							net.minecraft.client.gui.screen.ingame.HandledScreens.class.getDeclaredMethods())
					.filter(method -> method.getParameterCount() == 2)
					.filter(method -> method.getParameterTypes()[0]
							.isAssignableFrom(net.minecraft.screen.ScreenHandlerType.class))
					.filter(method -> method.getParameterTypes()[1] == providerType)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("HandledScreens.register not found"));
			register.setAccessible(true);
			register.invoke(null, AmmoStorageScreenHandler.TYPE, provider);
		} catch (Throwable t) {
			com.tk.gun.TkGun.LOGGER.error("[tkgun] 无法注册机械师弹药环屏幕", t);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		// HandledScreen itself does not draw slot tooltips; inventory-like screens do it here.
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.drawTexture(TEXTURE, x, y, 0, 0, 176, 212, 176, 212);
		int selected = handler.getSelectedSlot();
		int sx = x + AmmoStorageScreenHandler.slotX(selected);
		int sy = y + AmmoStorageScreenHandler.slotY(selected);
		context.fill(sx, sy, sx + 16, sy + 16, 0x66FFD24A);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// 环形窗口保持紧凑，不额外绘制标题/背包标题。
	}

}
