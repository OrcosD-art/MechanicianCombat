"""Export Bedrock geometry/animations with Blockbench built-ins, then apply tkgun postprocessing."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_geckolib_mech as mech
import gen_geckolib_official as base


def main():
    model_path = sys.argv[1]
    model = json.load(open(model_path, encoding="utf-8"))
    print("source:", model_path, "elements=", len(model["elements"]),
          "animations=", len(model.get("animations", [])))
    tex_w, tex_h = mech.extract_texture(model, mech.OUT_TEX)
    extras = mech.extract_extra_textures(model)
    table = base.skin_table()

    job = os.path.join(mech.W, "build", "mech_builtin_job.js")
    os.makedirs(os.path.dirname(job), exist_ok=True)
    with open(job, "w", encoding="utf-8") as fh:
        fh.write("globalThis.__BB_JOB = " + json.dumps(
            {"name": "BG261015", "modelJson": model, "skinUv": table},
            ensure_ascii=False) + ";\nreturn 1;")
    mech.cdp(job)
    raw = mech.cdp(os.path.join(mech.TOOLS, "bb_export_builtin.js")).strip()
    info = json.loads(raw.splitlines()[-1])
    print("actions:", info.get("log"), "uv_fixed=", info.get("uv_fixed"))

    wrote = []
    animation_document = info.get("animation_document")
    if isinstance(animation_document, str) and animation_document:
        processed = mech.postprocess_animations(animation_document, model, extras)
        os.makedirs(os.path.dirname(mech.OUT_ANIM), exist_ok=True)
        with open(mech.OUT_ANIM, "w", encoding="utf-8") as fh:
            fh.write(processed)
        names = list(json.loads(processed)["animations"].keys())
        print("animations:", len(names), ", ".join(names))
        wrote.append(("animation", mech.OUT_ANIM, os.path.getsize(mech.OUT_ANIM)))

    for entry in info.get("exports", []):
        name = (entry.get("name") or "").lower()
        content = entry.get("content")
        if not isinstance(content, str) or not content:
            continue
        if name.endswith(".geo.json") or "geometry" in name:
            content = mech.split_foreign_cubes(content, model)
            content, fixed, over = base.force_texture_size(content, tex_w, tex_h)
            print("geo texture size fixes:", fixed)
            print("geo uv overflow:", over[:8], "count=", len(over))
            os.makedirs(os.path.dirname(mech.OUT_GEO), exist_ok=True)
            with open(mech.OUT_GEO, "w", encoding="utf-8") as fh:
                fh.write(content)
            wrote.append(("geo", mech.OUT_GEO, os.path.getsize(mech.OUT_GEO)))
        elif name.endswith(".animation.json") or "animation" in name:
            content = mech.postprocess_animations(content, model, extras)
            os.makedirs(os.path.dirname(mech.OUT_ANIM), exist_ok=True)
            with open(mech.OUT_ANIM, "w", encoding="utf-8") as fh:
                fh.write(content)
            names = list(json.loads(content)["animations"].keys())
            print("animations:", len(names), ", ".join(names))
            wrote.append(("animation", mech.OUT_ANIM, os.path.getsize(mech.OUT_ANIM)))

    for item in wrote:
        print("wrote:", item)
    if not any(item[0] == "animation" for item in wrote):
        raise SystemExit("No animation export was produced")


if __name__ == "__main__":
    main()
