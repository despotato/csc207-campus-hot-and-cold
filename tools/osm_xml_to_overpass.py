"""Convert an OpenStreetMap API .osm bounding-box export for build_pack.py.

Usage: python3 -I tools/osm_xml_to_overpass.py map.osm area.json green.json
The two JSON outputs are temporary Overpass-compatible inputs, kept outside the repo.
"""
import json
import sys
import xml.etree.ElementTree as ET


def tags(element):
    return {tag.get("k"): tag.get("v") for tag in element.findall("tag")}


def main(source, area_out, green_out):
    root = ET.parse(source).getroot()
    nodes = {int(node.get("id")): (float(node.get("lon")), float(node.get("lat")))
             for node in root.findall("node")}
    ways = {int(way.get("id")): way for way in root.findall("way")}

    def geometry(way):
        return [{"lon": nodes[int(nd.get("ref"))][0], "lat": nodes[int(nd.get("ref"))][1]}
                for nd in way.findall("nd") if int(nd.get("ref")) in nodes]

    def as_element(element):
        kind = element.tag
        result = {"type": kind, "id": int(element.get("id")), "tags": tags(element)}
        if kind == "node":
            result["lon"], result["lat"] = nodes[result["id"]]
        elif kind == "way":
            result["geometry"] = geometry(element)
        else:
            result["members"] = [{"type": member.get("type"), "ref": int(member.get("ref")),
                                  "role": member.get("role", ""),
                                  "geometry": geometry(ways[int(member.get("ref"))])}
                                 for member in element.findall("member")
                                 if member.get("type") == "way" and int(member.get("ref")) in ways]
        return result

    area, green = [], []
    for element in root:
        kind = element.tag
        if kind not in ("node", "way", "relation"):
            continue
        t = tags(element)
        if (kind in ("way", "relation") and ("building" in t or t.get("leisure") == "stadium")) or (
                kind == "way" and "highway" in t):
            area.append(as_element(element))
        if (kind == "node" and t.get("natural") == "tree") or (
                kind in ("way", "relation") and (t.get("landuse") in {"grass", "meadow", "village_green"}
                or t.get("leisure") in {"park", "garden", "pitch"}
                or t.get("natural") in {"wood", "scrub"})):
            green.append(as_element(element))
    for path, elements in ((area_out, area), (green_out, green)):
        with open(path, "w") as output:
            json.dump({"elements": elements}, output, separators=(",", ":"))
    print(f"{len(area)} area elements, {len(green)} greenery elements")


if __name__ == "__main__":
    main(*sys.argv[1:])
