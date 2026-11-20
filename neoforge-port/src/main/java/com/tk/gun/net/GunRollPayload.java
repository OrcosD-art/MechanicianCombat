package com.tk.gun.net;

import com.tk.gun.TkGun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * 客户端 -> 服务端：**翻滚键按下了**（用户 2026-10-17："尝试将翻滚改为检测到输入翻滚按键时
 * 首先清空缓慢状态后并瞬间触发翻滚"）。
 *
 * <p>为什么要自己报一次：翻滚本身的位移/动画归 combat_roll，但它的事件只在"它自己真的滚起来"时
 * 才发；过热期间的"不可移动"阻尼 + 缓慢 10 会让它滚不起来 → 我们永远收不到事件、也就无从打断。
 * 这个包在**按键那一刻**就告诉服务端，于是可以：① 先清缓慢；② 过热 / `Focus_over` 里直接打断动画；
 * ③ 如果 combat_roll 这次没把玩家带走（速度还是很小），我们自己给一次冲刺 + 播我们的 `roll`。
 */
public record GunRollPayload(float directionX, float directionZ) implements CustomPayload {
	public static final CustomPayload.Id<GunRollPayload> ID =
			new CustomPayload.Id<>(TkGun.id("roll_key"));

	public static final PacketCodec<RegistryByteBuf, GunRollPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeFloat(value.directionX());
				buf.writeFloat(value.directionZ());
			},
			buf -> new GunRollPayload(buf.readFloat(), buf.readFloat()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
