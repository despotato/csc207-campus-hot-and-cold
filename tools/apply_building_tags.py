"""Adds OSM wall/roof material and colour to an existing pack's buildings, changing nothing else.

Usage: python3 -I tools/apply_building_tags.py tools/campuses/<id>.json <buildings.json> src/main/resources/campus/packs/<id>.json

Rebuilds the building list from the same OpenStreetMap export in a temporary file and copies only the
appearance fields (wallMaterial, roofMaterial, wallColour, roofColour). It refuses if the rebuilt buildings don't
match the pack exactly (same IDs at the same positions), so daily puzzles, facts and targets never change.
"""
import importlib.util
import json
import pathlib
import sys
import tempfile

_spec = importlib.util.spec_from_file_location("build_pack", pathlib.Path(__file__).with_name("build_pack.py"))
build_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_pack)

FIELDS = ("wallMaterial", "roofMaterial", "wallColour", "roofColour")


def main(config_path, export_path, pack_path):
    pack = json.load(open(pack_path))
    with tempfile.TemporaryDirectory() as tmp:
        out = pathlib.Path(tmp) / "pack.json"
        build_pack.main(config_path, export_path, str(out))
        fresh = json.load(open(out))
    old, new = pack["buildings"], fresh["buildings"]
    if len(old) != len(new) or any(a["id"] != b["id"] or abs(a["lat"] - b["lat"]) > 1e-6 or abs(a["lon"] - b["lon"]) > 1e-6
                                   for a, b in zip(old, new)):
        sys.exit("the export doesn't match this pack's buildings; not changing anything")
    count = 0
    for a, b in zip(old, new):
        for field in FIELDS:
            a.pop(field, None)
            if field in b:
                a[field] = b[field]
        count += any(field in b for field in FIELDS)
    with open(pack_path, "w") as f:
        json.dump(pack, f, separators=(",", ":"), ensure_ascii=False)
    print(f"{count} of {len(old)} buildings have mapped materials or colours")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
