"""Convert the authored combat-backpack bbmodel into GeckoLib assets.

The initial combat_backpack.bbmodel is the source of truth for the wearable
model and its CD/ready/aim animations. Palette variants only differ by texture.
"""

import argparse
import json
from pathlib import Path


def find_group(nodes, name):
    for node in nodes:
        if not isinstance(node, dict):
            continue
        if node.get("name") == name:
            return node
        found = find_group(node.get("children", []), name)
        if found is not None:
            return found
    return None


def number(value):
    value = float(value)
    return int(value) if value.is_integer() else value


def point(values):
    return [number(value) for value in values]


def converted_point(values, wearable):
    x, y, z = (float(value) for value in values)
    if wearable:
        return [number(-x - 4.0), number(y - 24.0), number(z - 2.0)]
    return [number(-x), number(y - 12.0), number(z)]


def converted_cube_origin(element, wearable):
    source_from = [float(value) for value in element["from"]]
    source_to = [float(value) for value in element["to"]]
    if wearable:
        return [
            number(-source_to[0] - 4.0),
            number(source_from[1] - 24.0),
            number(source_from[2] - 2.0),
        ]
    return [
        number(-source_to[0]),
        number(source_from[1] - 12.0),
        number(source_from[2]),
    ]


def converted_rotation(values):
    if not values:
        return None
    return [number(-float(values[0])), number(values[1]), number(values[2])]


def convert_cube(element, wearable):
    source_from = [float(value) for value in element["from"]]
    source_to = [float(value) for value in element["to"]]
    uv = {}
    for face_name, source_face in element.get("faces", {}).items():
        if source_face.get("texture") is None:
            continue
        x1, y1, x2, y2 = (float(value) for value in source_face["uv"])
        face = {
            "uv": [number(x1), number(y1)],
            "uv_size": [number(x2 - x1), number(y2 - y1)],
        }
        if source_face.get("rotation"):
            face["uv_rotation"] = int(source_face["rotation"])
        uv[face_name] = face
    cube = {
        "origin": converted_cube_origin(element, wearable),
        "size": [number(abs(source_to[i] - source_from[i])) for i in range(3)],
    }
    if uv:
        cube["uv"] = uv
    rotation = converted_rotation(element.get("rotation"))
    if rotation and any(rotation):
        cube["rotation"] = rotation
    return cube


def convert_geometry(source, wearable):
    elements = {element["uuid"]: element for element in source.get("elements", [])}
    backpack = find_group(source.get("outliner", []), "combat_backpack")
    if backpack is None:
        raise SystemExit("combat_backpack group missing")

    bones = []

    def add_group(group, parent=None):
        name = group["name"]
        if name == "sound":
            return
        bone = {}
        if parent is not None:
            bone["parent"] = parent
        if name == "combat_backpack":
            bone["pivot"] = [0, 0, 0] if wearable else [0, 0, 4]
        else:
            bone["pivot"] = converted_point(group["origin"], wearable)
        rotation = converted_rotation(group.get("rotation"))
        if rotation and any(rotation):
            bone["rotation"] = rotation
        cubes = []
        for child in group.get("children", []):
            if isinstance(child, str):
                element = elements.get(child)
                if element is not None and element.get("type") == "cube":
                    cubes.append(convert_cube(element, wearable))
                continue
            if child.get("type") == "locator":
                if child.get("name") in {"front", "rear"}:
                    locator = {
                        "name": child["name"],
                        "parent": name,
                        "pivot": converted_point(child["position"], wearable),
                    }
                    rotation = converted_rotation(child.get("rotation"))
                    if rotation and any(rotation):
                        locator["rotation"] = rotation
                    bones.append(locator)
                continue
            add_group(child, name)
        if cubes:
            bone["cubes"] = cubes
        bones.insert(0, bone)

    add_group(backpack)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.unknown",
                "texture_width": 48,
                "texture_height": 48,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": bones,
        }],
    }


def animation_time(value):
    return str(number(round(float(value), 4)))


def convert_animations(source, bone_names):
    animations = {}
    for animation in source.get("animations", []):
        bones = {}
        sound_effects = {}
        for animator in animation.get("animators", {}).values():
            if animator.get("type") != "bone":
                continue
            bone_name = animator.get("name")
            if bone_name not in bone_names:
                continue
            channels = {}
            for keyframe in animator.get("keyframes", []):
                channel = keyframe.get("channel")
                if channel not in {"rotation", "position", "scale"}:
                    continue
                data = keyframe.get("data_points", [])
                if not data:
                    continue
                first = data[0]
                value = point([first.get("x", 0), first.get("y", 0), first.get("z", 0)])
                channels.setdefault(channel, {})[animation_time(keyframe["time"])] = {
                    "vector": value,
                }
            if channels:
                bones[bone_name] = channels
        effects_animator = next(
            (animator for animator in animation.get("animators", {}).values()
             if animator.get("type") == "effect"),
            None,
        )
        if effects_animator is not None:
            for keyframe in effects_animator.get("keyframes", []):
                if keyframe.get("channel") != "sound":
                    continue
                for data in keyframe.get("data_points", []):
                    effect = data.get("effect")
                    if effect:
                        sound_effects[animation_time(keyframe["time"])] = {
                            "effect": effect,
                        }
        result = {
            "loop": "hold_on_last_frame" if animation.get("loop") == "hold" else "loop",
            "animation_length": number(animation.get("length", 0)),
        }
        if bones:
            result["bones"] = bones
        if sound_effects:
            result["sound_effects"] = sound_effects
        animations[animation["name"]] = result
    return {
        "format_version": "1.8.0",
        "animations": animations,
    }


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True)
    parser.add_argument("--wearable-out", required=True)
    parser.add_argument("--rig-out", required=True)
    parser.add_argument("--animation-out", required=True)
    args = parser.parse_args()

    source_path = Path(args.source)
    source = json.loads(source_path.read_text(encoding="utf-8"))
    wearable = convert_geometry(source, True)
    rig = convert_geometry(source, False)
    bone_names = {bone["name"] for bone in wearable["minecraft:geometry"][0]["bones"]}
    animation = convert_animations(source, bone_names)

    write_json(Path(args.wearable_out), wearable)
    write_json(Path(args.rig_out), rig)
    write_json(Path(args.animation_out), animation)


if __name__ == "__main__":
    main()
