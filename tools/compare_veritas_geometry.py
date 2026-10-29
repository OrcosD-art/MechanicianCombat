import json
import sys


def rounded(values):
	return [round(float(value), 4) for value in values]


def build_groups(nodes):
	groups = {}
	for node in nodes or []:
		if not isinstance(node, dict):
			continue
		groups[node["name"]] = node
		groups.update(build_groups(node.get("children")))
	return groups


def converted_cube(element):
	source_from = element["from"]
	source_to = element["to"]
	cube = {
		"origin": rounded([-source_to[0], source_from[1], source_from[2]]),
		"size": rounded([
			abs(source_to[index] - source_from[index]) for index in range(3)
		]),
	}
	rotation = element.get("rotation")
	if rotation and any(rotation):
		cube["rotation"] = rounded([-rotation[0], -rotation[1], rotation[2]])
	return cube


def main(model_path, geo_path):
	model = json.load(open(model_path, encoding="utf-8"))
	runtime = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
	elements = {element["uuid"]: element for element in model["elements"]}
	groups = build_groups(model["outliner"])

	mismatches = 0
	for bone in runtime["bones"]:
		name = bone["name"]
		group = groups.get(name)
		if group is None:
			continue
		source_pivot = group.get("origin")
		if source_pivot:
			converted_pivot = rounded([-source_pivot[0], source_pivot[1], source_pivot[2]])
			if converted_pivot != rounded(bone["pivot"]):
				print("PIVOT", name, converted_pivot, "!=", bone["pivot"])
				mismatches += 1

		source_rotation = group.get("rotation") or [0, 0, 0]
		converted_rotation = rounded([-source_rotation[0], -source_rotation[1], source_rotation[2]])
		if converted_rotation != rounded(bone.get("rotation") or [0, 0, 0]):
			print("ROTATION", name, converted_rotation, "!=", bone.get("rotation"))
			mismatches += 1

		cubes = []
		for child in group.get("children", []):
			if not isinstance(child, str):
				continue
			element = elements.get(child)
			if element and element.get("type") == "cube":
				cubes.append(converted_cube(element))
		runtime_cubes = []
		for cube in bone.get("cubes", []):
			converted = {
				"origin": rounded(cube["origin"]),
				"size": rounded(cube["size"]),
			}
			if cube.get("rotation"):
				converted["rotation"] = rounded(cube["rotation"])
			runtime_cubes.append(converted)
		if cubes != runtime_cubes:
			print("CUBES", name, cubes, "!=", runtime_cubes)
			mismatches += 1

	print("geometry mismatches:", mismatches)
	return 1 if mismatches else 0


if __name__ == "__main__":
	raise SystemExit(main(sys.argv[1], sys.argv[2]))
