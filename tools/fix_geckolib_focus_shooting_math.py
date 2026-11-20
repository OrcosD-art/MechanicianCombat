"""Replace unsupported ternary focus-shot Molang with GeckoLib math.max()."""

from __future__ import annotations

import json
from pathlib import Path

RIG_DIR = (
    Path(r"D:\codex\mod\rebuild\mod")
    / "src" / "main" / "resources"
    / "assets" / "mechaniciancombat" / "animations" / "entity"
)
OLD = "query.head_y_rotation > 90 ? -(query.head_y_rotation - 90) / 6 : 0"
NEW = "-math.max(query.head_y_rotation - 90, 0) / 6"


def replace_in(value):
    if isinstance(value, str):
        return NEW if value.strip() == OLD else value.replace(OLD, NEW)
    if isinstance(value, list):
        return [replace_in(child) for child in value]
    if isinstance(value, dict):
        return {key: replace_in(child) for key, child in value.items()}
    return value


def main() -> int:
    changed_total = 0
    for path in sorted(RIG_DIR.glob("*_rig.animation.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        replacements = json.dumps(
            document, ensure_ascii=False, separators=(",", ":")).count(OLD)
        updated = replace_in(document)
        serialized = json.dumps(
            updated, ensure_ascii=False, separators=(",", ":"))
        path.write_text(serialized, encoding="utf-8")
        remaining = serialized.count(OLD)
        changed_total += replacements
        print(
            f"{path.name}: replacements={replacements} "
            f"remaining_ternary={remaining}"
        )
    if changed_total == 0:
        raise RuntimeError("no GeckoLib-incompatible focus expressions found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
