"""Convert the edited chain_skill.bbmodel animation source to GeckoLib JSON."""

from __future__ import annotations

import copy
import json
from pathlib import Path

ROOT = Path(r"D:\codex\mod\rebuild")
SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\盔甲\胸甲"
    r"\soul_anchor\chain_skill.bbmodel"
)
BUILD_OUTPUT = ROOT / "build" / "soul_anchor.animation.json"
RIG_DIR = (
    ROOT / "mod" / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat" / "animations" / "entity"
)
RIG_FILES = (
    RIG_DIR / "bowgun_rig.animation.json",
    RIG_DIR / "abyssal_ancient_cannon_rig.animation.json",
    RIG_DIR / "veritas_et_justitia_rig.animation.json",
)


def number(value: object) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    return float(str(value))


def time_key(value: object) -> str:
    text = f"{float(value):.4f}".rstrip("0")
    return text + "0" if text.endswith(".") else text


def convert_animation(animation: dict) -> dict:
    bones: dict[str, dict[str, dict[str, list[float]]]] = {}
    sounds: dict[str, dict[str, str]] = {}
    for animator in animation.get("animators", {}).values():
        animator_type = animator.get("type")
        bone_name = animator.get("name")
        if animator_type == "bone" and bone_name:
            channels: dict[str, dict[str, list[float]]] = {}
            for keyframe in animator.get("keyframes", []):
                channel = keyframe.get("channel")
                points = keyframe.get("data_points") or []
                if channel not in {"rotation", "position", "scale"} or not points:
                    continue
                point = points[0]
                channels.setdefault(channel, {})[time_key(keyframe.get("time", 0))] = [
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
        elif animator_type == "effect":
            for keyframe in animator.get("keyframes", []):
                if keyframe.get("channel") != "sound":
                    continue
                for point in keyframe.get("data_points") or []:
                    effect = str(point.get("effect") or "chain")
                    if effect.endswith(".mp3"):
                        effect = effect[:-4]
                    sounds[time_key(keyframe.get("time", 0))] = {
                        "effect": effect,
                        "locator": str(point.get("locator") or "locator"),
                    }

    converted = {
        "loop": "hold_on_last_frame"
        if str(animation.get("loop", "hold")).lower() != "loop"
        else "loop",
        "animation_length": float(animation.get("length", 0)),
        "bones": bones,
    }
    if sounds:
        converted["sound_effects"] = dict(sorted(
            sounds.items(), key=lambda item: float(item[0])
        ))
    return converted


def reverse_animation(animation: dict) -> dict:
    """Derive a recall clip by reversing the authored throw timeline."""
    length = float(animation["animation_length"])
    bones: dict[str, dict[str, dict[str, list[float]]]] = {}
    for bone_name, channels in animation.get("bones", {}).items():
        reversed_channels: dict[str, dict[str, list[float]]] = {}
        for channel, values in channels.items():
            frames = sorted(values.items(), key=lambda item: float(item[0]))
            reversed_values: dict[float, list[float]] = {
                length - float(time): value for time, value in frames
            }
            if 0.0 not in reversed_values and frames:
                reversed_values[0.0] = copy.deepcopy(frames[-1][1])
            if length not in reversed_values and frames:
                reversed_values[length] = copy.deepcopy(frames[0][1])
            reversed_channels[channel] = {
                time_key(time): reversed_values[time]
                for time in sorted(reversed_values)
            }
        bones[bone_name] = reversed_channels
    return {
        "loop": "hold_on_last_frame",
        "animation_length": length,
        "bones": bones,
    }


def main() -> int:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    animations = {
        animation["name"]: convert_animation(animation)
        for animation in source.get("animations", [])
    }
    ready_upper = copy.deepcopy(animations["chain_ready_gun"])
    ready_upper["bones"].pop("leg_left", None)
    ready_upper["bones"].pop("leg_right", None)
    animations["chain_ready_gun_upper"] = ready_upper
    for name in (
        "chain_throw_gun", "chain_throw_gun_drag",
        "chain_sliding", "chain_sliding_to_idle",
        "chain_sliding_to_run",
    ):
        upper = copy.deepcopy(animations[name])
        upper["bones"].pop("leg_left", None)
        upper["bones"].pop("leg_right", None)
        animations[name + "_upper"] = upper
    animations["chain_recall"] = reverse_animation(
        animations["chain_throw"]
    )
    animations["chain_recall_gun"] = reverse_animation(
        animations["chain_throw_gun"]
    )

    document = {"format_version": "1.8.0", "animations": animations}
    BUILD_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    BUILD_OUTPUT.write_text(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    for path in RIG_FILES:
        rig = json.loads(path.read_text(encoding="utf-8"))
        rig.setdefault("animations", {}).update(
            copy.deepcopy(animations)
        )
        path.write_text(
            json.dumps(rig, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        print(f"merged {len(animations)} clips -> {path}")
    print(", ".join(sorted(animations)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
