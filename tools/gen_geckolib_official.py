"""驱动无头 Blockbench + **官方 GeckoLib 插件**导出 geo / animations，写到 mod 资源里。

前置：无头 Blockbench 跑在 9222（profile 里装了 geckolib 插件，见 tools/bb_install_geckolib*.js）。
"""
import json
import os
import struct
import subprocess
import sys

# --- 本机改动（2026-10-16）：原来的 D:\Minecraft 开发环境不在这台机器上，路径全部指向 rebuild 工作区 ---
REBUILD = r"D:\codex\mod\rebuild"
JAVA = r"C:\Program Files\Java\jdk-21\bin\java.exe"
CDP = os.path.join(REBUILD, "tools", "cdp.ps1")
W = os.path.join(REBUILD, "mod")
MODEL = os.path.join(REBUILD, "assets_src", "破垒炮", "BG261015.bbmodel")
PLUGIN_PLAYER = os.path.join(REBUILD, "tools", "plugin_player.geo.json")
RES = os.path.join(W, "src", "main", "resources", "assets", "tkgun")
OUT_GEO = os.path.join(RES, "geo", "entity", "bowgun_rig.geo.json")
OUT_ANIM = os.path.join(RES, "animations", "entity", "bowgun_rig.animation.json")
JOB_JS = os.path.join(W, "build", "gl_job.js")
# 枪的贴图（player/枪 两边都是这张 64x64 的画；玩家身体用各人自己的 64x64 皮肤）
TEX_PNG = os.path.join(RES, "textures", "entity", "bowgun_gun.png")
PORT = os.environ.get("BB_PORT", "9222")

# 我们的骨架 -> 官方玩家几何里的对应骨骼（身体每块在官方那份里是上下两半，要拼起来）
PLUGIN_TO_OURS = (
    ("chest", ("torso_bend", "torso")),
    ("head", ("head",)),
    ("arm_left", ("left_arm_bend", "left_arm")),
    ("arm_right", ("right_arm_bend", "right_arm")),
    ("leg_left", ("left_leg_bend", "left_leg")),
    ("leg_right", ("right_leg_bend", "right_leg")),
)
FACES = ("north", "east", "south", "west", "up", "down")


def png_size(path):
    head = open(path, "rb").read(24)
    return struct.unpack(">II", head[16:24])


def force_texture_size(text, tex_w, tex_h):
    """把 geo 里的 description.texture_width/height 钉成**贴图真实尺寸**。

    ⚠️ 2026-10-12 的坑（用户报"人和枪的贴图全乱"）：bbmodel 的 `resolution` 是 16x16，
    而模型里的 UV 全是按 64x64 贴图的**像素**存的（作者原始 BG.geo.json / 我们的
    gunfx/bg_geo.json 都声明 64x64）。Blockbench 导出 Bedrock geo 时会把 resolution
    原样写成 texture_width —— 于是 uv 20 被 GeckoLib 算成 20/16 = 1.25，**4 倍偏移**，
    每个面都采样到贴图外面（图集/其它区域），看起来就是"乱七八糟的乱码"。
    （GeoQuad.build 里就是 `u /= texWidth; v /= texHeight`。）

    这里不靠"记得改 bbmodel"，导出后按贴图真实尺寸钉一遍，并顺手核对 UV 有没有越界。
    """
    doc = json.loads(text)
    fixed = []
    for geo in doc.get("minecraft:geometry", []):
        desc = geo.setdefault("description", {})
        old = (desc.get("texture_width"), desc.get("texture_height"))
        if old != (tex_w, tex_h):
            desc["texture_width"], desc["texture_height"] = tex_w, tex_h
            fixed.append((desc.get("identifier"), old))
    over = []
    for geo in doc.get("minecraft:geometry", []):
        for bone in geo.get("bones", []):
            for cube in bone.get("cubes", []):
                for face, entry in (cube.get("uv") or {}).items():
                    u, v = entry["uv"]
                    du, dv = entry.get("uv_size", (0, 0))
                    if max(u, u + abs(du)) > tex_w or max(v, v + abs(dv)) > tex_h:
                        over.append((bone.get("name"), face, u, v, du, dv))
    return json.dumps(doc, ensure_ascii=False), fixed, over


def face_rect(cube, face):
    entry = cube["uv"][face]
    return (entry["uv"][0], entry["uv"][1], entry["uv_size"][0], entry["uv_size"][1])


def skin_table():
    """从官方插件自带的玩家几何里**算出**皮肤版式（不再手写，之前手写把 east/west 写反了）。"""
    doc = json.load(open(PLUGIN_PLAYER, encoding="utf-8"))
    cubes = {b["name"]: b["cubes"][0]
             for b in doc["minecraft:geometry"][0]["bones"] if b.get("cubes")}
    table = {}
    for ours, srcs in PLUGIN_TO_OURS:
        if len(srcs) == 1:
            cube = cubes[srcs[0]]
            table[ours] = {f: list(face_rect(cube, f)) for f in FACES}
            continue
        upper, lower = cubes[srcs[0]], cubes[srcs[1]]
        entry = {}
        for f in ("north", "east", "south", "west"):
            u1, v1, w1, h1 = face_rect(upper, f)
            _, v2, _, h2 = face_rect(lower, f)
            vmin = min(v1, v2)
            vmax = max(v1 + h1, v2 + h2)
            entry[f] = [u1, vmin, w1, vmax - vmin]
        entry["up"] = list(face_rect(upper, "up"))
        entry["down"] = list(face_rect(lower, "down"))
        table[ours] = entry
    return table


def cdp(expr_file):
    r = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", CDP,
                        "--port", PORT, "--file", expr_file],
                       capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600)
    if r.returncode != 0:
        print(r.stdout[-2000:])
        print(r.stderr[-3000:])
        raise SystemExit("Blockbench 调用失败")
    return r.stdout


def main():
    model = json.load(open(MODEL, encoding="utf-8"))
    table = skin_table()
    print("皮肤版式（从官方玩家几何算出）:")
    for k in sorted(table):
        print("   %-10s %s" % (k, {f: tuple(table[k][f]) for f in ("north", "east", "west", "up", "down")}))
    with open(JOB_JS, "w", encoding="utf-8") as fh:
        fh.write("globalThis.__BB_JOB = " + json.dumps({"name": "BG", "modelJson": model, "skinUv": table},
                                                       ensure_ascii=False) + ";\nreturn 1;")
    cdp(JOB_JS)
    raw = cdp(os.path.join(W, "tools", "bb_export_geckolib.js")).strip()
    info = json.loads(raw)
    print("皮肤 UV 改写:", info["uv_fixed"], "面; 身体骨:", info["body_bones"])
    print("项目格式:", info.get("format"))
    print("动作:", info["log"])
    print("导出条目:", [(e["name"], e["type"], e["bytes"]) for e in info["exports"]])
    wrote = []
    tex_w, tex_h = png_size(TEX_PNG)
    print("枪贴图真实尺寸: %dx%d (%s)" % (tex_w, tex_h, os.path.basename(TEX_PNG)))
    for e in info["exports"]:
        name = (e.get("name") or "").lower()
        content = e.get("content")
        if not content:
            continue
        if name.endswith(".geo") or "geo" in name:
            content, fixed, over = force_texture_size(content, tex_w, tex_h)
            for ident, old in fixed:
                print("  geo texture 尺寸修正: %s %s -> (%d, %d)" % (ident, old, tex_w, tex_h))
            if over:
                print("  !! UV 越界(会采样到贴图外):", over[:6], "共", len(over), "面")
            os.makedirs(os.path.dirname(OUT_GEO), exist_ok=True)
            with open(OUT_GEO, "w", encoding="utf-8") as fh:
                fh.write(content)
            wrote.append(("geo", OUT_GEO, os.path.getsize(OUT_GEO)))
        elif "animation" in name or name.endswith(".json"):
            os.makedirs(os.path.dirname(OUT_ANIM), exist_ok=True)
            with open(OUT_ANIM, "w", encoding="utf-8") as fh:
                fh.write(content)
            wrote.append(("anim", OUT_ANIM, os.path.getsize(OUT_ANIM)))
    for kind, path, size in wrote:
        print("写出 %s: %s (%d 字节)" % (kind, path, size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
