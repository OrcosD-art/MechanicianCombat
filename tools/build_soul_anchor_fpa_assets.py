"""Build the Soul Anchor first-person GeckoLib geometry and animations."""

import json
import shutil
from pathlib import Path

import rebuild_war_banner_assets as banner
from build_war_banner_fpa_assets import (
    restore_bone_pivots,
)

ROOT = Path(r"D:\codex\mod\rebuild")
SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\盔甲\胸甲"
    r"\soul_anchor\anchor_fist_person_animation.bbmodel"
)
TEXTURE_SOURCE = SOURCE.with_name("灵魂之锚.png")
RESOURCES = (
    ROOT / "mod" / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat"
)
GEO_OUT = RESOURCES / "geo" / "entity" / "soul_anchor_fpa.geo.json"
ANIMATION_OUT = (
    RESOURCES / "animations" / "entity"
    / "soul_anchor_fpa.animation.json"
)
TEXTURE_OUT = RESOURCES / "textures" / "entity" / "soul_anchor_fpa.png"


def main():
    model = json.loads(SOURCE.read_text(encoding="utf-8"))
    exported = banner.export_from_blockbench(
        model, "soul_anchor_fpa", 9357
    )
    geometry = next(
        entry["content"]
        for entry in exported.get("exports", [])
        if isinstance(entry.get("content"), dict)
        and entry["content"].get("bones")
    )
    elements = {element["uuid"]: element for element in model["elements"]}
    banner.restore_source_uvs(geometry, model, elements)
    restored = banner.restore_mirrored_cube_rotations(geometry, model)
    pivots = restore_bone_pivots(geometry, model)
    for bone in geometry["bones"]:
        if bone.get("name") == "锚":
            bone["name"] = "anchor"

    animation_document = json.loads(exported["animation_document"])
    for animation in animation_document.get("animations", {}).values():
        bones = animation.get("bones")
        if isinstance(bones, dict) and "锚" in bones:
            bones["anchor"] = bones.pop("锚")

    document = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": (
                    "geometry.mechaniciancombat.soul_anchor_fpa"
                ),
                "texture_width": 32,
                "texture_height": 32,
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
        json.dumps(animation_document, ensure_ascii=False,
                   separators=(",", ":")),
        encoding="utf-8",
    )
    TEXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(TEXTURE_SOURCE, TEXTURE_OUT)
    print(f"Geo: {GEO_OUT}")
    print(f"Animation: {ANIMATION_OUT}")
    print(f"Texture: {TEXTURE_OUT}")
    print(f"Restored rotated cubes: {restored}")
    print(f"Restored bone pivots: {pivots}")


if __name__ == "__main__":
    main()
