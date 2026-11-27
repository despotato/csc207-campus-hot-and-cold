"""Repairs daily puzzles whose start point the game can't use, without changing any other puzzle.

Usage: python3 -I tools/repair_daily_starts.py tools/campuses/<id>.json src/main/resources/campus/packs/<id>.json

A start is unusable if it lies outside the pack's bounds (the walkability grid treats that as blocked, so the
daily can't be set up) or within ~4 m of a building. Only those dates get a new start, chosen with the same
rules as tools/build_pack.py (main footpath network, 450-900 m from the zone, clear of buildings), seeded by
campus and date so the repair is reproducible. Targets and zones never change, so puzzles players already
saw stay the same.
"""
import hashlib
import importlib.util
import json
import math
import pathlib
import random
import sys

_spec = importlib.util.spec_from_file_location("build_pack", pathlib.Path(__file__).with_name("build_pack.py"))
build_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_pack)


def main(config_path, pack_path):
    config = json.load(open(config_path))
    pack = json.load(open(pack_path))
    bounds = pack["bounds"]
    lat0 = (bounds["south"] + bounds["north"]) / 2
    margin_lat = 15 / 111195
    margin_lon = margin_lat / math.cos(math.radians(lat0))

    def within_bounds(p):
        return (bounds["west"] + margin_lon <= p[0] <= bounds["east"] - margin_lon
                and bounds["south"] + margin_lat <= p[1] <= bounds["north"] - margin_lat)

    solid = [polygon[0] for b in pack["buildings"] if not b["flat"] for polygon in b["outline"]]
    boxes = [(min(q[0] for q in r), max(q[0] for q in r), min(q[1] for q in r), max(q[1] for q in r)) for r in solid]

    def clear(p):
        return not any(x0 - 0.00005 <= p[0] <= x1 + 0.00005 and y0 - 0.00005 <= p[1] <= y1 + 0.00005
                       for x0, x1, y0, y1 in boxes)

    coords, edges = pack["walkways"]["nodes"], pack["walkways"]["edges"]
    parent = list(range(len(coords)))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    for a, b in edges:
        parent[find(a)] = find(b)
    sizes = {}
    for i in range(len(coords)):
        sizes[find(i)] = sizes.get(find(i), 0) + 1
    main_root = max(sizes, key=sizes.get)
    network = [coords[i] for i in range(len(coords)) if find(i) == main_root and within_bounds(coords[i])]

    repaired, failed = [], []
    for date, puzzle in sorted(pack["daily"].items()):
        if within_bounds(puzzle["start"]) and clear(puzzle["start"]):
            continue
        zone = puzzle["zone"]
        rng = random.Random(int(hashlib.sha256((config["id"] + date + "start-repair").encode()).hexdigest(), 16))
        starts = [p for p in network
                  if build_pack.MIN_START <= build_pack.metres(p, zone, lat0) <= build_pack.MAX_START]
        rng.shuffle(starts)
        start = next((p for p in starts if clear(p)), None)
        if start is None:
            failed.append(date)
            continue
        puzzle["start"] = start
        repaired.append(date)

    with open(pack_path, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(repaired)} daily starts repaired: {', '.join(repaired) or 'none'}")
    if failed:
        sys.exit(f"could not repair: {', '.join(failed)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
