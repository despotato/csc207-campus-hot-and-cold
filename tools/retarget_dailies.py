"""Re-picks future daily puzzles whose target has no hint fact or recorded history, leaving every other date alone.

Usage: python3 -I tools/retarget_dailies.py tools/campuses/<id>.json src/main/resources/campus/packs/<id>.json FROM

FROM is the first date (YYYY-MM-DD) that may change; use tomorrow so today's and past puzzles stay exactly as
players saw them. A target qualifies when it has reviewed facts (see tools/apply_facts.py) or OSM history
(former name, year built, architect): the hard hint is then real campus history, never a size clue. Replacement
puzzles come from the same deterministic generator as tools/build_pack.py, limited to qualifying landmarks.
"""
import datetime
import importlib.util
import json
import pathlib
import sys

_spec = importlib.util.spec_from_file_location("build_pack", pathlib.Path(__file__).with_name("build_pack.py"))
build_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_pack)


def main(config_path, pack_path, first):
    config = json.load(open(config_path))
    pack = json.load(open(pack_path))
    buildings = {b["id"]: b for b in pack["buildings"]}

    def qualifies(landmark):
        b = buildings[landmark["building"]]
        return bool(landmark.get("facts") or b.get("formerName") or b.get("built") or b.get("architect"))
    landmarks = {l["id"]: l for l in pack["landmarks"]}
    good = [l for l in pack["landmarks"] if qualifies(l)]
    stale = sorted(d for d, p in pack["daily"].items() if d >= first and not qualifies(landmarks[p["landmark"]]))
    if not stale:
        print("0 dailies retargeted")
        return
    bounds = pack["bounds"]
    lat0 = (bounds["south"] + bounds["north"]) / 2
    walk = pack["walkways"]
    start_bounds = bounds if config.get("dailyStartInsideBounds") else None
    fresh = build_pack.daily_schedule(pack["buildings"], good, walk["nodes"], walk["edges"], lat0,
                                      config["id"] + "-retarget", datetime.date.fromisoformat(stale[0]),
                                      start_bounds, pack["landmarks"])
    missing = [d for d in stale if d not in fresh]
    for date in stale:
        if date in fresh:
            pack["daily"][date] = fresh[date]
    with open(pack_path, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(stale) - len(missing)} dailies retargeted from {stale[0]}")
    if missing:
        sys.exit(f"could not retarget: {', '.join(missing)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
