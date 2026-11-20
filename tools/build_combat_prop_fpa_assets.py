"""Build hunter anchor and backpack turret first-person GeckoLib assets."""

import base64
import importlib.util
import json
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(r"D:\codex\mod")
REBUILD = ROOT / "rebuild"
RESOURCE_ROOTS = (
    REBUILD / "mod" / "src" / "main" / "resources" / "assets"
    / "mechaniciancombat",
    ROOT / "neoforge-port" / "src" / "main" / "resources" / "assets"
    / "mechaniciancombat",
)
SOURCE_ROOT = Path(r"D:\Download\USER\work\AModel\机械师装备")
ASSETS = (
    (
        SOURCE_ROOT / "固定炮台" / "狩猎炮台_fist_person_animation.bbmodel",
        "hunter_anchor_fpa",
        "geometry.mechaniciancombat.hunter_anchor_fpa",
        0,
        9360,
    ),
    (
        SOURCE_ROOT / "背部装备" / "combat_backpack" / "炮台"
        / "便携炮台_fist_person_animation.bbmodel",
        "portable_turret_fpa",
        "geometry.mechaniciancombat.portable_turret_fpa",
        1,
        9361,
    ),
    (
        SOURCE_ROOT / "背部装备" / "combat_backpack" / "自动炮台"
        / "自动炮台_fist_person_animation.bbmodel",
        "auto_turret_fpa",
        "geometry.mechaniciancombat.auto_turret_fpa",
        1,
        9362,
    ),
)


def load_exporter():
    path = REBUILD / "tools" / "rebuild_war_banner_assets.py"
    spec = importlib.util.spec_from_file_location("fpa_exporter", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_pivots():
    path = REBUILD / "tools" / "build_war_banner_fpa_assets.py"
    spec = importlib.util.spec_from_file_location("fpa_pivots", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.restore_bone_pivots


def extract_texture(model, index):
    record = model["textures"][index]
    source = record["source"]
    if source.startswith("data:"):
        return base64.b64decode(source.split(",", 1)[1])
    return Path(source).read_bytes()


def build_asset(exporter, restore_bone_pivots, source_path, name,
                identifier, texture_index, port):
    model = json.loads(source_path.read_text(encoding="utf-8"))
    exported = exporter.export_from_blockbench(model, name, port)
    geometry = next(
        entry["content"]
        for entry in exported.get("exports", [])
        if isinstance(entry.get("content"), dict)
        and entry["content"].get("bones")
    )
    elements = {
        element["uuid"]: element for element in model.get("elements", [])
    }
    exporter.restore_source_uvs(geometry, model, elements)
    restored_rotations = exporter.restore_mirrored_cube_rotations(
        geometry, model
    )
    restored_pivots = restore_bone_pivots(geometry, model)

    texture_bytes = extract_texture(model, texture_index)
    image = Image.open(__import__("io").BytesIO(texture_bytes))
    document = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": image.width,
                "texture_height": image.height,
                "visible_bounds_width": 3.0,
                "visible_bounds_height": 4.0,
                "visible_bounds_offset": [0.0, 1.5, 0.0],
            },
            "bones": geometry["bones"],
        }],
    }
    animation = json.loads(exported["animation_document"])

    for resources in RESOURCE_ROOTS:
        geo_dir = resources / "geo" / "entity"
        animation_dir = resources / "animations" / "entity"
        texture_dir = resources / "textures" / "entity"
        for directory in (geo_dir, animation_dir, texture_dir):
            directory.mkdir(parents=True, exist_ok=True)
        (geo_dir / f"{name}.geo.json").write_text(
            json.dumps(document, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        (animation_dir / f"{name}.animation.json").write_text(
            json.dumps(animation, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        (texture_dir / f"{name}.png").write_bytes(texture_bytes)

    print(
        f"{name}: {image.width}x{image.height}, "
        f"rotations={restored_rotations}, pivots={restored_pivots}"
    )


def main():
    exporter = load_exporter()
    restore_bone_pivots = load_pivots()
    for values in ASSETS:
        build_asset(exporter, restore_bone_pivots, *values)


if __name__ == "__main__":
    main()
