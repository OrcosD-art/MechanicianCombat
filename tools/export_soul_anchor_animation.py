"""Export the latest Soul Anchor animation source into the build directory."""

import os
import sys

ROOT = r"D:\codex\mod\rebuild"
sys.path.insert(0, os.path.join(ROOT, "tools"))

import gen_geckolib_mech as exporter

build = os.path.join(ROOT, "build")
os.makedirs(build, exist_ok=True)
exporter.OUT_GEO = os.path.join(build, "soul_anchor_animation.geo.json")
exporter.OUT_ANIM = os.path.join(build, "soul_anchor.animation.json")
exporter.OUT_TEX = os.path.join(build, "soul_anchor_animation.png")
exporter.RES = os.path.join(ROOT, "build", "soul_anchor_export")

if __name__ == "__main__":
    raise SystemExit(exporter.main())
