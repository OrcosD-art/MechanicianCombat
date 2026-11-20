"""Sync Veritas ATK_hold_over final-hit particle metadata."""

from __future__ import annotations

import json
from pathlib import Path

TARGET = Path(
    r"D:\codex\mod\rebuild\mod\src\main\resources\assets"
    r"\mechaniciancombat\animations\entity"
    r"\veritas_et_justitia_rig.animation.json"
)


def ordered(entries: dict) -> dict:
    return dict(sorted(entries.items(), key=lambda item: float(item[0])))


def main() -> int:
    rig = json.loads(TARGET.read_text(encoding="utf-8"))
    particles = rig["animations"]["ATK_hold_over"]["particle_effects"]
    particles.pop("0.91667", None)
    particles["0.875"] = {
        "effect": "thunder_big",
        "locator": "locator",
    }
    rig["animations"]["ATK_hold_over"]["particle_effects"] = ordered(
        particles
    )
    TARGET.write_text(
        json.dumps(rig, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(json.dumps(
        rig["animations"]["ATK_hold_over"]["particle_effects"],
        ensure_ascii=False,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
