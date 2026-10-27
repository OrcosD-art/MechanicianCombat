package com.tk.gun.client;

import com.tk.gun.TkGun;
import com.tk.gun.item.GunAmmoData;
import com.tk.gun.net.SwitchAmmoPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Client-side staging for ammo switching.
 *
 * <p>The server slot change is intentionally delayed until the selection animation
 * reaches its last frame. This keeps the animation, the authoritative inventory
 * change, and the action-bar announcement in the same visual beat.</p>
 */
public final class AmmoSwitchHud {
	private static final Identifier POPUP =
			Identifier.of("tkgun", "textures/gui/ammo_switch_popup.png");
	private static final Identifier CYCLE_CW =
			Identifier.of("tkgun", "textures/gui/ammo_switch_cw.png");
	private static final Identifier CYCLE_CCW =
			Identifier.of("tkgun", "textures/gui/ammo_switch_ccw.png");
	private static final Identifier CHAMBER_COLORS =
			Identifier.of("tkgun", "textures/gui/ammo_chamber_colors.png");
	private static final Identifier RETRACT =
			Identifier.of("tkgun", "textures/gui/ammo_switch_retract.png");

	private static final int FRAME_SIZE = 80;
	private static final int POPUP_FRAMES = 5;
	private static final int CYCLE_FRAMES = 4;
	private static final int RETRACT_FRAMES = 5;
	private static final int TICKS_PER_FRAME = 1;
	private static final int HOLD_TICKS = 20;
	private static final long SWITCH_FRAME_NANOS = 75_000_000L;
	private static final long SWITCH_DURATION_NANOS = CYCLE_FRAMES * SWITCH_FRAME_NANOS;
	private static final long WHEEL_DEDUPE_NANOS = 80_000_000L;
	private static final int CHAMBER_CELL = 12;
	private static final int CHAMBER_HALF = 6;
	private static final int CHAMBER_PALETTE_COUNT = 7;
	private static final int CHAMBER_ATLAS_WIDTH = CHAMBER_PALETTE_COUNT * CHAMBER_CELL;
	private static final int CHAMBER_ATLAS_HEIGHT = 2 * CYCLE_FRAMES * 6 * CHAMBER_CELL;
	private static final int[] CHAMBER_X = {37, 43, 47, 47, 33, 33};
	private static final int[] CHAMBER_Y = {34, 34, 38, 43, 43, 38};
	/** The two bottom slots (4,5) are hidden by the hotbar selection frame. */
	private static final int[] CHAMBER_VISIBLE_SLOT = {0, 1, 2, 3, 6, 7};
	private static final int[] CHAMBER_PALETTES = new int[GunAmmoData.SIZE];

	static {
		Arrays.fill(CHAMBER_PALETTES, 5);
	}

	/** The GIF uses a 24x24 black square as a placeholder for the vanilla hotbar frame. */
	private static final int BLACK_X = 28;
	private static final int BLACK_Y = 44;

	private enum Phase {
		HIDDEN,
		POPUP,
		SWITCH,
		HOLD,
		RETRACT
	}

	private static Phase phase = Phase.HIDDEN;
	private static int elapsed;
	private static int pendingDirection;
	private static int switchDirection = 1;
	private static int displaySelected = -1;
	private static ItemStack trackedGun = ItemStack.EMPTY;
	private static long switchStartNanos;
	private static boolean switchClickPlayed;
	private static final Deque<Integer> queuedDirections = new ArrayDeque<>();
	private static long lastWheelNanos;

	private AmmoSwitchHud() {
	}

	/** Coalesces duplicate wheel callbacks produced by one physical notch. */
	public static void wheel(int direction) {
		long now = System.nanoTime();
		if (now - lastWheelNanos < WHEEL_DEDUPE_NANOS) {
			return;
		}
		lastWheelNanos = now;
		request(direction);
	}

	/** Called by the scroll-wheel mixin instead of sending the packet immediately. */
	public static void request(int direction) {
		direction = Integer.signum(direction);
		if (direction == 0) {
			return;
		}
		switch (phase) {
			case HIDDEN -> begin(Phase.POPUP, direction);
			case POPUP, SWITCH, RETRACT -> {
				// While an animation is playing, accept exactly one pre-input.
				if (queuedDirections.isEmpty()) {
					queuedDirections.addLast(direction);
				}
			}
			case HOLD -> begin(Phase.SWITCH, direction);
		}
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || client.currentScreen != null
				|| !TkGun.holdingGun(client.player)) {
			reset();
			return;
		}
		ItemStack gun = GunAmmoData.heldGun(client.player);
		if (gun != trackedGun) {
			trackedGun = gun;
			displaySelected = GunAmmoData.selected(gun);
		}
		refreshChambers(client);
		if (phase == Phase.HIDDEN) {
			return;
		}

		elapsed++;
		switch (phase) {
			case POPUP -> {
				if (elapsed >= POPUP_FRAMES * TICKS_PER_FRAME) {
					begin(Phase.SWITCH, pendingDirection);
				}
			}
			case SWITCH -> {
				if (System.nanoTime() - switchStartNanos >= SWITCH_DURATION_NANOS) {
					commitSwitch(client);
				}
			}
			case HOLD -> {
				if (elapsed >= HOLD_TICKS) {
					begin(Phase.RETRACT, 0);
				}
			}
			case RETRACT -> {
				if (elapsed >= RETRACT_FRAMES * TICKS_PER_FRAME) {
					if (queuedDirections.isEmpty()) {
						begin(Phase.HIDDEN, 0);
					} else {
						begin(Phase.POPUP, queuedDirections.removeFirst());
					}
				}
			}
			default -> {
			}
		}
	}

	public static int displayedSelectedSlot() {
		return displaySelected;
	}

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (phase == Phase.HIDDEN || client.player == null || client.world == null
				|| client.options.hudHidden || client.currentScreen != null
				|| !TkGun.holdingGun(client.player)) {
			return;
		}

		int frame;
		Identifier texture;
		int frames;
		if (phase == Phase.POPUP) {
			frame = Math.min(POPUP_FRAMES - 1, elapsed / TICKS_PER_FRAME);
			texture = POPUP;
			frames = POPUP_FRAMES;
		} else if (phase == Phase.RETRACT) {
			frame = Math.min(RETRACT_FRAMES - 1, elapsed / TICKS_PER_FRAME);
			texture = RETRACT;
			frames = RETRACT_FRAMES;
		} else if (phase == Phase.HOLD) {
			frame = CYCLE_FRAMES - 1;
			texture = switchDirection < 0 ? CYCLE_CCW : CYCLE_CW;
			frames = CYCLE_FRAMES;
		} else {
			frame = switchFrame();
			texture = switchDirection < 0 ? CYCLE_CCW : CYCLE_CW;
			frames = CYCLE_FRAMES;
			if (frame >= 1 && !switchClickPlayed) {
				switchClickPlayed = true;
				client.getSoundManager().play(
						PositionedSoundInstance.master(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0F));
			}
		}

		int selected = client.player.getInventory().selectedSlot;
		int hotbarFrameX = context.getScaledWindowWidth() / 2 - 92 + selected * 20;
		int hotbarFrameY = context.getScaledWindowHeight() - 23;
		int drawX = hotbarFrameX - BLACK_X;
		int drawY = hotbarFrameY - BLACK_Y;
		context.drawTexture(texture, drawX, drawY, frame * FRAME_SIZE, 0,
				FRAME_SIZE, FRAME_SIZE, frames * FRAME_SIZE, FRAME_SIZE);
		if (phase == Phase.SWITCH || phase == Phase.HOLD) {
			renderChambers(context, drawX, drawY);
		}
	}

	private static void renderChambers(DrawContext context, int drawX, int drawY) {
		int frame = phase == Phase.HOLD
				? CYCLE_FRAMES - 1
				: switchFrame();
		int direction = switchDirection < 0 ? 1 : 0;
		for (int visual = 0; visual < CHAMBER_VISIBLE_SLOT.length; visual++) {
			int row = (direction * CYCLE_FRAMES + frame) * 6 + visual;
			int palette = CHAMBER_PALETTES[CHAMBER_VISIBLE_SLOT[visual]];
			int x = drawX + CHAMBER_X[visual] - CHAMBER_HALF;
			int y = drawY + CHAMBER_Y[visual] - CHAMBER_HALF;
			context.drawTexture(CHAMBER_COLORS, x, y,
					palette * CHAMBER_CELL, row * CHAMBER_CELL,
					CHAMBER_CELL, CHAMBER_CELL,
					CHAMBER_ATLAS_WIDTH, CHAMBER_ATLAS_HEIGHT);
		}
	}

	private static void refreshChambers(MinecraftClient client) {
		ItemStack gun = GunAmmoData.heldGun(client.player);
		if (gun.isEmpty()) {
			Arrays.fill(CHAMBER_PALETTES, 5);
			return;
		}
		var items = GunAmmoData.read(gun, client.world.getRegistryManager());
		if (displaySelected < 0) {
			displaySelected = GunAmmoData.selected(gun);
		}
		int selected = displaySelected;
		for (int slot = 0; slot < CHAMBER_PALETTES.length; slot++) {
			CHAMBER_PALETTES[slot] =
					paletteFor(items.get(Math.floorMod(selected + slot, GunAmmoData.SIZE)));
		}
	}

	private static int paletteFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return 5;
		}
		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		return switch (path) {
			case "rose_quartz_bolt" -> 0;
			case "explosive_bolt" -> 1;
			case "armor_piercing_bolt" -> 2;
			case "cutting_bolt" -> 3;
			case "sculk_bolt" -> 4;
			case "bolt" -> 6;
			default -> 5;
		};
	}

	private static void commitSwitch(MinecraftClient client) {
		displaySelected = Math.floorMod(displaySelected + pendingDirection, GunAmmoData.SIZE);
		if (client.getNetworkHandler() != null) {
			ClientPlayNetworking.send(new SwitchAmmoPayload(pendingDirection));
		}
		pendingDirection = 0;
		if (queuedDirections.isEmpty()) {
			begin(Phase.HOLD, 0);
		} else {
			begin(Phase.SWITCH, queuedDirections.removeFirst());
		}
	}

	private static void begin(Phase next, int direction) {
		phase = next;
		elapsed = 0;
		if (next == Phase.POPUP || next == Phase.SWITCH) {
			pendingDirection = Integer.signum(direction);
			if (next == Phase.SWITCH) {
				switchDirection = pendingDirection;
				switchStartNanos = System.nanoTime();
				switchClickPlayed = false;
			}
		}
	}

	private static int switchFrame() {
		long elapsedNanos = Math.max(0L, System.nanoTime() - switchStartNanos);
		return Math.min(CYCLE_FRAMES - 1, (int) (elapsedNanos / SWITCH_FRAME_NANOS));
	}

	private static void reset() {
		phase = Phase.HIDDEN;
		elapsed = 0;
		pendingDirection = 0;
		displaySelected = -1;
		trackedGun = ItemStack.EMPTY;
		queuedDirections.clear();
	}
}
