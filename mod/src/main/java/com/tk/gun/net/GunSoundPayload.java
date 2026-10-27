package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：**动画音效关键帧到点了**（只报名字：`bolt` / `reload` / `reload2` / `vent` /
 * `mag` / `empty` / `roll` / `steam`）。
 *
 * <p>用户 2026-10-17："将音频文件加入 mod 中使旁观者也能听见" —— 这件事两边各出一半：
 * **时间**只有客户端知道（GeckoLib 按动画时间回调关键帧，倍速也会自动跟着缩放，Java 里一个偏移都不用写），
 * 而**让附近所有人都听见**只有服务端做得到（`ServerWorld#playSound` 会广播给范围内所有玩家，
 * 连发的人自己也会收到）。所以客户端只报"到点了"，服务端决定"放给谁"。
 *
 * <p>服务端不信任包里的字符串：只认 {@link TkGun#KEYFRAME_SOUNDS} 里那几个名字，
 * 而且手里必须拿着破垒重炮。
 */
public record GunSoundPayload(String sound) implements CustomPayload {
	public static final CustomPayload.Id<GunSoundPayload> ID =
			new CustomPayload.Id<>(TkGun.id("sound_key"));

	public static final PacketCodec<RegistryByteBuf, GunSoundPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeString(value.sound(), 16),
			buf -> new GunSoundPayload(buf.readString(16)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
