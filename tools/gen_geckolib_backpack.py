"""Generate Combat Backpack item, GeckoLib model, animation and texture assets."""

import base64
import copy
import json
import os
import subprocess
import sys

import gen_geckolib_official as base

SOURCE_DIR = r"D:\Download\USER\work\AModel\机械师装备\背部装备"
SOURCE_WEARABLE = os.path.join(SOURCE_DIR, "combat_backpack.bbmodel")
SOURCE_ITEM = os.path.join(SOURCE_DIR, "combat_backpack 物品.bbmodel")
RES = r"D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun"
OUT_ITEM = os.path.join(RES, "models", "item", "combat_backpack.json")
OUT_GEO = os.path.join(RES, "geo", "entity", "combat_backpack.geo.json")
OUT_ANIM = os.path.join(RES, "animations", "entity", "combat_backpack.animation.json")
OUT_TEX = os.path.join(RES, "textures", "entity", "combat_backpack.png")
ITEM_TEX = os.path.join(RES, "textures", "item", "combat_backpack.png")
JOB_JS = r"D:\codex\mod\rebuild\build\combat_backpack_job.js"
PORT = os.environ.get("BB_PORT", "9333")


def cdp(expr_file):
    result = subprocess.run([
        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", base.CDP,
        "--port", PORT, "--file", expr_file,
    ], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600)
    if result.returncode != 0:
        print(result.stdout[-2000:])
        print(result.stderr[-3000:])
        raise SystemExit("Blockbench call failed")
    return result.stdout


def find_group(children, name):
    for child in children:
        if isinstance(child, dict):
            if child.get("name") == name:
                return child
            found = find_group(child.get("children", []), name)
            if found:
                return found
    return None


def shift_group_y(group, amount):
    group["pivot"][1] += amount
    for child in group.get("children", []):
        if isinstance(child, dict):
            shift_group_y(child, amount)


def wearable_model(source):
    model = copy.deepcopy(source)
    backpack = find_group(model["outliner"], "combat_backpack")
    if backpack is None:
        raise SystemExit("combat_backpack group missing")
    backpack = copy.deepcopy(backpack)
    backpack.pop("parent", None)

    element_ids = set()

    def collect_ids(node):
        for child in node.get("children", []):
            if isinstance(child, str):
                element_ids.add(child)
            else:
                collect_ids(child)

    collect_ids(backpack)
    shift_group_y(backpack, -12.0)
    for element in model["elements"]:
        if element.get("uuid") in element_ids:
            for key in ("from", "to"):
                element[key][1] -= 12.0
            origin = element.get("origin")
            if origin:
                origin[1] -= 12.0
    model["outliner"] = [backpack]
    return model


def item_model(source):
    elements = []
    for src in source["elements"]:
        faces = {}
        for face_name, src_face in src.get("faces", {}).items():
            texture = src_face.get("texture")
            if texture is None:
                continue
            if texture != 0:
                raise SystemExit("item model uses unsupported texture index %s" % texture)
            face = {
                "uv": src_face["uv"],
                "texture": "#0",
            }
            if src_face.get("rotation"):
                face["rotation"] = src_face["rotation"]
            faces[face_name] = face
        element = {
            "from": src["from"],
            "to": src["to"],
            "faces": faces,
        }
        if src.get("rotation"):
            element["rotation"] = src["rotation"]
        if "shade" in src:
            element["shade"] = src["shade"]
        elements.append(element)
    return {
        "textures": {
            "0": "tkgun:item/combat_backpack",
            "particle": "tkgun:item/combat_backpack",
        },
        "elements": elements,
        "display": source.get("display", {}),
    }


def extract_texture(model, destination):
    texture = model["textures"][0]
    source = texture.get("source", "")
    if source.startswith("data:image/") and ";base64," in source:
        raw = base64.b64decode(source.split(",", 1)[1])
    else:
        path = texture.get("path")
        if not path:
            raise SystemExit("pack texture source missing")
        raw = open(path, "rb").read()
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    open(destination, "wb").write(raw)


def main():
    wearable = json.load(open(SOURCE_WEARABLE, encoding="utf-8"))
    item = json.load(open(SOURCE_ITEM, encoding="utf-8"))
    export_model = wearable_model(wearable)

    os.makedirs(os.path.dirname(OUT_GEO), exist_ok=True)
    os.makedirs(os.path.dirname(OUT_ANIM), exist_ok=True)
    os.makedirs(os.path.dirname(OUT_ITEM), exist_ok=True)
    with open(OUT_ITEM, "w", encoding="utf-8") as handle:
        json.dump(item_model(item), handle, ensure_ascii=False, separators=(",", ":"))
    extract_texture(wearable, OUT_TEX)
    extract_texture(item, ITEM_TEX)

    with open(JOB_JS, "w", encoding="utf-8") as handle:
        handle.write("globalThis.__BB_JOB = " + json.dumps({
            "name": "combat_backpack",
            "modelJson": export_model,
            "skinUv": {},
        }, ensure_ascii=False) + ";\nreturn 1;")
    cdp(JOB_JS)
    info = json.loads(cdp(os.path.join(base.REBUILD, "tools", "bb_export_geckolib.js")).strip())
    print("exports:", [(entry.get("name"), entry.get("type"), entry.get("bytes"))
                       for entry in info.get("exports", [])])

    tex_w, tex_h = base.png_size(OUT_TEX)
    wrote_geo = wrote_anim = False
    for entry in info.get("exports", []):
        name = (entry.get("name") or "").lower()
        content = entry.get("content")
        if not content:
            continue
        if name.endswith(".geo") or "geo" in name:
            content, fixed, outside = base.force_texture_size(content, tex_w, tex_h)
            if fixed:
                print("texture size fixed:", fixed)
            if outside:
                print("uv outside texture:", outside)
            open(OUT_GEO, "w", encoding="utf-8").write(content)
            wrote_geo = True
        elif "animation" in name or name.endswith(".json"):
            open(OUT_ANIM, "w", encoding="utf-8").write(content)
            wrote_anim = True

    if not wrote_geo or not wrote_anim:
        raise SystemExit("missing geo or animation export")
    print("wrote:", OUT_ITEM)
    print("wrote:", OUT_GEO)
    print("wrote:", OUT_ANIM)


if __name__ == "__main__":
    sys.exit(main())
