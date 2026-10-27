package com.tk.gun.client.rig;

import com.tk.gun.TkGun;
import com.tk.gun.client.GunCamera;
import com.tk.gun.client.GunClientState;
import com.tk.gun.client.TkGunClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家 rig 的 GeckoLib 渲染器。
 *
 * <p>主渲染（玩家皮肤）只画身体：**在 {@link #renderCubesOfBone} 里把非身体骨骼的方块跳过**。
 *
 * <p>⚠️ 别改用 {@code bone.setHidden(true)} 来藏枪：GeckoLib 里 hidden 会**连子骨骼一起
 * 不遍历**（renderChildBones 见到 isHidingChildren 直接 return），而枪件是
 * gun → barrel → fire1..6 / case / change1..5 这种多级嵌套 —— 藏了父级，子孙根本轮不到逐骨骼回调，
 * 结果就是"枪只剩几个零件 / 看着像贴图乱掉"（2026-10-12 用户实测）。
 */
public class BowgunRigRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayerEntity, BowgunRig> {
	/** 身体那 6 根（作者模型里的骨名），其余骨骼的方块交给 {@link BowgunGunLayer} 用枪贴图画。 */
	public static final Set<String> BODY_BONES = Set.of(
			"chest", "head", "arm_left", "arm_right", "leg_left", "leg_right");
	/**
	 * 遍历时顺手记下身体那 6 根 + 它们的父骨 {@code body} 的引用（现在只用于诊断/自检；
	 * 2026-10-14 试过用它给原版模型/盔甲折父链，那套已按用户要求撤掉了，见 SKILL.md）。
	 */
	private static final Set<String> POSE_BONES = Set.of(
			"body", "chest", "head", "arm_left", "arm_right", "leg_left", "leg_right");
	/** 作者新增的近端定位器；它与 locator 的连线就是视觉枪管中心线。 */
	private static final String AIM_BONE = "aim";
	/** 当前渲染帧两个定位器的世界坐标（body 子树遍历结束后合成中心线）。 */
	private static Vec3d lastAimPos;
	private static Vec3d lastMuzzlePos;
	/** Recent animated locator position for each rendered player. */
	private static final Map<Integer, LocatorSample> LOCATOR_POSITIONS = new HashMap<>();
	private record LocatorSample(Vec3d position, long worldTime) {
	}
	/** 弹匣相关骨骼：打空后整组隐藏。 */
	private static final Set<String> MAG_BONES = Set.of("magazine_1", "magazine_2", "change3");
	/**
	 * **换弹里"新弹夹推到位"的那一拍**（tick，从 RELOAD 状态开始算）。
	 *
	 * <p>作者那段 `reload` 埋在 0.8333s：`change3` 的 scale 在 0.8333s 处 0→1（新弹夹出现）——
	 * 0.8333s × 20 tick/s ≈ 16.7 → 取 17。`magazine_1/2`（弹链）在换弹动画里没有通道，
	 * 要等这一拍之后才按"满弹夹"显回来（理由见 {@link #renderRecursively} 里那段注释）。
	 */
	private static final int RELOAD_MAG_IN_TICKS = 17;
	/**
	 * **枪口焰/速度线那两个"父骨"**：作者在每一段非射击动画里都把它们缩成 0
	 * （`fire` scale (1,0,0)、`smoke` scale (1,0,1)），子骨 `fire1..4` / `dash1..4` 跟着一起藏起来。
	 * 但"移动开火"的叠层（上半身 `stand_shooting`）**只写子骨自己的 scale、不碰父骨** ——
	 * 所以叠层生效时要在这里把父骨显回来，否则枪口焰会被父骨压成 0、根本看不见。
	 */
	private static final Set<String> FLASH_PARENT_BONES = Set.of("fire", "smoke");
	private static final String SHELL_PARENT_BONE = "shells";
	private static final Set<String> SHELL_BONES = Set.of(
			SHELL_PARENT_BONE,
			"shell", "shell2", "shell3", "shell4", "shell5", "shell6", "shell7");
	/**
	 * **只在真开火那一发才显形的"特效平面"**：枪口焰（fire1..4）、速度线（dash1..4）。
	 * 举枪（`stand_shooting` 的"持枪停帧"）那一遍要把它们压掉，否则等于空放一发（见 renderRecursively）。
	 *
	 * <p>⚠️ 2026-10-17（用户："蹲射结束 / 冲刺窗口 + 疾跑 + 装填时上半身换弹动作不完整"）：
	 * **弹壳 `shell..shell7` 必须从这张表里拿掉**。它们各有自己的控制器（`shell_eject_*` / `shell_hold_*`，
	 * 见 {@code BowgunRig#registerControllers} 的第三组），不用这里操心；留在这张表里的后果是
	 * **只要上半身叠层在演、又没在开火（换弹就是这种）就每帧被 hide()** —— 换弹时整条弹链是空的，
	 * 看着就是"上半身换弹动作不完整"（弹夹插进去了、弹链却没有）。
	 */
	private static final Set<String> FLASH_EFFECT_BONES = Set.of(
			"fire1", "fire2", "fire3", "fire4",
			"dash1", "dash2", "dash3", "dash4",
			// airflow 是 fire2/3/4 的子骨；父骨被复位时也要逐根压掉。
			"airflow1", "airflow2", "airflow3");
	/** 上半身那根：抬仰角就拧它（理由见 renderRecursively 里的注释）。 */
	private static final String CHEST_BONE = "chest";
	/** rig 的**根骨**（`body`，没有父骨骼）：瞄准对齐时整条模型都挂在它下面。 */
	private static final String ROOT_BONE = "body";
	/** 这位玩家现在是不是**正在开火**（= 枪口焰/弹壳该出现的那几帧）。 */
	private static boolean isFiring(net.minecraft.entity.Entity entity) {
		if (entity == null) {
			return false;
		}
		GunClientState.Entry e = GunClientState.get(entity.getId());
		return e != null && e.active() && e.state == TkGun.State.FIRE;
	}
	/** 空弹夹时用它把 poseStack 缩到看不见（不能缩到 0：矩阵会退化）。 */
	private static final float HIDDEN_SCALE = 1.0E-4F;

	public BowgunRigRenderer(EntityRendererFactory.Context context) {
		super(context, new BowgunRigModel(), BowgunRig.INSTANCE);
		addRenderLayer(new BowgunGunLayer(this));
		// **玩家皮肤第二层**（头发/帽子/衣服覆盖层）：用户 2026-10-17 要求。
		// 我们接管玩家模型后，身体 6 块骨骼的 UV 是按"第一层"写死的，原版那套第二层方块就没了 ——
		// 这一层把同样的方块按第二层 UV 再画一遍（并沿法线外扩一点点防 z-fighting），见 BowgunSkinLayer。
		addRenderLayer(new BowgunSkinLayer(this));
		// 原版 ArmorFeatureRenderer 随整条 PlayerEntityRenderer 一起被取消；这一层在同样的身体骨骼上
		// 按原版盔甲贴图重画头盔/胸甲/护腿/靴子，见 BowgunArmorLayer。
		addRenderLayer(new BowgunArmorLayer(this));
		addRenderLayer(new CombatBackpackRigLayer(this));
		// ⚠️ **气流那一层默认不开**（2026-10-16 用户实测反馈："枪口特效完全糊着、像管风琴一列一列
		//    排开、太小"）：v4 素材把枪口焰第 2/3/4 帧那三块平面改成采样 `airflow.png` 的一整帧
		//    （32x32 = 2 格大的白云团），三块正好在炮管前方 z=-66/-62/-58 排成一列 →
		//    开火时它们是"一列大白团"，把作者本来那圈从枪口向外炸开的枪口焰（`fire1..4`）糊掉了。
		//    离线预演（`_mech_work\preview_muzzle.py`）对比过：关掉之后留下的就是作者那圈爆开。
		//    所以**默认不画**（`BowgunAirflowLayer` 保留着，想要就把它 addRenderLayer 回来）。
		// addRenderLayer(new BowgunAirflowLayer(this));
	}

	/** 身体那 6 根骨头的引用（每帧遍历时顺手记下），给 {@link RigPose} 把位姿搬到原版模型上用。 */
	private final Map<String, GeoBone> bodyBones = new HashMap<>();

	public GeoBone bone(String name) {
		return bodyBones.get(name);
	}

	@Override
	public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer,
								  int packedLight, int packedOverlay, int colour) {
		if (!BODY_BONES.contains(bone.getName())) {
			return;   // 枪件由 BowgunGunLayer 换贴图补画
		}
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
	}

	/**
	 * 骨骼遍历本身（每根骨骼都必定经过，含没有方块的）—— 两件事都放这里：
	 *
	 * <ol>
	 *   <li><b>量枪口</b>：`locator` 骨骼的 poseStack 原点 = 枪口，-Z = 枪管方向（渲染空间是
	 *       相机相对坐标，加相机位置就是世界坐标）。⚠️ 别放在 `GeoRenderLayer#renderForBone` ——
	 *       那个**不会为没有方块的骨骼触发**（2026-10-14 实测：一次都没跑起来）。</li>
	 *   <li><b>弹链按实际弹量摆</b>：作者只在射击动画里标了吃弹链的位移（`magazine_1` 每发往前一格），
	 *       待机/架枪/过热这些状态会把它们重置回"满弹夹"。这里按服务端广播的弹量把骨骼**摆到
	 *       它应有的位置**（直接改骨骼状态：位置/缩放由下面的 {@code super.renderRecursively}
	 *       记进 poseStack）—— 不改作者数据，其它状态（尤其**过热**）就跟着弹量走了。</li>
	 * </ol>
	 *
	 * <p>射击动画（FIRE）中不修改弹链骨骼：动画自身负责逐发位移和最后一发的卸匣表现。
	 * 打完停住的 {@code AIM_HOLD} 也不用管：它保持的就是射击动画尾帧，弹链位置本来就是对的。
	 */
	@Override
	public void renderRecursively(MatrixStack poseStack, BowgunRig animatable, GeoBone bone, RenderLayer type,
								  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender,
								  float partialTick, int packedLight, int packedOverlay, int colour) {
		String name = bone.getName();
		Entity renderedEntity = getCurrentEntity();
		boolean currentFrame = !isReRender && renderedEntity != null;
		boolean local = currentFrame && renderedEntity == MinecraftClient.getInstance().player;
		if (currentFrame && ROOT_BONE.equals(name)) {
			lastAimPos = null;
			lastMuzzlePos = null;
		}
		if (POSE_BONES.contains(name)) {
			bodyBones.put(name, bone);   // 给 RigPose（原版模型/盔甲那层）用
		}

		// **上半身叠层（举枪 / 开火）时特效平面怎么处理**（2026-10-15 素材 v3）：
		//   * 真开火那一发：腿在 walk_*/idle 上，枪口焰/速度线/弹壳来自叠层的 `stand_shooting`
		//     → 把父骨 scale 写回 1（父骨被动画压成 0 的话子骨也看不见，见 FLASH_PARENT_BONES 注释）；
		//   * **只是举着枪**（没在开火）：`stand_shooting` 是同一段动画，头 0.04~0.2 秒里也埋着
		//     枪口焰 + 弹壳甩出 —— 那会变成"举枪空放一发"。所以这时候把**特效骨压掉**
		//     （火焰平面/速度线/弹壳），只留端枪的姿势。
		Entity poseEntity = getCurrentEntity();
		// 是否开火必须是**全局 FIRE 状态**，不能乘上 `upper`：专注射击是全身动画，
		// upper 层为空；上一版用 `upper && isFiring` 会把专注开火的枪口焰也误判成“没开火”。
		boolean firing = isFiring(poseEntity);
		boolean upper = BowgunRig.upperLayerActive(poseEntity);
		com.tk.gun.client.GunClientState.Entry upperState =
				poseEntity == null ? null : com.tk.gun.client.GunClientState.get(poseEntity.getId());
		boolean bodyLocked = upper && poseEntity != null
				&& !com.tk.gun.client.GunClientState.focusish(poseEntity.getId())
				&& !isFullBodyState(upperState);
		// idle_lower 保留了作者完整 idle 的 body.rotation.y=45°。它只适合全身 idle；
		// 和 stand_shooting_hold 叠层时会把整个上半身一起拧右约 45°。
		if (bodyLocked && "body".equals(name)) {
			bone.setRotY(0.0F);
			// run_lower 的 body 前倾 27.5° 不能在切到持枪行走/站立后残留；
			// 真正的奔跑仍由完整 run 动画驱动，不走 bodyLocked 分支。
			bone.setRotX(0.0F);
		}
		if (bodyLocked && CHEST_BONE.equals(name)) {
			String walk = TkGunClient.walkAnimation(poseEntity.getId());
			if (walk != null && walk.startsWith("walk_")) {
				// 把作者 walk_* 的 chest.position.y 曲线叠加到腰射上半身。
				float bobPx = walkChestBobPx(TkGunClient.walkPhase(poseEntity.getId()));
				poseStack.translate(0.0F, bobPx / 16.0F, 0.0F);
			}
		}

		// —— 视角跟随（作者 v4 素材的 Molang）**只读验收**：值现在是 GeckoLib 求出来的 ——
		// 见 `client/TkGunMolang`（它把那 4 个 query 对 tkgun rig 的实现补上了）。
		// 这里只把"实际落到骨头上的角度"和"按公式该是多少"并排打出来，方便一眼看出 Molang 有没有生效。
		if (local && VERBOSE && VIEW_FOLLOW_BONES.contains(name) && poseEntity != null) {
			long now = poseEntity.getWorld().getTime();
			// ⚠️ 节流要**按骨头各记一份**：用同一个"上次打印 tick"的话，先被遍历到的那根骨头会
			// 把同一 tick 里另一根顶掉（第一次写就是这么漏掉 chest 的）。
			if (now % 40 == 0 && now != lastViewLogTick.getOrDefault(name, -1L)) {
				lastViewLogTick.put(name, now);
				float pitch = poseEntity.getPitch();
				float delta = poseEntity instanceof net.minecraft.entity.LivingEntity living
						? net.minecraft.util.math.MathHelper.wrapDegrees(living.getHeadYaw() - living.getBodyYaw())
						: 0.0F;
				TkGun.LOGGER.info("[tkgun] 跟随视角 {}：骨 rotX={}° / rotY={}° ｜ 实体 pitch={}° 头身差={}°"
								+ "（Molang 生效的话 rotX≈pitch/3{}：chest 才有；rotY≈头身差/{}）",
						name, r(bone.getRotX()), r(bone.getRotY()), r(pitch), r(delta),
						CHEST_BONE.equals(name) ? "" : "（body 没有纵向）",
						CHEST_BONE.equals(name) ? 3 : 6);
			}
		}

		if (firing && FLASH_PARENT_BONES.contains(name)) {
			bone.setScaleX(1.0F);
			bone.setScaleY(1.0F);
			bone.setScaleZ(1.0F);
		} else if (!firing && (FLASH_PARENT_BONES.contains(name)
				|| FLASH_EFFECT_BONES.contains(name))) {
			// 只要不在 FIRE 状态，父骨和特效子骨都压掉。这样 FIRE→IDLE 的过渡帧、
			// 翻滚、滑铲、换弹和腰射松右键都不会漏出一帧枪口焰/速度线。
			hide(bone);
		}
		if (SHELL_BONES.contains(name)) {
			if (firing) {
				if (SHELL_PARENT_BONE.equals(name)) {
					bone.setScaleX(1.0F);
					bone.setScaleY(1.0F);
					bone.setScaleZ(1.0F);
				}
			} else {
				hide(bone);
			}
		}

		// （原"双手 + 枪"多段旋转补偿已撤销，见文件上方注释。）

		// ⚠️ 枪骨角度**一动不动**（用户 2026-10-14："不用微调枪的角度去瞄准…你只需要考虑整体模型左右旋转"）。
		// 横向瞄准由 GunCamera 转实体 yaw（= 整个模型）保证；**竖向（打多高）现在由素材自己算** ——
		// 2026-10-15 素材 v3 把 `_22.5`/`_45` 变体删了，改成动画里写 Molang
		// （`chest.rotation.x = -query.body_x_rotation / 3`，GeckoLib 会拿实体俯仰求值）。
		// 这里原来"给 chest 补抬角"的那段**删掉了**（会和 Molang 叠成两倍）。

		// 量枪口：**每一帧都量**（用户 2026-10-14："你这个东西要一直检测的呀"），
		// 并且把 locator 自己的 pivot 偏移补回去（理由见 MUZZLE_BONE/LOCATOR_IN_GUN 的注释）。
		if (currentFrame && AIM_BONE.equals(name)) {
			org.joml.Vector3d wp = bone.getWorldPosition();
			lastAimPos = new Vec3d(wp.x, wp.y, wp.z);
		}
		if (currentFrame && MUZZLE_BONE.equals(name)) {
			Matrix4f m = poseStack.peek().getPositionMatrix();
			// ⭐ 枪口 = GeckoLib **自己算的** locator 世界坐标（`getWorldPosition()`，就是为 locator 骨骼
			//    准备的接口）。2026-10-14 实测它和我按字节码推的"绝对 pivot"版本只差 0.6 格（同一个点），
			//    而"相对 gun 的差 / 完全不补 / 玩家模型固定推算"那三种都差 2 格左右 —— 那才是
			//    "打第二发起跑到枪托/枪口左边"的根源。
			org.joml.Vector3d wp = bone.getWorldPosition();
			Vector3f d = m.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
			lastMuzzlePos = new Vec3d(wp.x, wp.y, wp.z);
		}
		// 诊断（-Dtkgun.demo 才打）：候选点对照，定下来之后可以删
		if (local && MUZZLE_BONE.equals(name) && VERBOSE && (probe++ % 60) == 0) {
			Matrix4f m = poseStack.peek().getPositionMatrix();
			Vec3d cam0 = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
			org.joml.Vector3d wp = bone.getWorldPosition();
			LOCATOR_WORLD_POS[0] = wp.x;
			LOCATOR_WORLD_POS[1] = wp.y;
			LOCATOR_WORLD_POS[2] = wp.z;
			Vector3f[] offsets = {
					new Vector3f(LOCATOR_IN_GUN),
					new Vector3f(0.0F, 4.0F / 16.0F, -45.0F / 16.0F),
					new Vector3f(0.0F, 0.0F, 0.0F)};
			for (int i = 0; i < offsets.length; i++) {
				Vector3f q = m.transformPosition(new Vector3f(offsets[i]), new Vector3f());
				MUZZLE_CANDIDATES[i][0] = cam0.x + q.x;
				MUZZLE_CANDIDATES[i][1] = cam0.y + q.y;
				MUZZLE_CANDIDATES[i][2] = cam0.z + q.z;
			}
			TkGun.LOGGER.info("[tkgun] 枪口候选：GeckoLib=({}, {}, {}) 绝对pivot=({}, {}, {}) 相对差=({}, {}, {}) 不补=({}, {}, {})",
					r(wp.x), r(wp.y), r(wp.z),
					r(MUZZLE_CANDIDATES[0][0]), r(MUZZLE_CANDIDATES[0][1]), r(MUZZLE_CANDIDATES[0][2]),
					r(MUZZLE_CANDIDATES[1][0]), r(MUZZLE_CANDIDATES[1][1]), r(MUZZLE_CANDIDATES[1][2]),
					r(MUZZLE_CANDIDATES[2][0]), r(MUZZLE_CANDIDATES[2][1]), r(MUZZLE_CANDIDATES[2][2]));
		}

		if (MAG_BONES.contains(name) && getCurrentEntity() != null) {
			GunClientState.Entry e = GunClientState.get(getCurrentEntity().getId());
			int capacity = GunClientState.capacity(getCurrentEntity().getId());
			int fired = e == null ? 0 : Math.max(0, Math.min(capacity, e.mag));
			TkGun.State state = e == null ? TkGun.State.IDLE : e.state;
			boolean mergedFocusFire = e != null && state == TkGun.State.FIRE && e.focus
					&& TkGun.mergedFocusShot(e.shotIndex, e.maxShotIndex);
			// 普通专注射击按两段弹链阶段共用动画；这里按发数叠加阶段内偏移，
			// 等价于原 Focus_shooting1..5 中仅有的差异。
			//
			// **`RELOAD` 里分两种骨头**（2026-10-17 用户第二次报："在满弹匣时奔跑换弹上半身换弹动画
			// 不完整"）：
			//   * `change3`（弹夹本体）**一帧都不碰** —— 作者那段 `reload` 就是"旧弹夹滑出 → 0.8333s
			//     处新弹夹啪一下滑到位（scale 0→1）→ 再收进枪身"，我们按"已打几发"去摆会把它盖掉
			//     （满弹匣时最明显：按 F 之前它正好被我们藏起来了）；
			//   * `magazine_1` / `magazine_2`（弹链）**换弹动画里根本没有通道** —— 上一状态“已打满”
			//     时被我们 `hide()` 藏起来了，不主动显回来，整段换弹就看不到弹链，看着就是"动画不完整"。
			//     所以按"新弹夹 = 已打 0 发"摆回来，但要**等新弹夹推到位之后**（0.8333s ≈ 17 tick）——
			//     提前显会看到"弹夹还没插、弹链已经在枪上"。
			boolean reloading = e != null && (state == TkGun.State.RELOAD || e.reloading);
			boolean magIn = reloading && e.elapsed >= RELOAD_MAG_IN_TICKS;
			if (mergedFocusFire) {
				show(bone);
				int firstChainEnd = Math.max(3, e.maxShotIndex - 4);
				boolean firstChainPhase = e.shotIndex <= firstChainEnd;
				if ("magazine_1".equals(name)) {
					if (firstChainPhase) {
						bone.setPosX(bone.getPosX() - (e.shotIndex - 4));
					}
					// 第二段阶段沿用 canonical 动画里的 -3 px 静态位置。
				} else if ("magazine_2".equals(name)) {
					if (firstChainPhase) {
						bone.setPosX(0.0F);
					} else {
						int secondChainStart = e.maxShotIndex - 3;
						bone.setPosX(bone.getPosX() - (e.shotIndex - secondChainStart));
					}
				}
			} else if (state != TkGun.State.FIRE && (!reloading || magIn)
					&& !(reloading && "change3".equals(name))) {
				int shown = magIn ? 0 : fired;   // 换弹里按"新弹夹"摆
				if (shown >= capacity) {
					// 弹匣已经打空并卸下：隐藏整组。
					// 缩父骨 change3 就够（子骨骼跟着），三根都缩只是省得逐个去想。
					hide(bone);
				} else {
					if ("magazine_1".equals(name)) {
						// 弹链：magazine_1 每发往前一格（0→-3），第 4 发起 magazine_2 接着走
						// （这几个数沿用作者原逐发动画里的位移，单位 = 模型 px）
						bone.setPosX(-Math.min(shown, 3));
						show(bone);
					} else if ("magazine_2".equals(name)) {
						bone.setPosX(-Math.max(0, shown - 3));
						show(bone);
					} else if (state == TkGun.State.OVERHEAT) {
						// 过热动画只写了 change3 的位移、没写缩放 —— 万一上一段打空后留下的
						// scale 0 还在，这里把弹夹显回来（用户要的就是"过热时弹夹按实际弹量摆"）。
						show(bone);
					}
					// ⚠️ 架枪（Focus_ready）里 **change3 的 scale 不要动**：作者用它做"弹夹从枪身里
					//    滑到位"（0 → 0 → 1），显回来反而会看到弹夹一跳。
					if (local && (lastLoggedState != state || lastLoggedFired != fired)) {
						lastLoggedState = state;
						lastLoggedFired = fired;
						TkGun.LOGGER.info("[tkgun] 弹链/弹夹按实际弹量摆：{} 已打 {} 发 → 骨 {} = x{}px scale{}",
								state, fired, name, bone.getPosX(), bone.getScaleX());
					}
				}
			}
		}

		super.renderRecursively(poseStack, animatable, bone, type, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
		// 两个定位器都在 body 子树里；body 的 super 返回时它们这一帧都已遍历完。
		// 用 locator - aim 得到视觉枪管中心线，而不是假设枪沿 locator 的局部 -Z。
		if (currentFrame && ROOT_BONE.equals(name)
				&& lastAimPos != null && lastMuzzlePos != null) {
			LOCATOR_POSITIONS.put(renderedEntity.getId(),
					new LocatorSample(lastMuzzlePos, renderedEntity.getWorld().getTime()));
			Vec3d dir = lastMuzzlePos.subtract(lastAimPos);
			if (local && dir.lengthSquared() > 1.0E-8D) {
				TkGunClient.captureMuzzle(lastAimPos, lastMuzzlePos, dir.normalize());
			}
		}
	}

	/** Latest rendered gun locator, rejected when the model has not rendered recently. */
	public static Vec3d locatorPosition(int playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		LocatorSample sample = LOCATOR_POSITIONS.get(playerId);
		if (client.world == null || sample == null
				|| Math.abs(client.world.getTime() - sample.worldTime()) > 3L) {
			return null;
		}
		return sample.position;
	}

	/**
	 * 精确复刻作者 walk_front/back/left/right 里 `chest.position.y` 的 1 秒循环：
	 * 0→-1→+0.1→0（前半圈），0→-1→0（后半圈）。单位仍是模型像素。
	 */
	private static float walkChestBobPx(float phase) {
		float p = phase - (float) Math.floor(phase);
		if (p < 1.0F / 12.0F) {
			return lerp(p, 0.0F, 1.0F / 12.0F, 0.0F, -1.0F);
		}
		if (p < 2.0F / 12.0F) {
			return lerp(p, 1.0F / 12.0F, 2.0F / 12.0F, -1.0F, 0.1F);
		}
		if (p < 0.5F) {
			return lerp(p, 2.0F / 12.0F, 0.5F, 0.1F, 0.0F);
		}
		if (p < 7.0F / 12.0F) {
			return lerp(p, 0.5F, 7.0F / 12.0F, 0.0F, -1.0F);
		}
		if (p < 8.0F / 12.0F) {
			return lerp(p, 7.0F / 12.0F, 8.0F / 12.0F, -1.0F, 0.0F);
		}
		return 0.0F;
	}

	private static float lerp(float t, float t0, float t1, float v0, float v1) {
		return v0 + (v1 - v0) * ((t - t0) / (t1 - t0));
	}

	private static boolean isFullBodyState(com.tk.gun.client.GunClientState.Entry e) {
		return e != null && (e.state == TkGun.State.RELOAD
				|| e.state == TkGun.State.ROLL
				|| e.state == TkGun.State.ROLL_TO_IDLE
				|| e.state == TkGun.State.SLIDE
				|| e.state == TkGun.State.SLIDE_READY
				|| e.state == TkGun.State.OVERHEAT
				|| e.state == TkGun.State.OVER);
	}

	// 2026-10-17：**第四十二/四十三轮的 chest 俯仰补偿整段撤销**（用户："撤销 42 与 43 轮俯仰角的修正"）。
	// 原因：作者素材里 chest 只跟 1/3 俯仰是**设计**，我们补了 2/3（后来又缩到 2/9）都会让上身姿势
	// 偏离作者关键帧；而真正要修的是**水平**（见 GunCamera 的 yaw 环 + 渲染期强制 yaw）。

	/** 藏：把骨骼缩到看不见（不能缩到 0，矩阵会退化）。 */
	private static void hide(GeoBone bone) {
		bone.setScaleX(HIDDEN_SCALE);
		bone.setScaleY(HIDDEN_SCALE);
		bone.setScaleZ(HIDDEN_SCALE);
	}

	/** 枪那一层的根骨（parent = chest）：瞄准修正整套套在它身上，子骨骼（含枪口 locator）跟着走。 */
	private static final String GUN_BONE = "gun";

	// ⚠️ 2026-10-17 **"双手 + 枪"多段旋转整套已撤销**（用户："撤销关于双手 / 身体切分 + 多段旋转的效果"）。
	//    原来这里有一套 WEAPON_BONES / WEAPON_FOLLOW，按"相机 yaw − 身体 yaw"给 arm_left/arm_right/gun
	//    加一个补偿角。撤销原因：① 先在别的动画里乱摆抽搐（已限定状态+平滑，但仍是"额外旋转"）；
	//    ② 配套的 "hands" 控制器在换弹时会 STOP、把 `reload_upper` 的手臂动作复位掉。
	//    现在双手/枪只由作者动画驱动，其它系统一概不加角度。
	/** 枪口 = 作者放在 bbmodel 里的 **`locator`**（position `[0,10,-36]`）：一直在枪管最前端。 */
	private static final String MUZZLE_BONE = "locator";

	/**
	 * `locator` 的 **绝对 pivot**（单位：格）= `(0, 10, -37)/16`。
	 *
	 * <p>**为什么必须自己补这一项**（2026-10-14 逐条读完 GeckoLib 字节码确认的）：
	 * <ol>
	 *   <li>`GeoRenderer.renderRecursively` = {@code push() → RenderUtil.prepMatrixForBone(poseStack, bone)
	 *       → renderCubesOfBone → renderChildBones → pop()}，也就是**骨头自己的变换是在 super 里面才应用的**；
	 *       我这个钩子在它之前跑，拿到的 poseStack 只到**父骨**为止。</li>
	 *   <li>`prepMatrixForBone` = `translate(pos)` → **`translate(骨头的 pivot)`（绝对值！）** → 旋转 → 缩放
	 *       → `translate(-pivot)`。父骨最后那步 `translate(-pivot)` 会把坐标系**退回模型原点**，
	 *       所以下一根骨用的是它自己的**绝对** pivot —— 也就是说，我在钩子里拿到的矩阵原点是**模型原点**，
	 *       要补的就是 locator 自己的绝对 pivot `(0, 10, -37)/16 = (0, 0.625, -2.3125)`。</li>
	 * </ol>
	 * 之前用"相对父骨 gun 的差"是错的：gun 骨在不同动画里位移不同，误差就跟着变 ——
	 * 表现就是"第一发对、后面几发跑到枪托/枪身中段"。
	 */
	private static final Vector3f LOCATOR_IN_GUN = new Vector3f(0.0F, 10.0F / 16.0F, -37.0F / 16.0F);
	/** 候选枪口点（世界坐标）：[0] 绝对 pivot [1] 相对 gun 的差 [2] 不补 [3] 玩家模型推算。 */
	public static final double[][] MUZZLE_CANDIDATES = new double[4][3];
	/** 子弹实际用哪个候选（0..3）。 */
	public static final int MUZZLE_CANDIDATE = 1;
	/** 诊断：locator 骨骼自己的 getWorldPosition（模型空间），用来判断单位/坐标系。 */
	public static double[] LOCATOR_WORLD_POS = new double[3];

	/**
	 * **瞄准修正**（用户 2026-10-14："长按右键瞄准的时候枪整体偏移一下，让枪口对准瞄准准星、重合一下"）：
	 * 在 `gun` 骨这一层给整把枪套一个**世界空间**变换
	 * {@code A = T(平移) · T(枪口) · R(把枪管转到瞄准方向) · T(-枪口)} ——
	 * 先绕**枪口点**把枪管转到 {@link GunCamera#aimBarrelDir()}，再把枪口平移到准星射线上
	 * （绕枪口转 = 枪口不动，所以"转"和"挪"互不干扰）。
	 *
	 * <p>为什么要绕枪口转：{@code BowgunRigRenderer} 量出来的"枪口"和"枪管方向"是给服务端开火用的
	 * （`locator` 骨骼的位姿）—— 把枪管真的转到"枪口 → 准星落点"，服务端拿到的方向就自动是
	 * **指哪打哪**，子弹依旧从枪口出来，服务端一行都不用改。
	 *
	 * <p>矩阵怎么套：poseStack 只能右乘（那是在**骨骼局部空间**里做变换），所以要把世界空间的 A
	 * 换算成局部：{@code L = P⁻¹ · A · P}（P = 当前 poseStack），再 {@code multiplyPositionMatrix(L)}。
	 * 相机相对坐标（poseStack 用的就是相机相对）里，相机位置就是原点，所以 A 里的点要减掉相机位置。
	 */
	/**
	 * 显：只有在**当前被藏起来**的时候才写回 1。
	 *
	 * <p>为什么不无脑写 1：GeckoLib 对"动画里没有通道的骨骼"是**重置回 geo 初值**的，
	 * 但那是在动画处理器那一层；这里改的是同一帧渲染用的骨骼状态，少写就少一分副作用。
	 * 反过来（打空后藏起来，正常状态里没人再动过那根骨骼）就必须写回来，
	 * 否则"弹夹插回去了但弹链看不见"。
	 */
	private static void show(GeoBone bone) {
		if (bone.getScaleX() < 0.5F || bone.getScaleY() < 0.5F || bone.getScaleZ() < 0.5F) {
			bone.setScaleX(1.0F);
			bone.setScaleY(1.0F);
			bone.setScaleZ(1.0F);
		}
	}
	/** 只给上面那行日志做"变化才打"用。 */
	private static int lastLoggedFired = -1;
	private static TkGun.State lastLoggedState = null;
	private static int probe;
	/** 诊断日志开关：`-Dtkgun.demo=1`（自动演示）或 `-Dtkgun.verbose=1`（只看日志不自动按键）。 */
	private static final boolean VERBOSE = System.getProperty("tkgun.demo") != null
			|| System.getProperty("tkgun.verbose") != null;

	// ---------------------------------------------------------------- 视角跟随（作者 v4 的 Molang）只读验收
	/** 打印用的两根骨头（值由 `client/TkGunMolang` 补的那 4 个 query 算出来）。 */
	private static final Set<String> VIEW_FOLLOW_BONES = Set.of("chest", "body");
	private static final Map<String, Long> lastViewLogTick = new HashMap<>();

	private static double r(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
