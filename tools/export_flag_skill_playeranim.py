"""Generate the PlayerAnimator skill clip for the backpack War Banner."""

import importlib.util
import json
import tempfile
from pathlib import Path


SOURCE = Path(
    r"D:\Download\USER\work\AModel\机械师装备\背部装备"
    r"\combat_backpack\战旗\flag_skill.bbmodel"
)
CONVERTER = Path(
    r"D:\codex\mod\rebuild\work\jump_playeranim_from_bbmodel.py"
)
OUTPUT = Path(
    r"D:\codex\mod\rebuild\mod\src\main\resources"
    r"\assets\tkgun\player_animations\flag_skill.json"
)


def main():
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    for animation in source.get("animations", []):
        if animation.get("name") == "skill_flag":
            animation["name"] = "jump"
            break
    else:
        raise RuntimeError("skill_flag animation is missing")

    temporary = Path(tempfile.gettempdir()) / "tkgun_flag_skill.bbmodel"
    temporary.write_text(
        json.dumps(source, ensure_ascii=False), encoding="utf-8")

    spec = importlib.util.spec_from_file_location(
        "flag_playeranim_converter", CONVERTER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.SOURCE = temporary
    module.OUT = Path(tempfile.gettempdir()) / "tkgun_flag_playeranim"
    module.build()

    result = json.loads(
        (module.OUT / "backpack_air_poses.json").read_text(encoding="utf-8"))
    animation = result["animations"]["backpack_air_jump"]
    animation["bones"] = {
        name: animation["bones"][name]
        for name in ("torso", "rightArm", "leftArm")
        if name in animation["bones"]
    }
    # Blockbench's authored front is opposite PlayerAnimator's model front.
    # The previous export mirrored pitch but left yaw unchanged, making the
    # upper body and right arm sweep in the opposite direction.
    for bone in animation["bones"].values():
        for rotation in bone.get("rotation", {}).values():
            rotation[1] = -rotation[1]
    OUTPUT.write_text(
        json.dumps(
            {"animations": {"flag_skill": animation}},
            ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    print(OUTPUT)


if __name__ == "__main__":
    main()
