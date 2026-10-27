"""Merge duplicate Fortress Destroyer focus-shot clips while preserving chain motion.

The normal focus shots 4..8 use identical body/gun animation tracks; only the
ammo-chain bone offsets differ. Keep one canonical clip and let the renderer
apply the per-shot offsets. Also remove derived upper-body focus clips that the
current state machine never selects.
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import shutil
import uuid


REMOVED_SOURCE_CLIPS = {
    "Focus_shooting",
    "Focus_shooting2",
    "Focus_shooting3",
    "Focus_shooting5",
}

REMOVED_UNREACHABLE_CLIPS = {
    "Focus_ready_upper",
    "Focus_over_shooting_upper",
    "Focus_shooting_upper",
    "Focus_shooting2_upper",
    "Focus_shooting3_upper",
    "Focus_shooting4_upper",
    "Focus_shooting5_upper",
    "Focus_shooting6_upper",
    "Focus_shooting7_upper",
    "Focus_ready_hold",
}


def read_json(path: str):
    with open(path, encoding="utf-8") as source:
        return json.load(source)


def write_json(path: str, document) -> None:
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8", newline="\n") as output:
        json.dump(document, output, ensure_ascii=False, separators=(",", ":"))
    os.replace(temporary, path)


def new_uuids(value):
    if isinstance(value, dict):
        result = {}
        for key, child in value.items():
            if key == "uuid":
                result[key] = str(uuid.uuid4())
            else:
                result[key] = new_uuids(child)
        return result
    if isinstance(value, list):
        return [new_uuids(child) for child in value]
    return value


def merge_geckolib(gecko: dict) -> dict:
    animations = gecko.get("animations") or {}
    required = ("Focus_shooting_extra", "Focus_shooting1",
                "Focus_shooting4", "Focus_shooting6", "Focus_shooting7")
    if not all(name in animations for name in required):
        raise ValueError("Expected focus-shot animations are missing.")

    for name in REMOVED_SOURCE_CLIPS | REMOVED_UNREACHABLE_CLIPS:
        animations.pop(name, None)
    return gecko


def find_animation(document: dict, name: str):
    return next((animation for animation in document.get("animations", [])
                 if animation.get("name") == name), None)


def find_animator(animation: dict, bone: str):
    return next((value for value in (animation.get("animators") or {}).values()
                 if value.get("name") == bone), None)


def add_animator(animation: dict, source_animation: dict, bone: str) -> None:
    animators = animation.setdefault("animators", {})
    for key, value in list(animators.items()):
        if value.get("name") == bone:
            del animators[key]
    source = find_animator(source_animation, bone)
    if source is None:
        raise ValueError(f"{source_animation.get('name')} has no {bone} animator.")
    animators[str(uuid.uuid4())] = new_uuids(copy.deepcopy(source))


def merge_bbmodel(document: dict) -> dict:
    required = ("Focus_shooting_extra", "Focus_shooting1",
                "Focus_shooting4", "Focus_shooting6", "Focus_shooting7")
    if not all(find_animation(document, name) for name in required):
        raise ValueError("Expected Blockbench focus-shot animations are missing.")
    document["animations"] = [
        animation for animation in document.get("animations", [])
        if animation.get("name") not in REMOVED_SOURCE_CLIPS
    ]
    return document


def backup(path: str, backup_dir: str) -> None:
    os.makedirs(backup_dir, exist_ok=True)
    destination = os.path.join(backup_dir, os.path.basename(path))
    shutil.copy2(path, destination)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gecko", required=True)
    parser.add_argument("--bbmodel", required=True)
    parser.add_argument("--backup-dir", required=True)
    args = parser.parse_args()

    backup(args.gecko, args.backup_dir)
    backup(args.bbmodel, args.backup_dir)

    gecko = merge_geckolib(read_json(args.gecko))
    bbmodel = merge_bbmodel(read_json(args.bbmodel))
    write_json(args.gecko, gecko)
    write_json(args.bbmodel, bbmodel)

    gecko_names = set((gecko.get("animations") or {}).keys())
    print("GeckoLib animations:", len(gecko_names))
    print("Removed runtime duplicates:",
          ", ".join(sorted(REMOVED_SOURCE_CLIPS | REMOVED_UNREACHABLE_CLIPS)))
    print("Blockbench animations:", len(bbmodel.get("animations", [])))
    print("Canonical clips: Focus_shooting_extra (1-3), "
          "Focus_shooting (4..max-2), Focus_shooting6, Focus_shooting7")


if __name__ == "__main__":
    main()
