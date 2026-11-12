"""Export the Mechanician hunter spear item, deploy pose, and fixed turret."""

import importlib.util
import json
import shutil
import tempfile
from pathlib import Path


ROOT = Path(r"D:\codex\mod\rebuild")
SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\固定炮台"
)
ASSETS = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "tkgun"
RESOURCES = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "tkgun"
TURRET = SOURCE / "固定狩猎炮台.bbmodel"
DEPLOY = SOURCE / "炮台部署skill.bbmodel"
SPEAR = SOURCE / "机械师猎矛.bbmodel"
TEXTURE = SOURCE / "mch.png"


def importer(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def export_geckolib(module, source, name, port, identifier,
        texture_width, texture_height):
    exported = module.export_from_blockbench(source, name, port)
    geometry = next(
        entry["content"]
        for entry in exported.get("exports", [])
        if isinstance(entry.get("content"), dict)
        and entry["content"].get("bones")
    )
    elements = {
        element["uuid"]: element for element in source["elements"]
    }
    module.restore_source_uvs(geometry, source, elements)
    module.restore_mirrored_cube_rotations(geometry, source)
    document = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": texture_width,
                "texture_height": texture_height,
                "visible_bounds_width": 3.0,
                "visible_bounds_height": 4.0,
                "visible_bounds_offset": [0.0, 1.5, 0.0],
            },
            "bones": geometry["bones"],
        }],
    }
    return document, json.loads(exported["animation_document"])


def normalize_hunter_turret_faces(geometry):
    """Mirror-safe representation for the portable turret's rear top face."""
    gunmode = next(
        bone for bone in geometry["bones"]
        if bone.get("name") == "gunmode"
    )
    rear_cube = gunmode["cubes"][-1]
    rear_cube["uv"]["up"] = {
        "uv": [133, 37],
        "uv_size": [5, -4],
    }


def export_deploy_player_clip(converter_module, source):
    document = json.loads(
        DEPLOY.read_text(encoding="utf-8"))
    for animation in document.get("animations", []):
        if animation.get("name") == "skill_flag":
            animation["name"] = "jump"
            break
    else:
        raise RuntimeError("deployment skill_flag animation is missing")

    temporary = Path(tempfile.gettempdir()) / "tkgun_spear_deploy.bbmodel"
    temporary.write_text(
        json.dumps(document, ensure_ascii=False), encoding="utf-8")
    converter_module.SOURCE = temporary
    converter_module.OUT = (
        Path(tempfile.gettempdir()) / "tkgun_spear_deploy_playeranim"
    )
    converter_module.build()
    converted = json.loads(
        (converter_module.OUT / "backpack_air_poses.json")
        .read_text(encoding="utf-8"))
    animation = converted["animations"]["backpack_air_jump"]
    animation["bones"] = {
        name: animation["bones"][name]
        for name in ("torso", "rightArm", "leftArm")
        if name in animation["bones"]
    }
    for bone in animation["bones"].values():
        for rotation in bone.get("rotation", {}).values():
            rotation[1] = -rotation[1]
    return {
        "animations": {
            "hunter_spear_deploy": animation,
        }
    }


def main():
    exporter = importer(
        "hunter_turret_exporter",
        ROOT / "tools" / "rebuild_war_banner_assets.py")
    turret_model = json.loads(TURRET.read_text(encoding="utf-8"))
    deploy_model = json.loads(DEPLOY.read_text(encoding="utf-8"))

    turret_geo, turret_animation = export_geckolib(
        exporter, turret_model, "hunter_turret", 9354,
        "geometry.tkgun.hunter_turret", 160, 160)
    normalize_hunter_turret_faces(turret_geo)
    deploy_geo, deploy_animation = export_geckolib(
        exporter, deploy_model, "hunter_spear_deploy", 9355,
        "geometry.tkgun.hunter_spear_deploy", 160, 160)

    geo_dir = ASSETS / "geo" / "entity"
    animation_dir = ASSETS / "animations" / "entity"
    texture_dir = ASSETS / "textures" / "entity"
    for directory in (geo_dir, animation_dir, texture_dir):
        directory.mkdir(parents=True, exist_ok=True)

    (geo_dir / "hunter_turret.geo.json").write_text(
        json.dumps(turret_geo, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    (animation_dir / "hunter_turret.animation.json").write_text(
        json.dumps(turret_animation, ensure_ascii=False,
                   separators=(",", ":")),
        encoding="utf-8")
    (geo_dir / "hunter_spear_deploy.geo.json").write_text(
        json.dumps(deploy_geo, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    (animation_dir / "hunter_spear_deploy.animation.json").write_text(
        json.dumps(deploy_animation, ensure_ascii=False,
                   separators=(",", ":")),
        encoding="utf-8")
    shutil.copyfile(TEXTURE, texture_dir / "hunter_turret.png")
    shutil.copyfile(TEXTURE, texture_dir / "hunter_spear_deploy.png")

    converter = importer(
        "hunter_spear_playeranim_converter",
        ROOT / "work" / "jump_playeranim_from_bbmodel.py")
    player_clip = export_deploy_player_clip(converter, deploy_model)
    player_dir = ASSETS / "player_animations"
    player_dir.mkdir(parents=True, exist_ok=True)
    (player_dir / "hunter_spear_deploy.json").write_text(
        json.dumps(player_clip, ensure_ascii=False,
                   separators=(",", ":")),
        encoding="utf-8")

    shutil.copyfile(TEXTURE, ASSETS / "textures" / "item"
                    / "mechanician_hunter_spear.png")
    print("Exported hunter turret assets")


if __name__ == "__main__":
    main()
