r"""把 bbmodel 里**所有方块**的 `export` 打开（并备份原文件）。

为什么每次都要检查：作者给的 bbmodel 里经常有一批方块被设成 `export: false`
（v2 是枪托 stock/bone + 弹壳 shell*，v3 又是同一批、共 21 个）——导出时它们会被**静默丢掉**，
表现就是"枪托没了 / 弹壳飞不出来"（见 SKILL.md 坑 16）。geo 里那根骨骼还在，只是没有方块。

用法：python _mech_work\fix_bbmodel_export_flags.py <bbmodel 路径>
"""
import json
import os
import shutil
import sys


def main() -> None:
	path = sys.argv[1]
	backup = path + ".orig"
	if not os.path.exists(backup):
		shutil.copy2(path, backup)
	with open(path, encoding="utf-8") as fh:
		doc = json.load(fh)
	fixed = 0
	for el in doc.get("elements", []):
		if el.get("export") is False:
			el["export"] = True
			fixed += 1
		if el.get("exporttocache") is False:
			el["exporttocache"] = True
	with open(path, "w", encoding="utf-8") as fh:
		json.dump(doc, fh, ensure_ascii=False)
	print("fixed %d cubes (backup: %s)" % (fixed, backup))


if __name__ == "__main__":
	main()
