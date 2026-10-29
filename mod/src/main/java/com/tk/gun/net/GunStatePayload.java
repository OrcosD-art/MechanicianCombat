package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** 服务端向客户端同步的武器状态、弹药、热量与瞄准输入锁。 */
public record GunStatePayload(int playerId, byte state, int ticks, int heat, int shotIndex, int mag,
							  boolean reloading, boolean focus, int shotSeq, boolean dashing,
							  int dashTicks, int aimLockTicks, int heatLockTicks, String magAmmo,
							  int rollCooldownTicks, int hipMagazineCapacity,
							  int focusMagazineCapacity, int maxShotIndex,
							  boolean airborneIdle, boolean dashMelee,
							  boolean meleeReturning, boolean dashMeleeReturn,
							  boolean dashMeleeReturnRun, boolean meleeHoldOver)
		implements CustomPayload {
	public static final CustomPayload.Id<GunStatePayload> ID =
			new CustomPayload.Id<>(TkGun.id("state"));

	public static final PacketCodec<RegistryByteBuf, GunStatePayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.playerId());
				buf.writeByte(value.state());
				buf.writeVarInt(value.ticks());
				buf.writeVarInt(value.heat());
				buf.writeVarInt(value.shotIndex());
				buf.writeVarInt(value.mag());
				buf.writeBoolean(value.reloading());
				buf.writeBoolean(value.focus());
				buf.writeVarInt(value.shotSeq());
				// 2026-10-17：**翻滚后的加速冲刺窗口里**（`dashUntil` 未到）—— 客户端用它禁止右键瞄准。
				buf.writeBoolean(value.dashing());
				buf.writeVarInt(value.dashTicks());
				// 2026-10-18：翻滚 / 滑铲及其结束后的右键瞄准锁剩余 tick。
				buf.writeVarInt(value.aimLockTicks());
				buf.writeVarInt(value.heatLockTicks());
				buf.writeString(value.magAmmo());
				// Keep legacy fields first so older clients can still decode newer packets.
				buf.writeVarInt(value.rollCooldownTicks());
				// Appended in r397. Dynamic per-gun magazine/animation metadata.
				buf.writeVarInt(value.hipMagazineCapacity());
				buf.writeVarInt(value.focusMagazineCapacity());
				buf.writeVarInt(value.maxShotIndex());
				buf.writeBoolean(value.airborneIdle());
				buf.writeBoolean(value.dashMelee());
				buf.writeBoolean(value.meleeReturning());
				buf.writeBoolean(value.dashMeleeReturn());
				buf.writeBoolean(value.dashMeleeReturnRun());
				buf.writeBoolean(value.meleeHoldOver());
			},
			buf -> new GunStatePayload(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(),
					// ⚠️ 宽一点读：新客户端 + **旧服务端**（还没重启，包里没这一位）时，
					//    `readBoolean()` 会在包尾越界 → 客户端 "Failed to decode packet" 直接断线
					//    （用户 2026-10-14 报的"右键直接崩溃"就是这个）。没有这一位就当 false。
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					// 射击序号（2026-10-17 加）：客户端靠"序号变了"来决定**重播射击动画** ——
					// 比原来"看包到达时机（elapsed<=1）"稳得多，用户报的"点按过快动画丢失"就是那个时机漏掉的。
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readString() : "",
					buf.readableBytes() > 0 ? buf.readVarInt() : 0,
					buf.readableBytes() > 0 ? buf.readVarInt() : 6,
					buf.readableBytes() > 0 ? buf.readVarInt() : 10,
					buf.readableBytes() > 0 ? buf.readVarInt() : 10,
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean(),
					buf.readableBytes() > 0 && buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
