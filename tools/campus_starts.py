"""Moves future daily starts that lie off campus back onto it, keeping each day's target and zone.

Usage: python3 -I tools/campus_starts.py tools/campuses/<id>.json src/main/resources/campus/packs/<id>.json FROM

A pack's bounds also take in the city around the campus, so older packs could start a puzzle several blocks away.
A start now counts as on campus near a landmark building (build_pack.on_campus, mirroring Expedition.onCampus).
FROM is the first date (YYYY-MM-DD) that may change; use tomorrow so today's and past puzzles stay as players saw them.
"""
import hashlib
import importlib.util
import json
import pathlib
import random
import sys

_spec = importlib.util.spec_from_file_location("build_pack", pathlib.Path(__file__).with_name("build_pack.py"))
build_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_pack)


def main(config_path, pack_path, first):
    config = json.load(open(config_path))
    pack = json.load(open(pack_path))
    bounds = pack["bounds"]
    lat0 = (bounds["south"] + bounds["north"]) / 2
    walk = pack["walkways"]
    # Always keep starts inside the bounds: the walkability grid treats anything outside as blocked.
    network, clear = build_pack.start_network(pack["buildings"], walk["nodes"], walk["edges"], lat0, bounds)
    by_id = {b["id"]: b for b in pack["buildings"]}
    centres = [[by_id[l["building"]]["lon"], by_id[l["building"]]["lat"]] for l in pack["landmarks"]]
    moved = 0
    for date in sorted(d for d in pack["daily"] if d >= first):
        puzzle = pack["daily"][date]
        zone = puzzle["zone"]
        ring = [p for p in network if build_pack.MIN_START <= build_pack.metres(p, zone, lat0) <= build_pack.MAX_START]
        starts = build_pack.on_campus(ring, centres, lat0)
        if not starts or puzzle["start"] in starts:
            continue
        rng = random.Random(int(hashlib.sha256((config["id"] + date + "campus-start").encode()).hexdigest(), 16))
        rng.shuffle(starts)
        start = next((p for p in starts[:200] if clear(p)), None)
        if start:
            puzzle["start"] = start
            moved += 1
    with open(pack_path, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{pack['id']}: {moved} daily starts moved onto campus from {first}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
