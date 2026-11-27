#!/usr/bin/env python3
"""Gives every unnamed building in a campus pack something to be called, changing nothing else.

Most OpenStreetMap buildings have no `name`, so hovering one in the guess phase used to say "Unnamed building".
This script fetches the campus area from Overpass (cached outside the repo) and adds, to buildings without a name:

  label    a name-like label: another OSM name tag (official/alt/short/local, house name), else the name of a
           business or service mapped inside the building (e.g. "Tim Hortons").
  address  where it is: "123 Street Name" (building or address-point tags), else "House on Street Name" from the
           building's OSM type and the nearest named street, else "Shed near Hart House".

The page shows name, else label, else address. `name` itself is untouched (zone and daily rules count real names),
and so are IDs, outlines, dailies and targets. Former names are never used: they are the zone clue's history.

Buildings are matched to OSM by their footprint centre (computed exactly as build_pack does); address points and
businesses are matched by falling inside the pack's own outline, so they work even where OSM has changed.

Usage: python3 -I tools/apply_building_labels.py src/main/resources/campus/packs/<id>.json [--refresh]
"""
import argparse
import importlib.util
import json
import math
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

try:   # python.org builds on macOS ship without CA certificates; certifi supplies them
    import certifi
    import ssl
    urllib.request.install_opener(urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=certifi.where()))))
except ImportError:
    pass

_spec = importlib.util.spec_from_file_location("build_pack", pathlib.Path(__file__).with_name("build_pack.py"))
build_pack = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_pack)

ENDPOINTS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
CACHE = pathlib.Path.home() / ".cache" / "campus-hot-and-cold" / "osm"
NAME_TAGS = ("official_name", "alt_name", "short_name", "loc_name", "name:en", "name:fr", "addr:housename")
POI_KEYS = ("amenity", "shop", "office", "tourism", "leisure", "craft", "healthcare", "club")
NOT_A_PLACE = {"atm", "vending_machine", "bench", "bicycle_parking", "parking_entrance", "post_box", "telephone",
               "waste_basket", "drinking_water", "charging_station", "bicycle_rental", "recycling", "parking",
               "motorcycle_parking", "fountain", "clock", "shelter", "toilets", "letter_box", "ticket_validator"}
STREETS = {"primary", "secondary", "tertiary", "residential", "unclassified", "living_street", "pedestrian",
           "service", "trunk", "primary_link", "secondary_link", "tertiary_link"}
STREET_REACH = 80   # metres: further than this from any named street, the address names a nearby building instead
NAMED_REACH = 400
KINDS = {"house": "House", "detached": "House", "semidetached_house": "House", "terrace": "Row houses",
         "bungalow": "House", "apartments": "Apartment building", "residential": "Residential building",
         "dormitory": "Residence", "garage": "Garage", "garages": "Garages", "carport": "Carport", "shed": "Shed",
         "church": "Church", "chapel": "Chapel", "cathedral": "Cathedral", "mosque": "Mosque",
         "synagogue": "Synagogue", "temple": "Temple", "commercial": "Commercial building", "retail": "Shops",
         "office": "Office building", "university": "University building", "college": "College building",
         "school": "School", "kindergarten": "Kindergarten", "hospital": "Hospital building", "parking": "Parking garage",
         "industrial": "Industrial building", "warehouse": "Warehouse", "greenhouse": "Greenhouse", "kiosk": "Kiosk",
         "hut": "Hut", "public": "Public building", "civic": "Civic building", "government": "Government building",
         "hotel": "Hotel", "train_station": "Station", "transportation": "Transit building", "service": "Service building",
         "sports_centre": "Sports building", "sports_hall": "Sports hall", "stadium": "Stadium",
         "grandstand": "Grandstand", "pavilion": "Pavilion", "boathouse": "Boathouse", "barn": "Barn",
         "farm_auxiliary": "Farm building", "stable": "Stable", "storage_tank": "Storage tank",
         "water_tower": "Water tower", "bunker": "Bunker", "toilets": "Washrooms", "museum": "Museum",
         "library": "Library building", "fire_station": "Fire station", "theatre": "Theatre"}


def overpass(query):
    data = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(6):
        url = ENDPOINTS[attempt % len(ENDPOINTS)]
        request = urllib.request.Request(url, data=data, headers={"User-Agent": "campus-pack-builder/1.0"})
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:   # busy (429/504) or a dropped connection: back off, other server
            if "CERTIFICATE" in str(error):
                sys.exit(f"{error}: pip install certifi")
            wait = 20 * (attempt + 1)
            print(f"  overpass: {error}; retrying in {wait} s", file=sys.stderr)
            time.sleep(wait)
    sys.exit("Overpass is unavailable; try again later")


def export(pack, refresh):
    path = CACHE / f"{pack['id']}-labels.json"
    if path.exists() and not refresh:
        return json.loads(path.read_text(encoding="utf-8"))
    b = pack["bounds"]
    box = f"{b['south']},{b['west']},{b['north']},{b['east']}"
    pois = "".join(f'node({box})["{k}"][name];' for k in POI_KEYS)
    query = (f'[out:json][timeout:240];(way({box})[building];relation({box})[building];)->.b;.b out geom;'
             f'({pois}node({box})["addr:housenumber"];)->.p;.p out;'
             f'way({box})[highway][name];out tags geom;')
    data = overpass(query)
    CACHE.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data), encoding="utf-8")
    return data


def address(tags):
    number, street = tags.get("addr:housenumber"), tags.get("addr:street")
    if not number or not street:
        return None
    return f"{number.split(';')[0].strip()} {street}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pack")
    parser.add_argument("--refresh", action="store_true", help="fetch OSM again instead of using the cache")
    args = parser.parse_args()
    pack_path = pathlib.Path(args.pack)
    pack = json.loads(pack_path.read_text(encoding="utf-8"))
    elements = export(pack, args.refresh)["elements"]

    lat0 = (pack["bounds"]["north"] + pack["bounds"]["south"]) / 2
    my = math.pi * 6371000 / 180
    mx = my * math.cos(math.radians(lat0))

    def xy(lon, lat):
        return (lon * mx, lat * my)

    # OSM buildings by footprint centre, on a 5 m grid.
    osm = {}
    for e in elements:
        if e["type"] == "node" or "building" not in e.get("tags", {}):
            continue
        shape = build_pack.footprint(e, False)
        if shape is None:
            continue
        lat, lon = build_pack.centre(shape["outline"])
        x, y = xy(lon, lat)
        osm.setdefault((round(x / 5), round(y / 5)), []).append((x, y, e["tags"]))

    def osm_tags(b):
        x, y = xy(b["lon"], b["lat"])
        best = None
        for gx in range(round(x / 5) - 1, round(x / 5) + 2):
            for gy in range(round(y / 5) - 1, round(y / 5) + 2):
                for ox, oy, tags in osm.get((gx, gy), []):
                    d = math.hypot(ox - x, oy - y)
                    if d <= 1.5 and (best is None or d < best[0]):
                        best = (d, tags)
        return best[1] if best else {}

    points = [e for e in elements if e["type"] == "node"]
    streets = []
    for e in elements:
        tags = e.get("tags", {})
        if e["type"] == "way" and tags.get("highway") in STREETS and tags.get("name") and "geometry" in e:
            line = [xy(p["lon"], p["lat"]) for p in e["geometry"]]
            streets.append((tags["name"], line))

    def nearest_street(b):
        px, py = xy(b["lon"], b["lat"])
        best = (STREET_REACH, None)
        for name, line in streets:
            for (ax, ay), (bx, by) in zip(line, line[1:]):
                dx, dy = bx - ax, by - ay
                t = 0 if dx == dy == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
                d = math.hypot(px - ax - t * dx, py - ay - t * dy)
                if d < best[0]:
                    best = (d, name)
        return best[1]

    named_xy = [(xy(n["lon"], n["lat"]), n["name"]) for n in pack["buildings"] if n.get("name")]

    def nearest_named(b):
        px, py = xy(b["lon"], b["lat"])
        d, name = min(((math.hypot(x - px, y - py), name) for (x, y), name in named_xy), default=(math.inf, None))
        return name if d <= NAMED_REACH else None

    def inside_outline(b, lon, lat):
        return any(build_pack.inside((lon, lat), [tuple(p) for p in polygon[0]]) for polygon in b["outline"])

    # Points grouped per building, by a coarse grid around each outline's bounding box.
    grid = {}
    for p in points:
        grid.setdefault((round(p["lon"] * 2000), round(p["lat"] * 2000)), []).append(p)

    def points_in(b):
        lons = [q[0] for polygon in b["outline"] for q in polygon[0]]
        lats = [q[1] for polygon in b["outline"] for q in polygon[0]]
        found = []
        for gx in range(round(min(lons) * 2000) - 1, round(max(lons) * 2000) + 2):
            for gy in range(round(min(lats) * 2000) - 1, round(max(lats) * 2000) + 2):
                found += [p for p in grid.get((gx, gy), []) if min(lons) <= p["lon"] <= max(lons)
                          and min(lats) <= p["lat"] <= max(lats) and inside_outline(b, p["lon"], p["lat"])]
        return found

    landmark_names = {l["name"].lower() for l in pack["landmarks"]}
    counts = {"label": 0, "address": 0, "street kind": 0, "near a named building": 0, "kind only": 0}
    for b in pack["buildings"]:
        b.pop("label", None)
        b.pop("address", None)
        if b.get("name"):
            continue
        tags = osm_tags(b)
        inner = points_in(b)
        label = next((tags[t] for t in NAME_TAGS if tags.get(t)), None)
        if not label:
            cx, cy = xy(b["lon"], b["lat"])
            places = sorted((p for p in inner if p.get("tags", {}).get("name") and any(
                p["tags"].get(k) and p["tags"].get(k) not in NOT_A_PLACE for k in POI_KEYS)),
                key=lambda p: (math.hypot(xy(p["lon"], p["lat"])[0] - cx, xy(p["lon"], p["lat"])[1] - cy), p["id"]))
            label = places[0]["tags"]["name"] if places else None
        if label and label.lower() not in landmark_names:
            b["label"] = label
            counts["label"] += 1
        where = address(tags) or next((address(p.get("tags", {})) for p in sorted(inner, key=lambda p: p["id"])
                                       if address(p.get("tags", {}))), None)
        if where:
            counts["address"] += 1
        else:
            kind = KINDS.get(tags.get("building"), "Building")
            street = nearest_street(b)
            near = None if street else nearest_named(b)
            where = f"{kind} on {street}" if street else f"{kind} near {near}" if near else kind
            counts["street kind" if street else "near a named building" if near else "kind only"] += 1
        b["address"] = where
    pack_path.write_text(json.dumps(pack, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
    unnamed = sum(1 for b in pack["buildings"] if not b.get("name"))
    print(f"{pack['id']}: {unnamed} unnamed buildings: {counts}")


if __name__ == "__main__":
    main()
