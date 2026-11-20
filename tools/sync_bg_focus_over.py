"""Merge the updated BG261015 Focus_over clip into bowgun_rig."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

ROOT = Path(r"D:\codex\mod\rebuild")
sys.path.insert(0, str(ROOT / "tools"))

from gen_geckolib_mech import SOUND_NAMES
from sync_soul_anchor_animations import convert_animation, time_key

SOURCE_NAME = "BG261015.bbmodel"
TARGET = (
    ROOT / "mod" / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat" / "animations" / "entity"
    / "bowgun_rig.animation.json"
)


def source_path() -> Path:
    root = Path(r"D:\Download\USER\work\AModel")
    matches = list(root.rglob(SOURCE_NAME))
    if len(matches) != 1:
        raise RuntimeError(
            f"expected one {SOURCE_NAME}, found {len(matches)}"
        )
    return matches[0]


def convert_sounds(animation: dict) -> dict:
    sounds: dict[str, dict[str, str]] = {}
    for animator in animation.get("animators", {}).values():
        if animator.get("type") != "effect":
            continue
        for keyframe in animator.get("keyframes", []):
            if keyframe.get("channel") != "sound":
                continue
            for point in keyframe.get("data_points") or []:
                filename = os.path.basename(
                    str(point.get("file") or "").replace("\\", "/")
                )
                effect = SOUND_NAMES.get(filename)
                if not effect:
                    effect = str(point.get("effect") or "").strip()
                if not effect:
                    continue
                value = {"effect": effect}
                locator = str(point.get("locator") or "")
                if locator:
                    value["locator"] = locator
                sounds[time_key(keyframe.get("time", 0))] = value
    return dict(sorted(sounds.items(), key=lambda item: float(item[0])))


def main() -> int:
    source = json.loads(source_path().read_text(encoding="utf-8"))
    animation = next(
        (entry for entry in source["animations"]
         if entry.get("name") == "Focus_over"),
        None,
    )
    if animation is None:
        raise RuntimeError("Focus_over not found in BG261015.bbmodel")

    converted = convert_animation(animation)
    sounds = convert_sounds(animation)
    if sounds:
        converted["sound_effects"] = sounds
    else:
        converted.pop("sound_effects", None)

    rig = json.loads(TARGET.read_text(encoding="utf-8"))
    rig["animations"]["Focus_over"] = converted
    TARGET.write_text(
        json.dumps(rig, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"synced Focus_over -> {TARGET}")
    print(
        f"length={converted['animation_length']} "
        f"loop={converted['loop']} "
        f"bones={','.join(converted['bones'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
