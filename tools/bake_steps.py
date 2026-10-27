"""把作者标 `step` 的关键帧烘进**已有的**动画 JSON（不用重开 Blockbench）。

用法（Blender 自带的 python）：
    python tools\\bake_steps.py

原理见 gen_geckolib_mech.bake_step_keyframes 的注释：Blockbench 导 Bedrock 动画不写
`lerp_mode`，GeckoLib 全按线性插值播 —— 作者给枪口焰/速度线标了 `step`（要硬切），
结果变成"渐显渐隐"，就是用户 2026-10-16 报的"fire 组的步帧未生效"。
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geckolib_mech as mech

MODEL = r"D:\codex\mod\rebuild\assets_src\破垒炮\BG261015.bbmodel"
ANIM = os.path.join(r"D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun",
                    "animations", "entity", "bowgun_rig.animation.json")

model = json.load(open(MODEL, encoding="utf-8"))
doc = json.load(open(ANIM, encoding="utf-8"))
before = json.dumps(doc, ensure_ascii=False)
n = mech.bake_step_keyframes(doc, model)
if n:
    with open(ANIM, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(doc, ensure_ascii=False))
print("baked %d step keys (%s) -> %s" % (n, "changed" if n else "no change", ANIM))
