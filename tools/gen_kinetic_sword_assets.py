"""Export the Kinetic Sword player rig and Java item model from the supplied bbmodels."""

import base64
import copy
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geckolib_mech as mech
import gen_geckolib_official as base


REBUILD = r"D:\codex\mod\rebuild"
TOOLS = os.path.join(REBUILD, "tools")
W = os.path.join(REBUILD, "mod")
RES = os.path.join(W, "src", "main", "resources", "assets",
                   "mechaniciancombat")
SOURCE_DIR = r"D:\Download\USER\work\AModel\机械师装备\剑"
RIG_SOURCE = os.path.join(SOURCE_DIR, "剑.bbmodel")
ITEM_SOURCE = os.path.join(SOURCE_DIR, "剑-物品.bbmodel")
OUT_GEO = os.path.join(RES, "geo", "entity",
                       "kinetic_sword_rig.geo.json")
OUT_ANIM = os.path.join(RES, "animations", "entity",
                        "kinetic_sword_rig.animation.json")
OUT_ENTITY_TEXTURE = os.path.join(RES, "textures", "entity",
                                  "kinetic_sword_sword.png")
OUT_ITEM_TEXTURE = os.path.join(RES, "textures", "item",
                                "kinetic_sword.png")
OUT_ITEM_MODEL = os.path.join(RES, "models", "item",
                              "kinetic_sword.json")


def decode_texture(texture):
    source = texture.get("source", "")
    if source.startswith("data:"):
        return base64.b64decode(source.split(",", 1)[1])
    with open(source, "rb") as handle:
        return handle.read()


def source_timelines(model):
    timelines = {}
    for animation in model.get("animations", []):
        events = {}
        for animator in animation.get("animators", {}).values():
            if not isinstance(animator, dict) or animator.get("type") != "effect":
                continue
            for keyframe in animator.get("keyframes", []):
                if keyframe.get("channel") != "timeline":
                    continue
                points = keyframe.get("data_points") or []
                if not points:
                    continue
                script = str(points[0].get("script") or "").strip()
                if not script:
                    continue
                key = ("%.5f" % float(keyframe.get("time") or 0.0)).rstrip(
                    "0").rstrip(".")
                events[key] = script
        if events:
            timelines[animation.get("name")] = events
    return timelines


def postprocess_animations(raw, model):
    doc = json.loads(raw)
    mech.bake_step_keyframes(doc, model)
    # The authored ATK_6 body spin is linear from 0° to 360°. The generic
    # step-baker can add a hold key immediately before 0.375s, which turns the
    # full rotation into a single invisible jump. Remove that synthetic hold.
    body_rotation = ((((doc.get("animations") or {}).get("GS_ATK_6") or {})
                      .get("bones") or {}).get("body") or {}).get("rotation")
    if isinstance(body_rotation, dict):
        body_rotation.pop("0.3749", None)
    timelines = source_timelines(model)
    for name, events in timelines.items():
        animation = (doc.get("animations") or {}).get(name)
        if animation is not None and events:
            animation["timeline"] = events
    return json.dumps(doc, ensure_ascii=False)


def export_rig(model):
    mech.ensure_geckolib()
    texture = model.get("textures", [{}])[0]
    texture_bytes = decode_texture(texture)
    os.makedirs(os.path.dirname(OUT_ENTITY_TEXTURE), exist_ok=True)
    with open(OUT_ENTITY_TEXTURE, "wb") as handle:
        handle.write(texture_bytes)
    tex_w, tex_h = mech.png_dims(texture_bytes)

    skin_table = base.skin_table()
    job_path = os.path.join(W, "build", "kinetic_sword_gl_job.js")
    os.makedirs(os.path.dirname(job_path), exist_ok=True)
    with open(job_path, "w", encoding="utf-8") as handle:
        handle.write(
            "globalThis.__BB_JOB = "
            + json.dumps({
                "name": "KineticSword",
                "modelJson": model,
                "skinUv": skin_table,
            }, ensure_ascii=False)
            + ";\nreturn 1;"
        )
    mech.cdp(job_path)
    raw = mech.cdp(os.path.join(TOOLS, "bb_export_geckolib.js")).strip()
    info = json.loads(raw)
    print("GeckoLib exports:", [
        (entry.get("name"), entry.get("type"), entry.get("bytes"))
        for entry in info.get("exports", [])
    ])
    wrote_animation = False
    for entry in info.get("exports", []):
        content = entry.get("content")
        name = (entry.get("name") or "").lower()
        if not content:
            continue
        if name.endswith(".geo") or "geo" in name:
            content, fixed, over = base.force_texture_size(
                content, tex_w, tex_h)
            geo_doc = json.loads(content)
            geo_doc["format_version"] = "1.12.0"
            geometry = geo_doc["minecraft:geometry"][0]
            geometry["description"]["identifier"] = \
                "geometry.mechaniciancombat.kinetic_sword_rig"
            content = json.dumps(geo_doc, ensure_ascii=False)
            if fixed:
                print("geo texture size:", fixed)
            if over:
                print("UV overflow:", over[:8], "count=", len(over))
            os.makedirs(os.path.dirname(OUT_GEO), exist_ok=True)
            with open(OUT_GEO, "w", encoding="utf-8") as handle:
                handle.write(content)
        elif "animation" in name or name.endswith(".json"):
            content = postprocess_animations(content, model)
            os.makedirs(os.path.dirname(OUT_ANIM), exist_ok=True)
            with open(OUT_ANIM, "w", encoding="utf-8") as handle:
                handle.write(content)
            wrote_animation = True

    # Blockbench 4.12's GeckoLib action can expose geometry but skip the
    # animation download dialog. The built-in compiler returns the same
    # Bedrock animation document directly, so use it as a deterministic
    # fallback rather than silently keeping the previous exported clips.
    if not wrote_animation:
        raw_builtin = mech.cdp(
            os.path.join(TOOLS, "bb_export_builtin.js")).strip()
        builtin = json.loads(raw_builtin)
        animation_document = builtin.get("animation_document")
        if not animation_document:
            raise RuntimeError(
                "Blockbench returned neither GeckoLib nor built-in animations")
        content = postprocess_animations(animation_document, model)
        os.makedirs(os.path.dirname(OUT_ANIM), exist_ok=True)
        with open(OUT_ANIM, "w", encoding="utf-8") as handle:
            handle.write(content)
        print("animations: built-in fallback ->", OUT_ANIM)

    return texture_bytes, tex_w, tex_h


def export_item(item_model):
    job_path = os.path.join(W, "build", "kinetic_sword_item_job.js")
    with open(job_path, "w", encoding="utf-8") as handle:
        handle.write(
            "globalThis.__BB_JOB = "
            + json.dumps({
                "name": "KineticSwordItem",
                "modelJson": item_model,
            }, ensure_ascii=False)
            + ";\nreturn 1;"
        )
    mech.cdp(job_path)
    raw = mech.cdp(os.path.join(TOOLS, "bb_export_java_item.js")).strip()
    info = json.loads(raw)
    print("Java item exports:", [
        (entry.get("name"), entry.get("type"))
        for entry in info.get("exports", [])
    ])
    content = next((
        entry.get("content")
        for entry in info.get("exports", [])
        if entry.get("content")
        and ((entry.get("name") or "").endswith(".json")
             or "model" in (entry.get("name") or "").lower())
    ), None)
    if content is None:
        content = next((
            entry.get("content")
            for entry in info.get("exports", [])
            if entry.get("content")
        ), None)
    if content is None:
        raise RuntimeError("Java item exporter returned no model")
    doc = json.loads(content)
    doc.pop("format_version", None)
    for element in doc.get("elements", []):
        element["faces"] = {
            face: value
            for face, value in element.get("faces", {}).items()
            if value.get("texture") != "#missing"
        }
    textures = doc.get("textures")
    if isinstance(textures, dict):
        for key in list(textures):
            textures[key] = "mechaniciancombat:item/kinetic_sword"
    os.makedirs(os.path.dirname(OUT_ITEM_MODEL), exist_ok=True)
    with open(OUT_ITEM_MODEL, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(doc, ensure_ascii=False, separators=(",", ":")))
    return doc


def main():
    rig_model = json.load(open(RIG_SOURCE, encoding="utf-8"))
    item_model = json.load(open(ITEM_SOURCE, encoding="utf-8"))
    texture, _width, _height = export_rig(rig_model)
    os.makedirs(os.path.dirname(OUT_ITEM_TEXTURE), exist_ok=True)
    with open(OUT_ITEM_TEXTURE, "wb") as handle:
        handle.write(texture)
    export_item(item_model)
    print("Wrote:", OUT_GEO, OUT_ANIM, OUT_ENTITY_TEXTURE,
          OUT_ITEM_TEXTURE, OUT_ITEM_MODEL)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
