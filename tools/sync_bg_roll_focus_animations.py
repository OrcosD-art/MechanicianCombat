"""Merge updated BG roll/focus clips and add the focused roll-to-run clip."""

from __future__ import annotations

import copy
import json
import os
import sys
from pathlib import Path

ROOT = Path(r"D:\codex\mod\rebuild")
sys.path.insert(0, str(ROOT / "tools"))

from gen_geckolib_mech import bake_step_keyframes, SOUND_NAMES
from sync_soul_anchor_animations import time_key

SOURCE_NAME = "BG261015.bbmodel"
RIG_DIR = (
    ROOT / "mod" / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat" / "animations" / "entity"
)
UPDATED_CLIPS = (
    "Focus_ready",
    "sliding_fast_ready",
    "rolling_fast_ready",
    "roll",
    "focus-roll-run",
)
PLAYER_BONES = {
    "body", "head", "arm_left", "arm_right",
    "leg_left", "leg_right", "chest",
}


def source_path() -> Path:
    matches = list(Path(r"D:\Download\USER\work\AModel").rglob(SOURCE_NAME))
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


def number(value: object) -> object:
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if not text:
        return 0.0
    try:
        return float(text)
    except ValueError:
        return text


def convert_clip(animation: dict) -> dict:
    bones: dict[str, dict[str, dict[str, list[object]]]] = {}
    for animator in animation.get("animators", {}).values():
        if animator.get("type") != "bone":
            continue
        bone_name = animator.get("name")
        if not bone_name:
            continue
        channels: dict[str, dict[str, list[object]]] = {}
        for keyframe in animator.get("keyframes", []):
            channel = keyframe.get("channel")
            points = keyframe.get("data_points") or []
            if channel not in {"rotation", "position", "scale"} or not points:
                continue
            point = points[0]
            channels.setdefault(channel, {})[
                time_key(keyframe.get("time", 0))
            ] = [
                number(point.get("x", 0)),
                number(point.get("y", 0)),
                number(point.get("z", 0)),
            ]
        if channels:
            bones[bone_name] = {
                channel: dict(sorted(
                    values.items(), key=lambda item: float(item[0])
                ))
                for channel, values in channels.items()
            }
    return {
        "loop": "hold_on_last_frame"
        if str(animation.get("loop", "hold")).lower() != "loop"
        else "loop",
        "animation_length": float(animation.get("length", 0)),
        "bones": bones,
    }


def first_sample(channels: dict) -> dict:
    sampled: dict = {}
    for channel, values in channels.items():
        if not isinstance(values, dict) or not values or "vector" in values:
            continue
        first = min(values.items(), key=lambda item: float(item[0]))
        sampled[channel] = {first[0]: copy.deepcopy(first[1])}
    return sampled


def replace_player_channels(target: dict, source: dict) -> dict:
    target_bones = target.setdefault("bones", {})
    for bone in PLAYER_BONES:
        if bone in source.get("bones", {}):
            target_bones[bone] = copy.deepcopy(source["bones"][bone])
    return target


def focused_roll_for_other_gun(target: dict, source: dict) -> dict:
    # The gun subtree is specific to each weapon. Reuse its own roll timeline,
    # but replace the player channels and start from its focused hold pose.
    result = copy.deepcopy(target)
    result["loop"] = source["loop"]
    result["animation_length"] = source["animation_length"]
    replace_player_channels(result, source)
    focus_hold = target.get("__focus_hold", {})
    for bone, channels in (result.get("bones") or {}).items():
        if bone in PLAYER_BONES or bone not in focus_hold:
            continue
        first = first_sample(focus_hold[bone])
        if first:
            channels.update(copy.deepcopy(first))
    return result


def main() -> int:
    model_doc = json.loads(source_path().read_text(encoding="utf-8"))
    source_animations = {
        animation["name"]: animation
        for animation in model_doc.get("animations", [])
    }
    selected_clips = tuple(sys.argv[1:]) or UPDATED_CLIPS
    unknown = [name for name in selected_clips if name not in UPDATED_CLIPS]
    if unknown:
        raise RuntimeError(
            f"unsupported clip selection: {', '.join(unknown)}"
        )
    missing = [name for name in selected_clips if name not in source_animations]
    if missing:
        raise RuntimeError(f"missing source clips: {', '.join(missing)}")

    converted: dict[str, dict] = {}
    for name in selected_clips:
        source = source_animations[name]
        clip = convert_clip(source)
        sounds = convert_sounds(source)
        if sounds:
            clip["sound_effects"] = sounds
        else:
            clip.pop("sound_effects", None)
        converted[name] = clip

    # Preserve Blockbench's authored step interpolation for muzzle/effect planes.
    bake_step_keyframes({"animations": converted}, model_doc)

    targets = (
        RIG_DIR / "bowgun_rig.animation.json",
        RIG_DIR / "abyssal_ancient_cannon_rig.animation.json",
        RIG_DIR / "veritas_et_justitia_rig.animation.json",
    )
    for target_path in targets:
        rig = json.loads(target_path.read_text(encoding="utf-8"))
        animations = rig.setdefault("animations", {})
        is_base = target_path.name == "bowgun_rig.animation.json"
        for name in selected_clips:
            if is_base:
                animations[name] = copy.deepcopy(converted[name])
                continue
            if name == "focus-roll-run":
                old_roll = copy.deepcopy(animations["roll"])
                focus_hold = copy.deepcopy(
                    (animations.get("Focus_hold") or {}).get("bones") or {}
                )
                old_roll["__focus_hold"] = focus_hold
                merged = focused_roll_for_other_gun(
                    old_roll, converted[name])
                merged.pop("__focus_hold", None)
                animations[name] = merged
                continue
            old = copy.deepcopy(animations[name])
            old["loop"] = converted[name]["loop"]
            old["animation_length"] = converted[name]["animation_length"]
            replace_player_channels(old, converted[name])
            if "sound_effects" in converted[name]:
                old["sound_effects"] = copy.deepcopy(
                    converted[name]["sound_effects"])
            else:
                old.pop("sound_effects", None)
            animations[name] = old

        target_path.write_text(
            json.dumps(rig, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        print(f"updated {target_path}")

    for name in selected_clips:
        clip = converted[name]
        print(
            f"{name}: length={clip['animation_length']} "
            f"loop={clip['loop']} bones={','.join(clip['bones'])}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
