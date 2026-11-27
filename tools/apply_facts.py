"""Copies the reviewed hint facts from a campus config into its built pack, changing nothing else.

Usage: python3 -I tools/apply_facts.py tools/campuses/<id>.json src/main/resources/campus/packs/<id>.json

Facts are hand-reviewed sentences about a landmark, best known first (normal hint), then historical (hard hint),
with the building's name left out. They live in the config ("facts" on each landmark) so a rebuild keeps them;
this script applies them without re-fetching OpenStreetMap data, so buildings and daily puzzles stay identical.
"""
import json
import sys


def main(config_path, pack_path):
    config = json.load(open(config_path))
    pack = json.load(open(pack_path))
    facts = {item["osm"]: item.get("facts") or [] for item in config["landmarks"]}
    for landmark in pack["landmarks"]:
        if facts.get(landmark["osm"]):
            landmark["facts"] = facts[landmark["osm"]]
        else:
            landmark.pop("facts", None)
    with open(pack_path, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{sum(1 for l in pack['landmarks'] if l.get('facts'))} of {len(pack['landmarks'])} landmarks have facts")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
