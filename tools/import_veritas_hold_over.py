import json
import sys


def point(keyframe):
	data = keyframe["data_points"][0]
	return [data.get("x", 0), data.get("y", 0), data.get("z", 0)]


def time_key(value):
	number = round(float(value), 5)
	return str(int(number)) if number.is_integer() else f"{number:g}"


def convert_animation(source):
	result = {
		"loop": "hold_on_last_frame" if source.get("loop") in ("hold", False) else source.get("loop"),
		"animation_length": source["length"],
		"bones": {},
	}
	sounds = {}
	particles = {}
	timeline = {}

	for animator in source.get("animators", {}).values():
		animator_type = animator.get("type")
		if animator_type == "bone":
			if animator.get("name") in ("leg_left", "leg_right"):
				continue
			bone = {}
			by_channel = {}
			for keyframe in animator.get("keyframes", []):
				by_channel.setdefault(keyframe["channel"], []).append(keyframe)
			for channel, keyframes in by_channel.items():
				keyframes.sort(key=lambda item: float(item["time"]))
				values = {}
				for keyframe in keyframes:
					key = time_key(keyframe["time"])
					values[key] = point(keyframe)
				# Apply step semantics after the complete value map exists;
				# otherwise the following keyframe overwrites its pre/post form.
				for index, keyframe in enumerate(keyframes):
					if keyframe.get("interpolation") != "step" or index + 1 >= len(keyframes):
						continue
					next_keyframe = keyframes[index + 1]
					next_key = time_key(next_keyframe["time"])
					values[next_key] = {
						"pre": point(keyframe),
						"post": point(next_keyframe),
					}
				bone[channel] = values
			if bone:
				result["bones"][animator["name"]] = bone
			continue

		for keyframe in animator.get("keyframes", []):
			key = time_key(keyframe["time"])
			data = keyframe["data_points"][0]
			if keyframe["channel"] == "sound":
				sounds[key] = {
					"effect": data.get("effect", ""),
					"locator": data.get("locator", ""),
				}
			elif keyframe["channel"] == "particle":
				particles[key] = {
					"effect": data.get("effect", ""),
					"locator": data.get("locator", ""),
				}
			elif keyframe["channel"] == "timeline" and data.get("script"):
				timeline[key] = data["script"]

	if sounds:
		result["sound_effects"] = sounds
	if particles:
		result["particle_effects"] = particles
	if timeline:
		result["timeline"] = timeline
	return result


def main(model_path, animation_path, animation_name):
	model = json.load(open(model_path, encoding="utf-8"))
	target = json.load(open(animation_path, encoding="utf-8"))
	source = next(
		(animation for animation in model["animations"]
		 if animation.get("name") == animation_name),
		None,
	)
	if source is None:
		raise SystemExit(f"Missing source animation: {animation_name}")
	converted = convert_animation(source)
	target["animations"][animation_name] = converted
	with open(animation_path, "w", encoding="utf-8", newline="\n") as handle:
		json.dump(target, handle, ensure_ascii=False, separators=(",", ":"))
	print(
		f"Imported {animation_name}: bones={len(converted['bones'])} "
		f"length={converted['animation_length']}"
	)


if __name__ == "__main__":
	main(sys.argv[1], sys.argv[2], sys.argv[3])
