"""Export and apply the authored gun-held War Banner skill animation."""

import importlib.util
import json
from pathlib import Path


ROOT = Path(r"D:\codex\mod\rebuild")
ASSETS = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "tkgun"
HELD_ANIMATION = ASSETS / "animations" / "entity" / "war_banner_held.animation.json"
RIG_ANIMATIONS = ASSETS / "animations" / "entity"
SOURCE_MODEL = Path(
    r"D:\Download\USER\work\AModel\机械师装备\背部装备"
    r"\combat_backpack\战旗\flag_skill.bbmodel"
)


def main():
    spec = importlib.util.spec_from_file_location(
        "war_banner_asset_exporter",
        ROOT / "tools" / "rebuild_war_banner_assets.py",
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    exported = module.export_from_blockbench(
        module.HELD_MODEL, "war_banner_skill_gun", 9351)
    document = json.loads(exported["animation_document"])
    animations = document["animations"]
    if "skill_flag_gun" not in animations:
        raise RuntimeError("skill_flag_gun was not exported")

    # Keep both the original and gun-held flag poses in one runtime resource.
    HELD_ANIMATION.write_text(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")

    rig_outputs = []
    for path in sorted(RIG_ANIMATIONS.glob("*_rig.animation.json")):
        rig = json.loads(path.read_text(encoding="utf-8"))
        rig["animations"]["skill_flag_gun"] = animations["skill_flag_gun"]
        path.write_text(
            json.dumps(rig, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8")
        rig_outputs.append(path)

    gun_bones = animations["skill_flag_gun"].get("bones", {})
    print("exported:", ", ".join(sorted(animations)))
    print("gun bones:", ", ".join(sorted(gun_bones)))
    print(HELD_ANIMATION)
    for path in rig_outputs:
        print(path)


if __name__ == "__main__":
    main()
