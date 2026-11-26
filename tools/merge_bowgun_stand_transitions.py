"""Merge the updated BG261015 clips without discarding the complete rig.

The latest source bbmodel is animation-focused and does not contain all
weapon-only groups/animations. Use r1093 as the complete resource baseline,
replace clips that exist in the fresh export, then synchronize the shared rig
channels to the other gun animation resources.
"""

import copy
import json
import os
import zipfile


ROOT = r"D:\codex\mod\rebuild"
BASELINE_JAR = os.path.join(ROOT, "dist", "MechanicianCombat-r1093.jar")
ASSET_ROOT = os.path.join(ROOT, "mod", "src", "main", "resources", "assets",
                          "mechaniciancombat")
ANIMATION_DIR = os.path.join(ASSET_ROOT, "animations", "entity")
MAIN_ANIMATION = os.path.join(ANIMATION_DIR, "bowgun_rig.animation.json")
MAIN_GEOMETRY = os.path.join(ASSET_ROOT, "geo", "entity",
                             "bowgun_rig.geo.json")
OTHER_GUNS = (
    os.path.join(ANIMATION_DIR,
                 "abyssal_ancient_cannon_rig.animation.json"),
    os.path.join(ANIMATION_DIR,
                 "veritas_et_justitia_rig.animation.json"),
)
TRANSITION_CLIPS = (
    "idle-to_stand_shooting",
    "stand_shooting_back-idle",
    "stand_shooting_hold",
    "stand_shooting_resume_1",
    "stand_shooting_resume_2",
    "stand_shooting_resume_3",
    "stand_shooting_resume_4",
    "stand_shooting_resume_5",
)
GUN_SYNC_CLIPS = (
    "sliding",
    "Focus_ready",
    "Focus_over",
    "sliding_fast_ready",
    "rolling_fast_ready",
    "stand_shooting",
    "stand_shooting_hold",
    "focus-roll-run",
)
SHARED_PLAYER_CLIPS = (
    "Focus_hold",
    "Focus_ready",
    "Focus_over",
    "Focus_shooting_extra",
    "Focus_shooting1",
    "Focus_shooting4",
    "Focus_shooting6",
    "Focus_shooting7",
    "focus-roll-run",
    "roll",
    "roll_to_idle",
    "roll_to_idle_upper",
    "rolling_fast_ready",
    "sliding",
    "sliding_fast_ready",
    "stand_shooting",
)
SHARED_PLAYER_BONES = (
    "body",
    "chest",
    "head",
    "arm_left",
    "arm_right",
    "leg_left",
    "leg_right",
)


def read_zip_json(archive, entry):
    with zipfile.ZipFile(archive) as jar:
        return json.loads(jar.read(entry).decode("utf-8"))


def read_zip_bytes(archive, entry):
    with zipfile.ZipFile(archive) as jar:
        return jar.read(entry)


def write_json(path, document):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False,
                  separators=(",", ":"))


def main():
    baseline = read_zip_json(
        BASELINE_JAR,
        "assets/mechaniciancombat/animations/entity/"
        "bowgun_rig.animation.json")
    fresh = json.load(open(MAIN_ANIMATION, encoding="utf-8"))

    # Keep the complete idle hierarchy. The fresh standalone export omitted
    # idle_upper/idle_lower, which caused upper-body loss and root offset leaks.
    keep_existing = {"idle", "idle_lower", "idle_upper"}
    for name, animation in fresh.get("animations", {}).items():
        if name not in keep_existing:
            baseline["animations"][name] = copy.deepcopy(animation)
    write_json(MAIN_ANIMATION, baseline)

    # The supplied bbmodel is not the complete runtime geometry. Restore the
    # complete 49-bone/90-cube rig from the last good build.
    geometry = read_zip_bytes(
        BASELINE_JAR,
        "assets/mechaniciancombat/geo/entity/bowgun_rig.geo.json")
    with open(MAIN_GEOMETRY, "wb") as handle:
        handle.write(geometry)

    base_animations = baseline["animations"]
    for path in OTHER_GUNS:
        target = json.load(open(path, encoding="utf-8"))
        target_animations = target.setdefault("animations", {})

        # New standing raise/return clips must exist in every independent gun
        # resource or upperAnimationFor resolves missing names at runtime.
        for name in TRANSITION_CLIPS:
            source = base_animations.get(name)
            if source is not None:
                target_animations[name] = copy.deepcopy(source)

        # The shared gun group was updated in the source for firing, raise,
        # lowering and quick-ready transitions. Preserve each weapon's authored
        # body/arm channels while synchronizing only its gun channel.
        for name in GUN_SYNC_CLIPS:
            source = base_animations.get(name)
            target_animation = target_animations.get(name)
            if source is None or target_animation is None:
                continue
            gun = (source.get("bones") or {}).get("gun")
            if gun is not None:
                target_animation.setdefault("bones", {})["gun"] = \
                    copy.deepcopy(gun)

        # Other weapon-only bones remain authored per gun, but the player
        # skeleton must follow the latest shared base motion.
        for name in SHARED_PLAYER_CLIPS:
            source = base_animations.get(name)
            target_animation = target_animations.get(name)
            if source is None or target_animation is None:
                continue
            source_bones = source.get("bones") or {}
            target_bones = target_animation.setdefault("bones", {})
            for bone in SHARED_PLAYER_BONES:
                if bone in source_bones:
                    target_bones[bone] = copy.deepcopy(source_bones[bone])
                else:
                    target_bones.pop(bone, None)

        write_json(path, target)
        print("merged:", path, "animations=", len(target_animations))

    print("merged:", MAIN_ANIMATION,
          "animations=", len(base_animations))
    print("restored complete geometry:", MAIN_GEOMETRY)


if __name__ == "__main__":
    main()
