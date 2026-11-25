"""Builds a campus pack from an Overpass dump and campus configuration.

Usage: python3 -I build_pack.py campus.json area.json out.json [greenery.json]
greenery.json (optional): trees (natural=tree nodes) and lawns/gardens/parks/pitches as an Overpass export.
Landmarks are hand-picked in the campus config; everything else is generated.
"""
import datetime
import hashlib
import json
import math
import random
import sys

# Daily puzzles: the same target, zone and start for every player on a given date.
DAILY_DAYS = 400
ZONE_RADIUS, MIN_START, MAX_START = 90, 450, 900

HIGHWAYS = {"footway", "path", "pedestrian", "steps", "living_street", "residential", "service",
            "tertiary", "secondary", "primary", "unclassified", "cycleway", "corridor"}


def key(e):
    return e["type"][0] + str(e["id"])


def ring_from_way(e):
    return [(p["lon"], p["lat"]) for p in e["geometry"]]


def join_rings(ways):
    """Joins open way segments into closed rings by matching endpoints."""
    segments = [list(w) for w in ways if len(w) >= 2]
    rings = []
    while segments:
        ring = segments.pop(0)
        changed = True
        while ring[0] != ring[-1] and changed:
            changed = False
            for i, s in enumerate(segments):
                if s[0] == ring[-1]:
                    ring += s[1:]
                elif s[-1] == ring[-1]:
                    ring += list(reversed(s))[1:]
                elif s[-1] == ring[0]:
                    ring = s[:-1] + ring
                elif s[0] == ring[0]:
                    ring = list(reversed(s))[:-1] + ring
                else:
                    continue
                segments.pop(i)
                changed = True
                break
        if ring[0] == ring[-1] and len(ring) >= 4:
            rings.append(ring)
    return rings


def member_rings(e, role):
    return join_rings([[(p["lon"], p["lat"]) for p in m["geometry"]] for m in e.get("members", [])
                       if m.get("role") == role and m.get("geometry")])


def outer_rings(e):
    if e["type"] == "way":
        ring = ring_from_way(e)
        return [ring] if len(ring) >= 4 and ring[0] == ring[-1] else []
    return member_rings(e, "outer")


def inside(point, ring):
    x, y = point
    hit = False
    for (x0, y0), (x1, y1) in zip(ring, ring[1:]):
        if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
            hit = not hit
    return hit


def polygons(e):
    """Outer rings, each followed by the courtyards (inner rings) it contains."""
    outers = outer_rings(e)
    result = [[ring] for ring in outers]
    if e["type"] == "relation":
        for hole in member_rings(e, "inner"):
            for polygon in result:
                if inside(hole[0], polygon[0]):
                    polygon.append(hole)
                    break
    return result


def height(tags):
    """Mirrors OpenMapTiles render_height so highlights match the rendered buildings."""
    try:
        return round(float(str(tags["height"]).split()[0]), 1)
    except (KeyError, ValueError):
        pass
    try:
        return round(float(tags["building:levels"]) * 3.66, 1)
    except (KeyError, ValueError):
        return 5.0


def min_height(tags):
    try:
        return round(float(str(tags["min_height"]).split()[0]), 1)
    except (KeyError, ValueError):
        pass
    try:
        return round(float(tags["building:min_level"]) * 3.66, 1)
    except (KeyError, ValueError):
        return 0.0


def area_centroid(ring):
    a = cx = cy = 0.0
    for (x0, y0), (x1, y1) in zip(ring, ring[1:]):
        cross = x0 * y1 - x1 * y0
        a += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    if abs(a) < 1e-15:
        return ring[0]
    return cx / (3 * a), cy / (3 * a), abs(a) / 2


def r6(ring):
    return [[round(x, 6), round(y, 6)] for x, y in ring]


MATERIALS = {"brick": "brick", "stone": "stone", "sandstone": "stone", "limestone": "stone", "granite": "stone",
             "glass": "glass", "metal": "glass", "steel": "glass", "concrete": "concrete", "plaster": "concrete",
             "cement_block": "concrete", "wood": "brick", "timber_framing": "brick"}


def material(tags, building_id, height, category):
    """Facade material: OpenStreetMap's building:material when known, otherwise a plausible guess."""
    real = MATERIALS.get(str(tags.get("building:material", "")).lower())
    if real:
        return real
    h = (building_id * 2654435761) % 2 ** 32
    if height >= 40 or (category == "Teaching" and height >= 25):
        return "glass"
    if category in ("College", "Library", "Campus life"):
        return "stone" if h % 3 == 0 else "brick"
    if category == "Residence":
        return "brick"
    if height >= 18:
        return "concrete" if h % 2 else "brick"
    return ["brick", "stone", "brick", "concrete"][h % 4]


def history(tags):
    """Construction year, architect and former name from OpenStreetMap, where recorded."""
    result = {}
    year = str(tags.get("start_date", ""))[:4]
    if year.isdigit():
        result["built"] = int(year)
    if tags.get("architect"):
        result["architect"] = tags["architect"]
    if tags.get("old_name"):
        result["formerName"] = tags["old_name"]
    return result


def footprint(e, flat):
    shapes = polygons(e)
    return {
        "height": 0.6 if flat else height(e["tags"]),
        "minHeight": 0.0 if flat else min_height(e["tags"]),
        # Each polygon is [outer ring, courtyard rings...] as [lon, lat] pairs.
        "outline": [[r6(r) for r in shape] for shape in shapes],
    } if shapes else None


def centre(outline):
    parts = [area_centroid(polygon[0]) for polygon in outline]
    total = sum(p[2] for p in parts)
    return (round(sum(p[1] * p[2] for p in parts) / total, 7),
            round(sum(p[0] * p[2] for p in parts) / total, 7))


def greenery(src):
    """Trees as points, and green areas (lawn, garden, park, pitch, wood) as polygons, for decoration."""
    trees, greens = [], []
    for e in json.load(open(src))["elements"]:
        tags = e["tags"]
        if e["type"] == "node" and tags.get("natural") == "tree":
            trees.append([round(e["lon"], 6), round(e["lat"], 6)])
            continue
        kind = ("pitch" if tags.get("leisure") == "pitch" else "garden" if tags.get("leisure") == "garden"
                else "wood" if tags.get("natural") in ("wood", "scrub") else "lawn")
        for polygon in polygons(e):
            greens.append({"kind": kind, "outline": [r6(r) for r in polygon]})
    return trees, greens


def metres(a, b, lat0):
    """Planar distance in metres between [lon, lat] points (fine at campus scale)."""
    m_lat = math.pi * 6371000 / 180
    return math.hypot((a[0] - b[0]) * m_lat * math.cos(math.radians(lat0)), (a[1] - b[1]) * m_lat)


def offset(p, bearing, dist, lat0):
    m_lat = math.pi * 6371000 / 180
    r = math.radians(bearing)
    return [p[0] + math.sin(r) * dist / (m_lat * math.cos(math.radians(lat0))), p[1] + math.cos(r) * dist / m_lat]


def daily_schedule(buildings, landmarks, coords, edges, lat0, campus_id, daily_from, start_bounds=None):
    """Deterministic daily puzzles. Starts are footpath points on the main network, clear of buildings."""
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
    solid = [polygon for b in buildings if not b["flat"] for polygon in b["outline"]]

    def clear(p):
        for polygon in solid:
            ring = polygon[0]
            xs = [q[0] for q in ring]
            ys = [q[1] for q in ring]
            if min(xs) - 0.00005 <= p[0] <= max(xs) + 0.00005 and min(ys) - 0.00005 <= p[1] <= max(ys) + 0.00005:
                return False   # within ~4 m of a building: keep starts comfortably outdoors
        return True
    def within_bounds(p):
        if start_bounds is None:
            return True
        # Leave enough room for the Java walkability grid to snap the start outdoors.
        margin_lat = 15 / 111195
        margin_lon = margin_lat / math.cos(math.radians(lat0))
        return (start_bounds["west"] + margin_lon <= p[0] <= start_bounds["east"] - margin_lon
                and start_bounds["south"] + margin_lat <= p[1] <= start_bounds["north"] - margin_lat)

    network = [coords[i] for i in range(len(coords)) if find(i) == main_root and within_bounds(coords[i])]
    by_id = {b["id"]: b for b in buildings}
    schedule = {}
    for day in range(DAILY_DAYS):
        date = (daily_from + datetime.timedelta(days=day)).isoformat()
        rng = random.Random(int(hashlib.sha256((campus_id + date).encode()).hexdigest(), 16))
        for _ in range(500):
            landmark = rng.choice(landmarks)
            centre = [by_id[landmark["building"]]["lon"], by_id[landmark["building"]]["lat"]]
            zone = offset(centre, rng.uniform(0, 360), rng.uniform(0, 50), lat0)
            choices = [b for b in buildings if b["height"] >= 4 and metres([b["lon"], b["lat"]], zone, lat0) <= ZONE_RADIUS]
            if len(choices) < 5 or sum(1 for b in choices if b["name"]) < 3:
                continue
            if not any(metres(p, zone, lat0) <= ZONE_RADIUS - 10 for p in network):
                continue
            starts = [p for p in network if MIN_START <= metres(p, zone, lat0) <= MAX_START]
            rng.shuffle(starts)
            start = next((p for p in starts[:40] if clear(p)), None)
            if start:
                schedule[date] = {"landmark": landmark["id"], "zone": [round(zone[0], 7), round(zone[1], 7)],
                                  "start": start}
                break
    return schedule


def main(config_src, src, dst, green_src=None):
    config = json.load(open(config_src))
    bounds = config["bounds"]
    landmarks_config = config["landmarks"]
    flat = set(config["flat"])
    elements = json.load(open(src))["elements"]
    by_key = {key(e): e for e in elements}
    landmark_keys = {item["osm"] for item in landmarks_config}

    buildings = []
    building_ids = {}
    for e in elements:
        tags = e["tags"]
        k = key(e)
        if k not in landmark_keys:
            if "building" not in tags or tags.get("building") in ("roof", "construction", "train_station"):
                continue
            if tags.get("layer", "0").startswith("-") or tags.get("location") == "underground":
                continue
        shape = footprint(e, k in flat)
        if shape is None:
            continue
        lat, lon = centre(shape["outline"])
        building_ids[k] = len(buildings) + 1
        buildings.append({"id": len(buildings) + 1, "name": tags.get("name"), "lat": lat, "lon": lon,
                          "flat": k in flat, "courtyard": any(len(p) > 1 for p in shape["outline"]),
                          "osmTags": tags, **shape})

    landmarks = []
    for next_id, item in enumerate(landmarks_config, start=1):
        k, name, category, wiki = (item["osm"], item["name"], item["category"], item["wikipedia"])
        if k not in building_ids:
            sys.exit(f"missing outline for {k} {name}")
        building = buildings[building_ids[k] - 1]
        building["name"] = name
        building["category"] = category
        landmarks.append({"id": next_id, "building": building["id"], "osm": k, "name": name,
                          "category": category, "wikipedia": wiki})

    for building in buildings:
        tags = building.pop("osmTags")
        building["material"] = material(tags, building["id"], building["height"], building.pop("category", None))
        building.update(history(tags))

    nodes = {}
    coords = []
    edges = set()
    for e in elements:
        if e["type"] != "way" or e["tags"].get("highway") not in HIGHWAYS:
            continue
        if e["tags"].get("access") in ("private", "no") or e["tags"].get("foot") == "no":
            continue
        previous = None
        for p in e["geometry"]:
            c = (round(p["lon"], 6), round(p["lat"], 6))
            if c not in nodes:
                nodes[c] = len(coords)
                coords.append([c[0], c[1]])
            n = nodes[c]
            if previous is not None and previous != n:
                edges.add((min(previous, n), max(previous, n)))
            previous = n

    lat0 = (bounds["south"] + bounds["north"]) / 2
    pack = {
        "version": 2,
        "id": config["id"],
        "timezone": config["timezone"],
        "campus": config["name"],
        "bounds": bounds,
        "attribution": "Building outlines and paths © OpenStreetMap contributors (ODbL)",
        "mascot": config["mascot"],
        "landmarks": landmarks,
        "buildings": buildings,
        "walkways": {"nodes": coords, "edges": sorted(edges)},
    }
    if green_src:
        pack["trees"], pack["greens"] = greenery(green_src)
    pack["daily"] = daily_schedule(buildings, landmarks, coords, sorted(edges), lat0, config["id"],
                                    datetime.date.fromisoformat(config["dailyFrom"]),
                                    bounds if config.get("dailyStartInsideBounds") else None)
    with open(dst, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(landmarks)} landmarks, {len(buildings)} buildings, "
          f"{len(coords)} walk nodes, {len(edges)} walk edges, {len(pack['daily'])} daily puzzles")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)
