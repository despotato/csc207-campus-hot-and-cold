#!/usr/bin/env python3
"""Pick the start-of-round clue views for a campus pack, ahead of time.

At the start of a round the game shows a street view of an ordinary building inside the hidden zone
(ZoneClue.building; never a landmark, which would give the zone away). This script runs once per campus (by a developer) and lists one view per region of the campus in the pack
as "cluePhotos", then fills upcoming daily zones that still have none.

The campus is split into square regions (60 m by default). Each region gets one view: its tallest ordinary building
(not a landmark, 200 m² or more) that street imagery faces from close by. A 90 m zone covers several regions, so most
zones have a view.

Sources (--source):
  google (default): Google Street View. This script only calls the Street View *metadata* endpoint, which Google
    doesn't charge for and which uses no quota, to find the nearest panorama and aim the camera at the building.
    It stores the panorama ID and camera angle, never an image (Google's terms don't allow storing them). Players'
    browsers then show it through the Maps Embed API, which Google provides at no charge with unlimited use. With
    the keys restricted as in docs/CLUE_PHOTOS.md, neither step can ever be billed.
  mapillary: open CC BY-SA photos (faces and plates blurred), downloaded, cropped around the building and stored
    next to the packs (clues/<campus>/<building>.jpg).

Keys come from the environment or from ~/.config/campus-hot-and-cold/keys.env (outside the repo), one per line:
    GOOGLE_METADATA_KEY=AIza...
    MAPILLARY_TOKEN=MLY|...

Usage:
    python3 tools/fetch_clue_photos.py src/main/resources/campus/packs/st-george.json
    pip install pillow certifi; python3 tools/fetch_clue_photos.py --source mapillary <pack>
Options: --region 60 (metres), --refresh (redo regions that already have a view; by default they're kept, so views
players have seen don't change).
"""
import argparse
import io
import json
import math
import os
import pathlib
import sys
import time
import urllib.parse
import urllib.request

try:   # python.org builds on macOS ship without CA certificates; certifi supplies them
    import certifi
    import ssl
    urllib.request.install_opener(urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=certifi.where()))))
except ImportError:
    pass
try:
    from PIL import Image   # only for --source mapillary
except ImportError:
    Image = None

API = "https://graph.mapillary.com/images"
SV_METADATA = "https://maps.googleapis.com/maps/api/streetview/metadata"
FIELDS = "id,thumb_2048_url,computed_compass_angle,compass_angle,computed_geometry,geometry,is_pano,creator,captured_at"
M_PER_DEG = 111320
MIN_HEIGHT = 4          # metres; most buildings have no height in OpenStreetMap and default to about 5 m
MIN_FOOTPRINT = 200     # square metres: a real building, not a shed or a kiosk
NEAR_LANDMARKS = 180    # metres: zones always surround a landmark, so photos further out are never used
OUT_W, OUT_H = 640, 400
PHOTO_FOV = 70          # degrees: typical horizontal view of a phone or dashcam photo


def local(lat0):
    k = M_PER_DEG * math.cos(math.radians(lat0))
    return lambda lon, lat: ((lon - LON0) * k, (lat - lat0) * M_PER_DEG)


def bearing(a, b):
    """Degrees from a to b, both (lon, lat)."""
    dx = (b[0] - a[0]) * math.cos(math.radians(a[1]))
    return (math.degrees(math.atan2(dx, b[1] - a[1])) + 360) % 360


def metres(a, b):
    dx = (b[0] - a[0]) * M_PER_DEG * math.cos(math.radians(a[1]))
    return math.hypot(dx, (b[1] - a[1]) * M_PER_DEG)


def mapillary(token, west, south, east, north):
    query = urllib.parse.urlencode({"access_token": token, "fields": FIELDS, "limit": 2000,
                                    "bbox": f"{west:.6f},{south:.6f},{east:.6f},{north:.6f}"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(f"{API}?{query}", timeout=60) as response:
                return json.load(response).get("data", [])
        except Exception as error:  # network hiccup or rate limit: back off and retry
            if attempt == 3:
                print(f"  ! Mapillary request failed: {error}", file=sys.stderr)
                return []
            time.sleep(2 ** attempt)


def read_keys():
    """Environment first, then ~/.config/campus-hot-and-cold/keys.env (NAME=value lines; values may contain |)."""
    keys = {}
    path = pathlib.Path.home() / ".config" / "campus-hot-and-cold" / "keys.env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            name, eq, value = line.partition("=")
            if eq and not name.strip().startswith("#"):
                keys[name.strip()] = value.strip().strip('"').strip("'")
    keys.update({k: v for k, v in os.environ.items() if k in ("GOOGLE_METADATA_KEY", "MAPILLARY_TOKEN") and v})
    return keys


def street_metadata(key, lon, lat):
    query = urllib.parse.urlencode({"location": f"{lat},{lon}", "radius": 50, "source": "outdoor", "key": key})
    try:
        with urllib.request.urlopen(f"{SV_METADATA}?{query}", timeout=30) as response:
            meta = json.load(response)
    except Exception as error:
        print(f"  ! metadata request failed: {error}", file=sys.stderr)
        return None
    if meta.get("status") in ("REQUEST_DENIED", "OVER_QUERY_LIMIT", "INVALID_REQUEST"):
        sys.exit(f"Street View metadata: {meta.get('status')} {meta.get('error_message', '')}")
    return meta if meta.get("status") == "OK" else None


def google_view(key, building):
    """
    Aim at the building from a nearby outdoor Street View panorama (metadata only: free, no quota). Google's own
    captures are preferred over photospheres uploaded by the public: they look consistent and are taken from streets.
    Looks from the building and from 30 m out on each side, then takes the best: Google's, about 25 m away.
    """
    centre = (building["lon"], building["lat"])
    dlat = 30 / M_PER_DEG
    dlon = dlat / math.cos(math.radians(building["lat"]))
    best, seen = None, set()
    for lon, lat in [centre, (centre[0], centre[1] + dlat), (centre[0] + dlon, centre[1]),
                     (centre[0], centre[1] - dlat), (centre[0] - dlon, centre[1])]:
        meta = street_metadata(key, lon, lat)
        if not meta or meta["pano_id"] in seen:
            continue
        seen.add(meta["pano_id"])
        at = (meta["location"]["lng"], meta["location"]["lat"])
        distance = metres(at, centre)
        if not 8 <= distance <= 60:
            continue
        by_google = "google" in (meta.get("copyright") or "").lower()
        score = abs(distance - 25) + (0 if by_google else 1000)
        if best is None or score < best[0]:
            best = (score, meta, at, distance)
        if by_google and abs(distance - 25) < 10:
            break   # good enough: save calls
    if best is None:
        return None
    _, meta, at, distance = best
    pitch = math.degrees(math.atan((building["height"] * 0.45 - 2.5) / distance))
    return {"building": building["id"], "pano": meta["pano_id"], "location": [round(at[0], 7), round(at[1], 7)],
            "heading": round(bearing(at, centre)), "pitch": round(min(30, max(0, pitch))),
            "fov": round(min(55, max(20, 2 * math.degrees(math.atan(7 / distance))))),
            "taken": meta.get("date")}


def footprint(building, lat0):
    """Ground area in square metres (outer rings of the outline)."""
    k = M_PER_DEG * math.cos(math.radians(lat0))
    area = 0.0
    for polygon in building.get("outline") or []:
        ring = polygon[0] if polygon else []
        area += abs(sum((a[0] * k) * (b[1] * M_PER_DEG) - (b[0] * k) * (a[1] * M_PER_DEG)
                        for a, b in zip(ring, ring[1:] + ring[:1]))) / 2
    return area


def best_view(building, images):
    """The photo that shows this building best: 12-60 m away, facing it (any panorama faces everything)."""
    centre = (building["lon"], building["lat"])
    best = None
    for image in images:
        at = (image.get("computed_geometry") or image.get("geometry") or {}).get("coordinates")
        compass = image.get("computed_compass_angle", image.get("compass_angle"))
        if not at or compass is None or not image.get("thumb_2048_url"):
            continue
        distance = metres(at, centre)
        if not 12 <= distance <= 60:
            continue
        off = (bearing(at, centre) - compass + 540) % 360 - 180
        if not image.get("is_pano") and abs(off) > 20:
            continue
        score = abs(distance - 28) + (0 if image.get("is_pano") else abs(off) * 0.5)
        score -= (image.get("captured_at") or 0) / 1000 / 86400 / 365 * 2   # newer photos look like today's campus
        if best is None or score < best[0]:
            best = (score, image, off, distance)
    return best


def crop(photo, pano, off, distance):
    """A tight 16:10 crop around the building, so the photo hints without showing the whole street."""
    width, height = photo.size
    view = max(2 * math.degrees(math.atan(10 / distance)), 24)   # degrees across the crop
    if pano:   # equirectangular: 360° across
        centre_x = width * ((off / 360 + 0.5) % 1)
        crop_w = width * view / 360
        shift = int(width / 2 - centre_x)                # roll so the building sits mid-image (no wrap at the edges)
        rolled = Image.new(photo.mode, (width, height))
        rolled.paste(photo, (shift, 0))
        rolled.paste(photo, (shift - width if shift > 0 else shift + width, 0))
        photo, centre_x = rolled, width / 2
        centre_y = height * 0.42   # a little above the horizon: walls, not pavement
    else:
        centre_x = width * (0.5 + off / PHOTO_FOV)
        crop_w = min(width * 0.65, max(width * 0.45, width * view / PHOTO_FOV))
        centre_y = height * 0.36   # street photos put the road in the lower half
    crop_h = crop_w * OUT_H / OUT_W
    left = min(max(0, centre_x - crop_w / 2), width - crop_w)
    top = min(max(0, centre_y - crop_h / 2), height - crop_h)
    box = tuple(round(v) for v in (left, top, left + crop_w, top + crop_h))
    return photo.crop(box).resize((OUT_W, OUT_H), Image.LANCZOS)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pack")
    parser.add_argument("--region", type=float, default=60)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--source", choices=["google", "mapillary"], default="google")
    args = parser.parse_args()
    google = args.source == "google"
    token = read_keys().get("GOOGLE_METADATA_KEY" if google else "MAPILLARY_TOKEN")
    if not token:
        sys.exit("Set GOOGLE_METADATA_KEY (see docs/CLUE_PHOTOS.md)." if google
                 else "Set MAPILLARY_TOKEN (a Mapillary client token: mapillary.com/dashboard/developers).")
    if not google and Image is None:
        sys.exit("pip install pillow")

    pack_path = pathlib.Path(args.pack)
    pack = json.loads(pack_path.read_text(encoding="utf-8"))
    campus = pack["id"]
    out_dir = pack_path.parent / "clues" / campus
    out_dir.mkdir(parents=True, exist_ok=True)

    global LON0
    lat0 = (pack["bounds"]["north"] + pack["bounds"]["south"]) / 2
    LON0 = (pack["bounds"]["west"] + pack["bounds"]["east"]) / 2
    to_xy = local(lat0)
    by_id = {b["id"]: b for b in pack["buildings"]}
    landmark_ids = {l["building"] for l in pack["landmarks"]}
    landmark_xy = [to_xy(by_id[i]["lon"], by_id[i]["lat"]) for i in landmark_ids if i in by_id]

    def near_campus(b):
        x, y = to_xy(b["lon"], b["lat"])
        return any(math.hypot(x - lx, y - ly) <= NEAR_LANDMARKS for lx, ly in landmark_xy)

    regions = {}
    for b in pack["buildings"]:
        if (b["id"] in landmark_ids or b.get("flat") or (b.get("height") or 0) < MIN_HEIGHT
                or not near_campus(b)):
            continue
        b["_size"] = footprint(b, lat0)
        if b["_size"] < MIN_FOOTPRINT:
            continue
        x, y = to_xy(b["lon"], b["lat"])
        regions.setdefault((math.floor(x / args.region), math.floor(y / args.region)), []).append(b)

    kept = {p["building"]: p for p in pack.get("cluePhotos", [])
            if p["building"] in by_id and p["building"] not in landmark_ids
            and (p.get("pano") or (out_dir / f"{p['building']}.jpg").exists())}
    photos = {} if args.refresh else dict(kept)   # building id -> entry; existing views stay unless --refresh

    def mapillary_entry(b, images):
        view = best_view(b, images)
        if not view:
            return None
        _, image, off, distance = view
        try:
            with urllib.request.urlopen(image["thumb_2048_url"], timeout=60) as response:
                original = Image.open(io.BytesIO(response.read())).convert("RGB")
        except Exception as error:
            print(f"  ! download failed for building {b['id']}: {error}", file=sys.stderr)
            return None
        crop(original, bool(image.get("is_pano")), off, distance).save(
            out_dir / f"{b['id']}.jpg", "JPEG", quality=80, optimize=True, progressive=True)
        author = (image.get("creator") or {}).get("username") or "a Mapillary contributor"
        taken = time.strftime("%Y", time.gmtime(image["captured_at"] / 1000)) if image.get("captured_at") else None
        return {"building": b["id"], "file": f"clues/{campus}/{b['id']}.jpg",
                "credit": f"© {author}" + (f", {taken}" if taken else "") + " · Mapillary · CC BY-SA 4.0 (cropped)",
                "source": f"https://www.mapillary.com/app/?pKey={image['id']}"}

    def first_view(candidates):
        """A view of the first candidate that has one (candidates are tried biggest first)."""
        images = None
        if not google and candidates:
            pad_lat = 130 / M_PER_DEG
            pad_lon = pad_lat / math.cos(math.radians(lat0))
            cx = sum(b["lon"] for b in candidates) / len(candidates)
            cy = sum(b["lat"] for b in candidates) / len(candidates)
            images = mapillary(token, cx - pad_lon, cy - pad_lat, cx + pad_lon, cy + pad_lat)
            time.sleep(0.2)
        for b in candidates:
            entry = google_view(token, b) if google else mapillary_entry(b, images)
            if entry:
                photos[b["id"]] = entry
                return entry
        return None

    biggest_first = lambda bs: sorted(bs, key=lambda m: (-m["_size"] * m["height"], m["id"]))
    for n, (key, members) in enumerate(sorted(regions.items()), 1):
        if not any(b["id"] in photos for b in members):
            first_view(biggest_first(members))
        print(f"\r{campus}: region {n}/{len(regions)}, {len(photos)} views", end="", flush=True)
    print()

    # Daily zones still without a view: try every ordinary building inside, small ones too (sparse campuses).
    today = time.strftime("%Y-%m-%d")
    def zone_xy(puzzle):
        return to_xy(*puzzle["zone"]) if isinstance(puzzle["zone"], list) else to_xy(
            puzzle["zone"]["lon"], puzzle["zone"]["lat"])
    def inside(b, xy, radius=80):
        bx, by_ = to_xy(b["lon"], b["lat"])
        return math.hypot(bx - xy[0], by_ - xy[1]) <= radius

    def clue_candidates(puzzle):
        """The photographable part of ZoneClue.candidates: ordinary buildings 10 m in, else anywhere in the zone."""
        xy = zone_xy(puzzle)
        ordinary = [b for b in pack["buildings"] if b["id"] not in landmark_ids]
        return [b for b in ordinary if inside(b, xy)] or [b for b in ordinary if inside(b, xy, 90)]

    tried = set()
    for date, puzzle in sorted(pack.get("daily", {}).items()):
        if date < today:
            continue
        pool = clue_candidates(puzzle)
        if any(b["id"] in photos for b in pool):
            continue
        candidates = [b for b in pool if b["id"] not in tried and not b.get("flat")]
        for b in candidates:
            b.setdefault("_size", footprint(b, lat0))
            b["_height"] = b.get("height") or 0
        tried.update(b["id"] for b in candidates)
        if first_view(sorted(candidates, key=lambda m: (-m["_size"] * max(m["_height"], 1), m["id"]))):
            print(f"\r{campus}: daily fill, {len(photos)} views", end="", flush=True)
    print()
    for b in pack["buildings"]:
        b.pop("_height", None)
    photos = list(photos.values())

    used = {p["building"] for p in photos}
    stored = {p["building"] for p in photos if p.get("file")}
    for stale in out_dir.glob("*.jpg"):   # photos of regions that no longer qualify
        if int(stale.stem) not in stored:
            stale.unlink()
    for b in pack["buildings"]:
        b.pop("_size", None)
    pack["cluePhotos"] = sorted(photos, key=lambda p: p["building"])
    with pack_path.open("w", encoding="utf-8") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)

    # How many upcoming dailies will show a photo (same choice as Expedition.clueBuilding).
    upcoming = [p for d, p in pack.get("daily", {}).items() if d >= today]
    def has_clue(puzzle):
        return any(b["id"] in used for b in clue_candidates(puzzle))
    covered = sum(1 for p in upcoming if has_clue(p))
    size = sum(f.stat().st_size for f in out_dir.glob("*.jpg")) / 1e6
    for folder in (out_dir, out_dir.parent):   # no stored photos: leave no empty folders behind
        if folder.exists() and not any(folder.iterdir()):
            folder.rmdir()
    print(f"{campus}: {len(photos)} views ({size:.1f} MB stored) for {len(regions)} regions; "
          f"{covered}/{len(upcoming)} upcoming dailies have a photo")


if __name__ == "__main__":
    main()
