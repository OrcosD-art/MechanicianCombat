"""Export War Banner geometry with source UVs and compile its animations."""

import json
import sys
from pathlib import Path


def find_group(nodes, name):
    for node in nodes or []:
        if not isinstance(node, dict):
            continue
        if node.get("name") == name:
            return node
        found = find_group(node.get("children"), name)
        if found:
            return found
    return None


def main():
    source_path = Path(sys.argv[1])
    export_log = Path(sys.argv[2])
    geo_out = Path(sys.argv[3])
    animation_out = Path(sys.argv[4])

    source = json.loads(source_path.read_text(encoding="utf-8"))
    export = None
    for line in export_log.read_text(encoding="utf-8").splitlines():
        if line.startswith('{"log"'):
            export = json.loads(line.split("--- exit=", 1)[0])
            break
    if not export:
        raise RuntimeError("Blockbench export result was not found")

    geometry = export["exports"][0]["content"]
    elements = {element["uuid"]: element for element in source["elements"]}
    uv_count = 0

    for bone in geometry["bones"]:
        source_group = find_group(source["outliner"], bone["name"])
        if not source_group:
            continue
        source_cubes = []
        for child in source_group.get("children", []):
            if isinstance(child, str):
                element = elements.get(child)
                if element and element.get("type", "cube") == "cube":
                    source_cubes.append(element)
            elif isinstance(child, dict) and child.get("type") == "cube":
                source_cubes.append(child)
        exported_cubes = bone.get("cubes", [])
        if len(source_cubes) != len(exported_cubes):
            raise RuntimeError(
                f"Cube count mismatch for {bone['name']}: "
                f"{len(source_cubes)} != {len(exported_cubes)}")
        for source_cube, exported_cube in zip(source_cubes, exported_cubes):
            uv = {}
            for face_name, face in source_cube.get("faces", {}).items():
                if face.get("uv") is None or face.get("texture") is None:
                    continue
                x1, y1, x2, y2 = map(float, face["uv"])
                entry = {
                    "uv": [x1, y1],
                    "uv_size": [x2 - x1, y2 - y1],
                }
                if face.get("rotation"):
                    entry["uv_rotation"] = face["rotation"]
                uv[face_name] = entry
                uv_count += 1
            if uv:
                exported_cube["uv"] = uv

    geometry.pop("texturewidth", None)
    geometry.pop("textureheight", None)
    geometry.pop("visible_bounds_width", None)
    geometry.pop("visible_bounds_height", None)
    geometry.pop("visible_bounds_offset", None)
    document = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.tkgun.war_banner",
                "texture_width": 48,
                "texture_height": 48,
                "visible_bounds_width": 3,
                "visible_bounds_height": 5.5,
                "visible_bounds_offset": [0, 2.25, 0],
            },
            "bones": geometry["bones"],
        }],
    }
    geo_out.write_text(
        json.dumps(document, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8")
    animation_out.write_text(
        export["animation_document"], encoding="utf-8")
    print(f"UV faces: {uv_count}")
    print(f"Geo: {geo_out}")
    print(f"Animations: {animation_out}")


if __name__ == "__main__":
    main()
