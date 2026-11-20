"""Build the War Banner first-person GeckoLib geometry and animation."""

import json
import shutil
from pathlib import Path

import rebuild_war_banner_assets as banner

ROOT = Path(r"D:\codex\mod\rebuild")
SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\背部装备"
    r"\combat_backpack\战旗\flag_fist_person_animation.bbmodel"
)
FLAG_TEXTURE = SOURCE.with_name("flag.png")
RESOURCES = (
    ROOT / "mod" / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat"
)
GEO_OUT = RESOURCES / "geo" / "entity" / "war_banner_fpa.geo.json"
ANIMATION_OUT = (
    RESOURCES / "animations" / "entity"
    / "war_banner_fpa.animation.json"
)
TEXTURE_OUT = RESOURCES / "textures" / "entity" / "war_banner_fpa.png"


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


def restore_bone_pivots(geometry, model):
    restored = 0
    for bone in geometry["bones"]:
        group = find_group(model.get("outliner"), bone.get("name"))
        origin = group.get("origin") if group else None
        if not origin:
            continue
        # Bedrock/GeckoLib mirrors model X while retaining source Y/Z.
        bone["pivot"] = [
            -float(origin[0]),
            float(origin[1]),
            float(origin[2]),
        ]
        restored += 1
    return restored


def main():
    model = json.loads(SOURCE.read_text(encoding="utf-8"))
    exported = banner.export_from_blockbench(model, "war_banner_fpa", 9356)
    geometry = next(
        entry["content"]
        for entry in exported.get("exports", [])
        if isinstance(entry.get("content"), dict)
        and entry["content"].get("bones")
    )
    elements = {element["uuid"]: element for element in model["elements"]}
    banner.restore_source_uvs(geometry, model, elements)
    restored = banner.restore_mirrored_cube_rotations(geometry, model)
    restored_pivots = restore_bone_pivots(geometry, model)

    document = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.mechaniciancombat.war_banner_fpa",
                "texture_width": 48,
                "texture_height": 48,
                "visible_bounds_width": 2.0,
                "visible_bounds_height": 3.0,
                "visible_bounds_offset": [0, 1.0, 0],
            },
            "bones": geometry["bones"],
        }],
    }

    GEO_OUT.parent.mkdir(parents=True, exist_ok=True)
    GEO_OUT.write_text(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    ANIMATION_OUT.parent.mkdir(parents=True, exist_ok=True)
    ANIMATION_OUT.write_text(
        exported["animation_document"], encoding="utf-8"
    )
    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(FLAG_TEXTURE, TEXTURE_OUT)
    print(f"Geo: {GEO_OUT}")
    print(f"Animation: {ANIMATION_OUT}")
    print(f"Texture: {TEXTURE_OUT}")
    print(f"Restored rotated cubes: {restored}")
    print(f"Restored bone pivots: {restored_pivots}")


if __name__ == "__main__":
    main()
