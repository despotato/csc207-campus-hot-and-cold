"""Builds the St. George campus pack from an Overpass dump.

Usage: python3 -I build_pack.py area.json out.json
Landmarks are hand-picked below by OSM element; everything else is generated.
"""
import json
import math
import sys

# (osm element, display name, category, wikipedia title or None)
LANDMARKS = [
    # Libraries
    ("w7991747", "Robarts Library", "Library", "Robarts Library"),
    ("r14756874", "Gerstein Science Information Centre", "Library", "Gerstein Science Information Centre"),
    ("w9916090", "Thomas Fisher Rare Book Library", "Library", "Thomas Fisher Rare Book Library"),
    ("w8111900", "E. J. Pratt Library", "Library", None),
    ("w8027952", "John M. Kelly Library", "Library", None),
    ("w12000627", "Bora Laskin Law Library", "Library", None),
    # Residences
    ("w7685383", "Wetmore Hall", "Residence", None),
    ("w10336058", "Wilson Hall", "Residence", None),
    ("w8068859", "New College Residence (45 Willcocks)", "Residence", None),
    ("w8027962", "Sir Daniel Wilson Residence", "Residence", None),
    ("w12873850", "Burwash Hall", "Residence", "Burwash Hall"),
    ("w12873822", "Margaret Addison Hall", "Residence", None),
    ("w23436623", "Annesley Hall", "Residence", "Annesley Hall"),
    ("w12874392", "Whitney Hall", "Residence", None),
    ("w12874330", "Morrison Hall", "Residence", None),
    ("r73341", "Graduate House", "Residence", "Graduate House (University of Toronto)"),
    ("w20898043", "Innis Residence", "Residence", None),
    ("w10336057", "Woodsworth College Residence", "Residence", None),
    ("w23435125", "Elmsley Hall", "Residence", None),
    ("w15401950", "Sorbara Hall", "Residence", None),
    ("w23433444", "St. Hilda's College", "Residence", "St. Hilda's College, University of Toronto"),
    ("w23436644", "Loretto College", "Residence", None),
    ("w23436633", "Rowell Jackman Hall", "Residence", None),
    ("w1383202632", "Oak House", "Residence", None),
    # Colleges
    ("w8032167", "University College", "College", "University College, Toronto"),
    ("r2687", "Trinity College", "College", "Trinity College, Toronto"),
    ("w204323029", "Victoria College", "College", "Victoria University, Toronto"),
    ("w22372215", "Innis College", "College", "Innis College, Toronto"),
    ("r2032", "Knox College", "College", "Knox College, Toronto"),
    ("r4323", "Massey College", "College", "Massey College, Toronto"),
    ("r47346", "Wycliffe College", "College", "Wycliffe College, Toronto"),
    ("w204323317", "Emmanuel College", "College", "Emmanuel College, Toronto"),
    ("w60303738", "Regis College", "College", "Regis College (Toronto)"),
    ("w27750952", "Woodsworth College (Kruger Hall)", "College", None),
    # Teaching and research
    ("w141691917", "Bahen Centre for Information Technology", "Teaching", "Bahen Centre for Information Technology"),
    ("w7948423", "Sidney Smith Hall", "Teaching", None),
    ("w403302492", "Myhal Centre", "Teaching", None),
    ("w988615299", "Medical Sciences Building", "Teaching", None),
    ("w1466900017", "Lash Miller Chemical Laboratories", "Teaching", None),
    ("w1428103037", "McLennan Physical Laboratories", "Teaching", None),
    ("w13403180", "Sandford Fleming Building", "Teaching", None),
    ("r76177", "Galbraith Building", "Teaching", None),
    ("w1464898602", "Wallberg Building", "Teaching", None),
    ("w354180331", "Mechanical Engineering Building", "Teaching", None),
    ("w12873543", "Lassonde Mining Building", "Teaching", None),
    ("r2152", "Earth Sciences Centre", "Teaching", None),
    ("w9303310", "Ramsay Wright Building", "Teaching", None),
    ("w12873582", "Leslie Dan Pharmacy Building", "Teaching", "Leslie Dan Faculty of Pharmacy"),
    ("w12000960", "Rotman School of Management", "Teaching", None),
    ("w676618964", "Jackman Law Building", "Teaching", None),
    ("w8101762", "Daniels Building (One Spadina)", "Teaching", "1 Spadina Crescent"),
    ("w12000624", "Edward Johnson Building", "Teaching", None),
    ("w10725629", "OISE", "Teaching", "Ontario Institute for Studies in Education"),
    ("w23436801", "Health Sciences Building", "Teaching", None),
    ("w12873396", "Donnelly Centre", "Teaching", None),
    ("w23433584", "C. David Naylor Building", "Teaching", None),
    ("w23201562", "Jackman Humanities Building", "Teaching", None),
    ("w8117208", "Northrop Frye Hall", "Teaching", None),
    ("w8810864", "Carr Hall", "Teaching", None),
    ("w10334995", "Claude T. Bissell Building", "Teaching", None),
    ("w22372209", "Max Gluskin House", "Teaching", None),
    ("w1461363247", "Schwartz Reisman Innovation Campus", "Teaching", None),
    ("w9916087", "Clara Benson Building", "Athletics", None),
    ("w23113405", "Gerald Larkin Building", "Teaching", None),
    # Campus life and landmarks
    ("r1989", "Hart House", "Campus life", "Hart House (University of Toronto)"),
    ("w330718925", "Convocation Hall", "Campus life", "Convocation Hall (University of Toronto)"),
    ("w10336055", "Simcoe Hall", "Campus life", None),
    ("w28822997", "Soldiers' Tower", "Campus life", "Soldiers' Tower"),
    ("w8099717", "Koffler Student Services Centre", "Campus life", "Koffler Student Centre"),
    ("w12872719", "Student Commons", "Campus life", None),
    ("w12873892", "Isabel Bader Theatre", "Campus life", None),
    ("w27291427", "George Ignatieff Theatre", "Campus life", None),
    ("w23436928", "Central Exams Facility", "Campus life", None),
    ("w6147259", "Varsity Stadium", "Athletics", "Varsity Stadium"),
    ("w8095834", "Varsity Arena", "Athletics", "Varsity Arena"),
    ("w258167657", "Goldring Centre", "Athletics", "Goldring Centre for High Performance Sport"),
]

# Open-air landmarks highlighted at ground level instead of as a raised block.
FLAT = {"w6147259"}

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


def main(src, dst):
    elements = json.load(open(src))["elements"]
    by_key = {key(e): e for e in elements}
    landmark_keys = {k for k, *_ in LANDMARKS}

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
        shape = footprint(e, k in FLAT)
        if shape is None:
            continue
        lat, lon = centre(shape["outline"])
        building_ids[k] = len(buildings) + 1
        buildings.append({"id": len(buildings) + 1, "name": tags.get("name"), "lat": lat, "lon": lon,
                          "flat": k in FLAT, **shape})

    landmarks = []
    for next_id, (k, name, category, wiki) in enumerate(LANDMARKS, start=1):
        if k not in building_ids:
            sys.exit(f"missing outline for {k} {name}")
        building = buildings[building_ids[k] - 1]
        building["name"] = name
        landmarks.append({"id": next_id, "building": building["id"], "osm": k, "name": name,
                          "category": category, "wikipedia": wiki})

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

    pack = {
        "version": 2,
        "campus": "University of Toronto — St. George",
        "bounds": {"south": 43.6565, "west": -79.4040, "north": 43.6695, "east": -79.3875},
        "attribution": "Building outlines and paths © OpenStreetMap contributors (ODbL)",
        "landmarks": landmarks,
        "buildings": buildings,
        "walkways": {"nodes": coords, "edges": sorted(edges)},
    }
    with open(dst, "w") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)
    print(f"{len(landmarks)} landmarks, {len(buildings)} buildings, "
          f"{len(coords)} walk nodes, {len(edges)} walk edges")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
