"""Rebuild the War Banner geo, animation sheet, and locator metadata."""

import json
import shutil
import subprocess
from pathlib import Path

from PIL import Image


ROOT = Path(r"D:\codex\mod\rebuild")
SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\背部装备"
    r"\combat_backpack\战旗\战旗.bbmodel"
)
FLAG = SOURCE.with_name("flag.png")
FLOW = SOURCE.with_name("flag_flow.png")
HELD_SOURCE = SOURCE.with_name("flag_skill.bbmodel")
HELD_GEO_SOURCE = SOURCE.with_name("flag_skill - Converted.geo.json")
HELD_ANIMATION_SOURCE = SOURCE.with_name("model.animation.json")
RESOURCES = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "tkgun"
GEO_OUT = RESOURCES / "geo" / "entity" / "war_banner.geo.json"
HELD_GEO_OUT = RESOURCES / "geo" / "entity" / "war_banner_held.geo.json"
ANIMATION_OUT = RESOURCES / "animations" / "entity" / "war_banner.animation.json"
HELD_ANIMATION_OUT = (
    RESOURCES / "animations" / "entity" / "war_banner_held.animation.json"
)
TEXTURE_OUT = RESOURCES / "textures" / "entity" / "war_banner.png"
HELD_TEXTURE_OUT = RESOURCES / "textures" / "entity" / "war_banner_held.png"
METADATA_OUT = RESOURCES / "textures" / "entity" / "war_banner.png.mcmeta"
STATIC_UV_OFFSET = 48.0
EXPORT_JS = ROOT / "tools" / "bb_export_builtin.js"
GECKO_EXPORT_JS = ROOT / "tools" / "bb_export_geckolib.js"
RUN_JS = ROOT / "tools" / "run_js.ps1"
BLOCKBENCH = ROOT / "tools" / "Blockbench5-portable" / "Blockbench.exe"
PROFILE = ROOT / "work" / "bbprofile_war_banner_current"
GECKO_PROFILE = ROOT / "bbprofile_bb522"


def find_group(nodes, name):
    for node in nodes or []:
        if not isinstance(node, dict):
            continue
        if node.get("name") == name:
            return node
        found = find_group(node.get("children"), name)
        if found:
            return found
    return None


def export_from_blockbench(source, name, port, exporter=EXPORT_JS,
		profile=PROFILE):
    job = ROOT / "work" / f"{name}_job.js"
    job.parent.mkdir(parents=True, exist_ok=True)
    job.write_text(
        "globalThis.__BB_JOB = "
        + json.dumps(
            {"name": name, "modelJson": source, "skinUv": {}},
            ensure_ascii=False,
        )
        + ";\nreturn 1;",
        encoding="utf-8",
    )
    command = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(RUN_JS),
        "-Exe",
        str(BLOCKBENCH),
        "-Profile",
        str(profile),
        "-Port",
        str(port),
        "-Js",
        f"{job},{exporter}",
    ]
    result = subprocess.run(
        command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8"
    )
    if result.returncode:
        raise RuntimeError(
            "Blockbench export failed:\n" + result.stdout + result.stderr
        )
    for line in reversed(result.stdout.splitlines()):
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            return json.loads(line.split("--- exit=", 1)[0])
        except json.JSONDecodeError:
            continue
    raise RuntimeError("Blockbench export JSON was not found:\n" + result.stdout)


def restore_source_uvs(geometry, source_model, elements):
    uv_count = 0
    for bone in geometry["bones"]:
        source_group = find_group(source_model["outliner"], bone["name"])
        if not source_group:
            continue
        source_cubes = []
        for child in source_group.get("children", []):
            if isinstance(child, str):
                element = elements.get(child)
                if element and element.get("type", "cube") == "cube":
                    source_cubes.append(element)
            elif isinstance(child, dict) and child.get("type") == "cube":
                source_cubes.append(child)
        exported_cubes = bone.get("cubes", [])
        if not exported_cubes:
            continue
        if len(source_cubes) != len(exported_cubes):
            raise RuntimeError(
                f"Cube count mismatch for {bone['name']}: "
                f"{len(source_cubes)} != {len(exported_cubes)}"
            )
        for source_cube, exported_cube in zip(source_cubes, exported_cubes):
            uv = {}
            for face_name, face in source_cube.get("faces", {}).items():
                if face.get("uv") is None or face.get("texture") is None:
                    continue
                x1, y1, x2, y2 = map(float, face["uv"])
                entry = {
                    "uv": [x1, y1],
                    "uv_size": [x2 - x1, y2 - y1],
                }
                if face.get("rotation"):
                    entry["uv_rotation"] = face["rotation"]
                uv[face_name] = entry
                uv_count += 1
            if uv:
                exported_cube["uv"] = uv
    return uv_count


def restore_mirrored_cube_rotations(geometry, source_model, bone_name):
    source_group = find_group(source_model["outliner"], bone_name)
    elements = {
        element["uuid"]: element for element in source_model["elements"]
    }
    source_cubes = []
    for child in source_group.get("children", []):
        if isinstance(child, str):
            element = elements.get(child)
            if element and element.get("type", "cube") == "cube":
                source_cubes.append(element)
        elif isinstance(child, dict) and child.get("type") == "cube":
            source_cubes.append(child)
    bone = next(
        bone for bone in geometry["bones"] if bone.get("name") == bone_name)
    exported_cubes = bone.get("cubes", [])
    if len(source_cubes) != len(exported_cubes):
        raise RuntimeError(
            f"Cube count mismatch for {bone_name}: "
            f"{len(source_cubes)} != {len(exported_cubes)}")
    restored = 0
    for source_cube, exported_cube in zip(source_cubes, exported_cubes):
        rotation = source_cube.get("rotation")
        if not rotation:
            continue
        source_pivot = source_cube.get("origin")
        if not source_pivot:
            continue
        # Blockbench's free-model axis is mirrored into Bedrock X. Reflections
        # keep X rotation and reverse Y/Z rotation, while the cube pivot follows
        # the same X mirror as its vertices.
        exported_cube["rotation"] = [
            float(rotation[0]),
            -float(rotation[1]),
            -float(rotation[2]),
        ]
        exported_cube["pivot"] = [
            -float(source_pivot[0]),
            float(source_pivot[1]),
            float(source_pivot[2]),
        ]
        restored += 1
    return restored


def mirror_held_animation(animation_document):
    document = json.loads(animation_document)
    bones = document["animations"]["skill_flag"]["bones"]
    for channels in bones.values():
        for rotation in channels.get("rotation", {}).values():
            if not isinstance(rotation, list):
                continue
            rotation[1] = -rotation[1]
            rotation[2] = -rotation[2]
        for position in channels.get("position", {}).values():
            if not isinstance(position, list):
                continue
            position[0] = -position[0]
    return json.dumps(document, ensure_ascii=False, separators=(",", ":"))


def dynamic_uv_rectangles(source):
    rectangles = []
    for element in source.get("elements", []):
        if element.get("type", "cube") != "cube":
            continue
        for face in element.get("faces", {}).values():
            if face.get("texture") != 1 or face.get("uv") is None:
                continue
            x1, y1, x2, y2 = map(float, face["uv"])
            rectangles.append(
                (
                    int(min(x1, x2)),
                    int(min(y1, y2)),
                    int(max(x1, x2)),
                    int(max(y1, y2)),
                )
            )
    if not rectangles:
        raise RuntimeError("No flag_flow UV rectangles were found")
    return rectangles


def separate_static_texture_uvs(geometry):
    dynamic_bones = {"f1", "f2", "f3", "f4", "f5"}
    moved = 0
    for bone in geometry["bones"]:
        if bone.get("name") in dynamic_bones:
            continue
        for cube in bone.get("cubes", []):
            for face in cube.get("uv", {}).values():
                face["uv"][0] += STATIC_UV_OFFSET
                moved += 1
    return moved


def align_held_flag_to_vanilla_right_arm(geometry):
    """Move the hidden source arm and its flag child onto vanilla's right arm."""
    for bone in geometry["bones"]:
        if bone.get("name") not in {"arm_right", "flag", "locator"}:
            continue
        bone["pivot"][0] -= 1.0
        for cube in bone.get("cubes", []):
            cube["origin"][0] -= 1.0
            if "pivot" in cube:
                cube["pivot"][0] -= 1.0
        for locator in bone.get("locators", {}).values():
            locator[0] -= 1.0


def build_texture(source):
    base = Image.open(FLAG).convert("RGBA")
    flow = Image.open(FLOW).convert("RGBA")
    frame_size = base.width
    if base.size != (frame_size, frame_size):
        raise RuntimeError("flag.png must be square")
    if flow.width != frame_size or flow.height % frame_size:
        raise RuntimeError("flag_flow.png does not match the base frame size")

    rectangles = dynamic_uv_rectangles(source)
    frame_count = flow.height // frame_size
    atlas_width = frame_size * 2
    frame_height = frame_size * 2
    output = Image.new(
        "RGBA", (atlas_width, frame_height * frame_count))
    for frame_index in range(frame_count):
        frame = Image.new("RGBA", (atlas_width, frame_height))
        for x1, y1, x2, y2 in rectangles:
            frame.paste((0, 0, 0, 0), (x1, y1, x2, y2))
        flow_frame = flow.crop(
            (0, frame_index * frame_size, frame_size,
             (frame_index + 1) * frame_size)
        )
        frame.alpha_composite(flow_frame)
        frame.alpha_composite(base, (frame_size, 0))
        output.alpha_composite(
            frame, (0, frame_index * frame_height))

    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    output.save(TEXTURE_OUT)
    # Integer-tick textures cannot represent exactly 9 FPS. Uniform 2-tick
    # frames run at 10 FPS without the visible pauses caused by mixed 2/3 ticks.
    frame_times = [2] * frame_count
    METADATA_OUT.write_text(
        json.dumps(
            {
                "animation": {
                    "frametime": 1,
                    "interpolate": False,
                    "frames": [
                        {"index": index, "time": frame_times[index]}
                        for index in range(frame_count)
                    ],
                }
            },
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )
    return frame_count, rectangles


SOURCE_MODEL = json.loads(SOURCE.read_text(encoding="utf-8"))
HELD_MODEL = json.loads(HELD_SOURCE.read_text(encoding="utf-8"))


def main():
    elements = {element["uuid"]: element for element in SOURCE_MODEL["elements"]}
    export = export_from_blockbench(
        SOURCE_MODEL, "war_banner", 9347)
    gecko_export = export_from_blockbench(
        SOURCE_MODEL, "war_banner", 9346, GECKO_EXPORT_JS,
        GECKO_PROFILE)
    gecko_content = next(
        entry["content"]
        for entry in gecko_export.get("exports", [])
        if entry.get("name") == "war_banner.geo"
        and isinstance(entry.get("content"), str)
    )
    gecko_document = json.loads(gecko_content)
    geometry = gecko_document["minecraft:geometry"][0]
    geometry["description"].update({
        "identifier": "geometry.tkgun.war_banner",
        "texture_width": int(STATIC_UV_OFFSET * 2),
        "texture_height": int(STATIC_UV_OFFSET * 2),
        "visible_bounds_width": 3,
        "visible_bounds_height": 4.0,
        "visible_bounds_offset": [0, 2.0, 0],
    })
    moved_static_faces = separate_static_texture_uvs(geometry)
    uv_count = sum(
        len(cube.get("uv", {}))
        for bone in geometry["bones"]
        for cube in bone.get("cubes", [])
    )
    GEO_OUT.parent.mkdir(parents=True, exist_ok=True)
    GEO_OUT.write_text(
        json.dumps(gecko_document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    ANIMATION_OUT.parent.mkdir(parents=True, exist_ok=True)
    ANIMATION_OUT.write_text(
        export["animation_document"], encoding="utf-8")

    frame_count, rectangles = build_texture(SOURCE_MODEL)

    HELD_GEO_OUT.parent.mkdir(parents=True, exist_ok=True)
    if HELD_GEO_SOURCE.exists():
        shutil.copyfile(HELD_GEO_SOURCE, HELD_GEO_OUT)
    HELD_ANIMATION_OUT.parent.mkdir(parents=True, exist_ok=True)
    if HELD_ANIMATION_SOURCE.exists():
        shutil.copyfile(HELD_ANIMATION_SOURCE, HELD_ANIMATION_OUT)
    HELD_TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(FLAG, HELD_TEXTURE_OUT)

    p2 = find_group(SOURCE_MODEL["outliner"], "P2")
    locator_y = float(p2["origin"][1]) / 16.0
    print(f"UV faces: {uv_count}")
    print(f"Static UV faces moved to right atlas: {moved_static_faces}")
    held_document = json.loads(
        HELD_GEO_OUT.read_text(encoding="utf-8"))
    held_flag = next(
        bone for bone in held_document["minecraft:geometry"][0]["bones"]
        if bone.get("name") == "flag")
    print(f"Held flag cubes: {len(held_flag.get('cubes', []))}")
    print(f"Frames: {frame_count}, dynamic UV rectangles: {rectangles}")
    print(f"Entity locator Y: {locator_y:.6f} blocks")
    print(f"Geo: {GEO_OUT}")
    print(f"Animation: {ANIMATION_OUT}")
    print(f"Texture: {TEXTURE_OUT}")
    print(f"Held geo: {HELD_GEO_OUT}")
    print(f"Held animation: {HELD_ANIMATION_OUT}")


if __name__ == "__main__":
    main()
