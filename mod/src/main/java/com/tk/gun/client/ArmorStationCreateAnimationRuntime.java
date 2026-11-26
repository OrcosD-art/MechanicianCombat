package com.tk.gun.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tk.gun.block.ArmorDressingStationBlockEntity;
import com.tk.gun.entity.ArmorDressingPerformerEntity;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Create-inspired cached pose evaluator for the comparison workstation.
 *
 * <p>The authored keyframes are compiled into primitive arrays once. Drawing
 * only consumes the resulting local transforms; it never searches or sorts the
 * animation JSON.</p>
 */
public final class ArmorStationCreateAnimationRuntime {
	private static final double HANDOFF_HOLD_SECONDS = 0.1D;
	private static final double HANDOFF_BLEND_SECONDS = 0.1D;
	private static final Map<String, BoneClip> CLIPS =
			new ConcurrentHashMap<>();
	private static JsonObject animations;

	public static void applyPerformerRoot(
			ArmorDressingPerformerEntity performer, GeoBone root,
			float partialTick) {
		String animationName = performer.animationName();
		JsonObject bones = animationBones(animationName);
		if (bones == null) {
			return;
		}
		applyTree(animationName, bones, root,
				performer.animationSeconds(partialTick));
	}

	public static void applyStationRoot(
			ArmorDressingStationBlockEntity station, GeoBone root,
			float partialTick) {
		if (station.phase()
				== ArmorDressingStationBlockEntity.Phase.INACTIVE) {
			return;
		}
		double seconds = station.animationSeconds(partialTick);
		if (station.phase()
				== ArmorDressingStationBlockEntity.Phase.DRESSING) {
			String dressingAnimation = station.dressingAnimation();
			JsonObject dressingBones = animationBones(dressingAnimation);
			if (dressingBones != null) {
				applyTree(dressingAnimation, dressingBones, root, seconds);
			}
			return;
		}
		String animationName = switch (station.phase()) {
			case INACTIVE -> "未启动";
			case DRESSING -> "盔甲着装";
			case ACTIVE -> "激活";
		};
		JsonObject bones = animationBones(animationName);
		if (bones == null) {
			return;
		}
		if (ArmorDressingClientState.dressingHandoffActive(
				station.getPos())
				&& seconds < HANDOFF_HOLD_SECONDS
						+ HANDOFF_BLEND_SECONDS) {
			String sourceName = station.dressingAnimation();
			JsonObject sourceBones = animationBones(sourceName);
			if (sourceBones != null) {
				double progress = Math.max(0.0D, Math.min(1.0D,
						(seconds - HANDOFF_HOLD_SECONDS)
								/ HANDOFF_BLEND_SECONDS));
				applyBlendedTree(sourceName, sourceBones,
						animationName, bones, root,
						animationLength(sourceName), seconds, progress);
				if ("body".equals(root.getName())) {
					root.updateRotation(0.0F, 0.0F, 0.0F);
				}
				return;
			}
		}
		applyTree(animationName, bones, root, seconds);
		if (station.phase()
				== ArmorDressingStationBlockEntity.Phase.ACTIVE
				&& "body".equals(root.getName())) {
			// The final active orientation uses the neutral station axis;
			// the seated player keeps the counter-rotated neutral heading.
			root.updateRotation(0.0F, 0.0F, 0.0F);
		}
	}

	static float dressingTailSeconds(
			ArmorDressingStationBlockEntity station) {
		return (float) animationLength(station.dressingAnimation());
	}

	private static void applyTree(String animationName, JsonObject bones,
			GeoBone bone, double time) {
		reset(bone);
		String key = animationName + '\0' + bone.getName();
		BoneClip clip = CLIPS.computeIfAbsent(key,
				ignored -> compileClip(bones, bone.getName()));
		clip.apply(bone, time);
		for (GeoBone child : bone.getChildBones()) {
			applyTree(animationName, bones, child, time);
		}
	}

	private static void applyBlendedTree(String sourceName,
			JsonObject sourceBones, String targetName, JsonObject targetBones,
			GeoBone bone, double sourceTime, double targetTime,
			double progress) {
		reset(bone);
		String sourceKey = sourceName + '\0' + bone.getName();
		String targetKey = targetName + '\0' + bone.getName();
		BoneClip source = CLIPS.computeIfAbsent(sourceKey,
				ignored -> compileClip(sourceBones, bone.getName()));
		BoneClip target = CLIPS.computeIfAbsent(targetKey,
				ignored -> compileClip(targetBones, bone.getName()));
		BoneSnapshot initial = bone.getInitialSnapshot();
		double eased = progress * progress * (3.0D - 2.0D * progress);

		double[] sourceRotation = source.rotation == null ? null
				: source.rotation.sample(sourceTime);
		double[] targetRotation = target.rotation == null ? null
				: target.rotation.sample(targetTime);
		float initialX = initial == null ? bone.getRotX()
				: initial.getRotX();
		float initialY = initial == null ? bone.getRotY()
				: initial.getRotY();
		float initialZ = initial == null ? bone.getRotZ()
				: initial.getRotZ();
		bone.updateRotation(
				initialX + (float) Math.toRadians(blendComponent(
						sourceRotation, targetRotation, 0, 0.0D, eased)),
				initialY + (float) Math.toRadians(blendComponent(
						sourceRotation, targetRotation, 1, 0.0D, eased)),
				initialZ + (float) Math.toRadians(blendComponent(
						sourceRotation, targetRotation, 2, 0.0D, eased)));

		double[] sourcePosition = source.position == null ? null
				: source.position.sample(sourceTime);
		double[] targetPosition = target.position == null ? null
				: target.position.sample(targetTime);
		float offsetX = initial == null ? bone.getPosX()
				: initial.getOffsetX();
		float offsetY = initial == null ? bone.getPosY()
				: initial.getOffsetY();
		float offsetZ = initial == null ? bone.getPosZ()
				: initial.getOffsetZ();
		bone.updatePosition(
				(float) blendComponent(sourcePosition, targetPosition, 0,
						offsetX, eased),
				(float) blendComponent(sourcePosition, targetPosition, 1,
						offsetY, eased),
				(float) blendComponent(sourcePosition, targetPosition, 2,
						offsetZ, eased));

		double[] sourceScale = source.scale == null ? null
				: source.scale.sample(sourceTime);
		double[] targetScale = target.scale == null ? null
				: target.scale.sample(targetTime);
		float scaleX = initial == null ? bone.getScaleX()
				: initial.getScaleX();
		float scaleY = initial == null ? bone.getScaleY()
				: initial.getScaleY();
		float scaleZ = initial == null ? bone.getScaleZ()
				: initial.getScaleZ();
		bone.updateScale(
				(float) blendComponent(sourceScale, targetScale, 0,
						scaleX, eased),
				(float) blendComponent(sourceScale, targetScale, 1,
						scaleY, eased),
				(float) blendComponent(sourceScale, targetScale, 2,
						scaleZ, eased));
		for (GeoBone child : bone.getChildBones()) {
			applyBlendedTree(sourceName, sourceBones, targetName, targetBones,
					child, sourceTime, targetTime, progress);
		}
	}

	private static double blendComponent(double[] source, double[] target,
			int component, double fallback, double progress) {
		double from = source == null ? fallback : source[component];
		double to = target == null ? fallback : target[component];
		return lerp(from, to, progress);
	}

	private static BoneClip compileClip(JsonObject bones,
			String boneName) {
		JsonObject source = findBoneAnimation(bones, boneName);
		if (source == null) {
			return BoneClip.EMPTY;
		}
		return new BoneClip(compileTrack(source.get("rotation")),
				compileTrack(source.get("position")),
				compileTrack(source.get("scale")));
	}

	private static Track compileTrack(JsonElement channel) {
		if (channel == null || channel.isJsonNull()) {
			return null;
		}
		if (channel.isJsonArray()) {
			double[] value = vector(channel.getAsJsonArray());
			return new Track(new float[] {0.0F},
					new double[][] {value}, new double[][] {value});
		}
		List<Map.Entry<String, JsonElement>> frames =
				new ArrayList<>(channel.getAsJsonObject().entrySet());
		frames.sort(Comparator.comparingDouble(entry -> {
			try {
				return Double.parseDouble(entry.getKey());
			} catch (NumberFormatException ignored) {
				return Double.POSITIVE_INFINITY;
			}
		}));
		List<Float> times = new ArrayList<>(frames.size());
		List<double[]> pre = new ArrayList<>(frames.size());
		List<double[]> post = new ArrayList<>(frames.size());
		for (Map.Entry<String, JsonElement> frame : frames) {
			double[] before = vectorValue(frame.getValue(), false);
			double[] after = vectorValue(frame.getValue(), true);
			if (before == null || after == null) {
				continue;
			}
			times.add(Float.parseFloat(frame.getKey()));
			pre.add(before);
			post.add(after);
		}
		if (times.isEmpty()) {
			return null;
		}
		float[] compiledTimes = new float[times.size()];
		double[][] compiledPre = pre.toArray(double[][]::new);
		double[][] compiledPost = post.toArray(double[][]::new);
		for (int index = 0; index < times.size(); index++) {
			compiledTimes[index] = times.get(index);
		}
		return new Track(compiledTimes, compiledPre, compiledPost);
	}

	private static double[] vectorValue(JsonElement value, boolean post) {
		if (value.isJsonArray()) {
			return vector(value.getAsJsonArray());
		}
		JsonObject object = value.getAsJsonObject();
		JsonElement selected = object.get(post ? "post" : "pre");
		if (selected == null) {
			selected = object.get(post ? "pre" : "post");
		}
		return selected != null && selected.isJsonArray()
				? vector(selected.getAsJsonArray()) : null;
	}

	private static double[] vector(JsonArray array) {
		return new double[] {
				array.get(0).getAsDouble(),
				array.get(1).getAsDouble(),
				array.get(2).getAsDouble()
		};
	}

	private static void reset(GeoBone bone) {
		BoneSnapshot snapshot = bone.getInitialSnapshot();
		if (snapshot == null) {
			return;
		}
		bone.updateRotation(snapshot.getRotX(), snapshot.getRotY(),
				snapshot.getRotZ());
		bone.updatePosition(snapshot.getOffsetX(), snapshot.getOffsetY(),
				snapshot.getOffsetZ());
		bone.updateScale(snapshot.getScaleX(), snapshot.getScaleY(),
				snapshot.getScaleZ());
	}

	private static JsonObject findBoneAnimation(JsonObject bones,
			String boneName) {
		if (bones == null) {
			return null;
		}
		JsonObject direct = bones.getAsJsonObject(boneName);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
			if (matchesBoneName(boneName, entry.getKey())) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static boolean matchesBoneName(String actual,
			String authored) {
		if (authored.equals(actual)) {
			return true;
		}
		byte[] utf8 = authored.getBytes(StandardCharsets.UTF_8);
		for (Charset charset : candidateCharsets()) {
			if (new String(utf8, charset).equals(actual)) {
				return true;
			}
		}
		return false;
	}

	private static List<Charset> candidateCharsets() {
		List<Charset> candidates = new ArrayList<>(6);
		candidates.add(Charset.defaultCharset());
		for (String name : new String[] {
				"GB18030", "GBK", "windows-1252", "ISO-8859-1"
		}) {
			if (Charset.isSupported(name)) {
				Charset charset = Charset.forName(name);
				if (!candidates.contains(charset)) {
					candidates.add(charset);
				}
			}
		}
		return candidates;
	}

	private static JsonObject animationBones(String animationName) {
		JsonObject definitions = definitions();
		if (definitions == null) {
			return null;
		}
		JsonObject animation = definitions.getAsJsonObject(animationName);
		return animation == null ? null
				: animation.getAsJsonObject("bones");
	}

	private static double animationLength(String animationName) {
		JsonObject definitions = definitions();
		if (definitions == null) {
			return 0.0D;
		}
		JsonObject animation = definitions.getAsJsonObject(animationName);
		if (animation == null || !animation.has("animation_length")) {
			return 0.0D;
		}
		return animation.get("animation_length").getAsDouble();
	}

	private static JsonObject definitions() {
		if (animations != null) {
			return animations;
		}
		try (var stream = ArmorStationCreateAnimationRuntime.class
				.getResourceAsStream(
						"/assets/mechaniciancombat/animations/block/"
								+ "armor_dressing_station.animation.json")) {
			if (stream == null) {
				return null;
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
			animations = root.getAsJsonObject("animations");
			return animations;
		} catch (Exception exception) {
			com.tk.gun.TkGun.LOGGER.error(
					"[mechaniciancombat] Unable to compile armor station "
							+ "Create-style animation",
					exception);
			return null;
		}
	}

	private record BoneClip(Track rotation, Track position, Track scale) {
		private static final BoneClip EMPTY =
				new BoneClip(null, null, null);

		private void apply(GeoBone bone, double time) {
			double[] value = rotation == null ? null : rotation.sample(time);
			if (value != null) {
				BoneSnapshot initial = bone.getInitialSnapshot();
				float initialX = initial == null ? bone.getRotX()
						: initial.getRotX();
				float initialY = initial == null ? bone.getRotY()
						: initial.getRotY();
				float initialZ = initial == null ? bone.getRotZ()
						: initial.getRotZ();
				bone.updateRotation(
						initialX + (float) Math.toRadians(value[0]),
						initialY + (float) Math.toRadians(value[1]),
						initialZ + (float) Math.toRadians(value[2]));
			}
			value = position == null ? null : position.sample(time);
			if (value != null) {
				bone.updatePosition((float) value[0], (float) value[1],
						(float) value[2]);
			}
			value = scale == null ? null : scale.sample(time);
			if (value != null) {
				bone.updateScale((float) value[0], (float) value[1],
						(float) value[2]);
			}
		}
	}

	private record Track(float[] times, double[][] pre, double[][] post) {
		private double[] sample(double time) {
			if (times.length == 0) {
				return null;
			}
			if (times.length == 1 || time <= times[0]) {
				return post[0];
			}
			if (time >= times[times.length - 1]) {
				return post[post.length - 1];
			}
			for (int next = 1; next < times.length; next++) {
				if (Math.abs(time - times[next]) < 1.0E-7D) {
					return post[next];
				}
				if (time < times[next]) {
					int previous = next - 1;
					double progress = (time - times[previous])
							/ (times[next] - times[previous]);
					return new double[] {
							lerp(post[previous][0], pre[next][0],
									progress),
							lerp(post[previous][1], pre[next][1],
									progress),
							lerp(post[previous][2], pre[next][2],
									progress)
					};
				}
			}
			return post[post.length - 1];
		}
	}

	private static double lerp(double from, double to,
			double progress) {
		return from + (to - from) * progress;
	}

	private ArmorStationCreateAnimationRuntime() {
	}
}
