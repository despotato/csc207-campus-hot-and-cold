#!/usr/bin/env python3
"""Pick the start-of-round clue views for a campus pack, ahead of time.

At the start of a round the game shows a street view of an ordinary building inside the hidden zone
(Expedition.clueBuilding). This script runs once per campus (by a developer) and lists one view per region of the
campus in the pack as "cluePhotos".

The campus is split into square regions (60 m by default). Each region gets one view: its tallest ordinary building
(not a landmark, 6 m or taller) that street imagery faces from close by. A 90 m zone covers several regions, so most
zones have a view.

Sources (--source):
  google (default): Google Street View. This script only calls the Street View *metadata* endpoint, which Google
    doesn't charge for and which uses no quota, to find the nearest panorama and aim the camera at the building.
    It stores the panorama ID and camera angle, never an image (Google's terms don't allow storing them). Players'
    browsers then show it through the Maps Embed API, which Google provides at no charge with unlimited use. With
    the keys restricted as in docs/CLUE_PHOTOS.md, neither step can ever be billed.
  mapillary: open CC BY-SA photos (faces and plates blurred), downloaded, cropped around the building and stored
    next to the packs (clues/<campus>/<building>.jpg).

Usage:
    GOOGLE_METADATA_KEY=... python3 tools/fetch_clue_photos.py src/main/resources/campus/packs/st-george.json
    pip install pillow; MAPILLARY_TOKEN=MLY|... python3 tools/fetch_clue_photos.py --source mapillary <pack>
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

try:
    from PIL import Image   # only for --source mapillary
except ImportError:
    Image = None

API = "https://graph.mapillary.com/images"
SV_METADATA = "https://maps.googleapis.com/maps/api/streetview/metadata"
FIELDS = "id,thumb_2048_url,computed_compass_angle,compass_angle,computed_geometry,geometry,is_pano,creator,captured_at"
M_PER_DEG = 111320
MIN_HEIGHT = 6          # metres: a real building, not a shed
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


def google_view(key, building):
    """Aim at the building from the nearest outdoor Street View panorama (metadata only: free, no quota)."""
    query = urllib.parse.urlencode({"location": f"{building['lat']},{building['lon']}", "radius": 50,
                                    "source": "outdoor", "key": key})
    try:
        with urllib.request.urlopen(f"{SV_METADATA}?{query}", timeout=30) as response:
            meta = json.load(response)
    except Exception as error:
        print(f"  ! metadata request failed: {error}", file=sys.stderr)
        return None
    if meta.get("status") != "OK":
        if meta.get("status") in ("REQUEST_DENIED", "OVER_QUERY_LIMIT"):
            sys.exit(f"Street View metadata: {meta.get('status')} {meta.get('error_message', '')}")
        return None
    at = (meta["location"]["lng"], meta["location"]["lat"])
    centre = (building["lon"], building["lat"])
    distance = metres(at, centre)
    if not 8 <= distance <= 60:
        return None
    pitch = math.degrees(math.atan((building["height"] * 0.45 - 2.5) / distance))
    return {"building": building["id"], "pano": meta["pano_id"], "location": [round(at[0], 7), round(at[1], 7)],
            "heading": round(bearing(at, centre)), "pitch": round(min(30, max(0, pitch))),
            "fov": round(min(55, max(20, 2 * math.degrees(math.atan(7 / distance))))),
            "taken": meta.get("date")}


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
        centre_y = height * 0.47
    else:
        centre_x = width * (0.5 + off / PHOTO_FOV)
        crop_w = min(width * 0.6, max(width * 0.3, width * view / PHOTO_FOV))
        centre_y = height * 0.45
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
    token = os.environ.get("GOOGLE_METADATA_KEY" if google else "MAPILLARY_TOKEN")
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
        if b["id"] in landmark_ids or (b.get("height") or 0) < MIN_HEIGHT or not near_campus(b):
            continue
        x, y = to_xy(b["lon"], b["lat"])
        regions.setdefault((math.floor(x / args.region), math.floor(y / args.region)), []).append(b)

    kept = {p["building"]: p for p in pack.get("cluePhotos", [])
            if p.get("pano") or (out_dir / f"{p['building']}.jpg").exists()}
    photos = []
    for n, (key, members) in enumerate(sorted(regions.items()), 1):
        already = [kept[b["id"]] for b in members if b["id"] in kept]
        if already and not args.refresh:
            photos.extend(already)
            continue
        if google:
            for b in sorted(members, key=lambda m: (-m["height"], m["id"])):
                view = google_view(token, b)
                if view:
                    photos.append(view)
                    break
            print(f"\r{campus}: region {n}/{len(regions)}, {len(photos)} views", end="", flush=True)
            continue
        pad_lat = (args.region + 70) / M_PER_DEG
        pad_lon = pad_lat / math.cos(math.radians(lat0))
        cx = sum(b["lon"] for b in members) / len(members)
        cy = sum(b["lat"] for b in members) / len(members)
        images = mapillary(token, cx - pad_lon, cy - pad_lat, cx + pad_lon, cy + pad_lat)
        for b in sorted(members, key=lambda m: (-m["height"], m["id"])):
            view = best_view(b, images)
            if not view:
                continue
            _, image, off, distance = view
            try:
                with urllib.request.urlopen(image["thumb_2048_url"], timeout=60) as response:
                    original = Image.open(io.BytesIO(response.read())).convert("RGB")
            except Exception as error:
                print(f"  ! download failed for building {b['id']}: {error}", file=sys.stderr)
                continue
            crop(original, bool(image.get("is_pano")), off, distance).save(
                out_dir / f"{b['id']}.jpg", "JPEG", quality=80, optimize=True, progressive=True)
            author = (image.get("creator") or {}).get("username") or "a Mapillary contributor"
            taken = time.strftime("%Y", time.gmtime(image["captured_at"] / 1000)) if image.get("captured_at") else None
            photos.append({"building": b["id"], "file": f"clues/{campus}/{b['id']}.jpg",
                           "credit": f"© {author}" + (f", {taken}" if taken else "") + " · Mapillary · CC BY-SA 4.0 (cropped)",
                           "source": f"https://www.mapillary.com/app/?pKey={image['id']}"})
            break
        print(f"\r{campus}: region {n}/{len(regions)}, {len(photos)} photos", end="", flush=True)
        time.sleep(0.2)
    print()

    used = {p["building"] for p in photos}
    stored = {p["building"] for p in photos if p.get("file")}
    for stale in out_dir.glob("*.jpg"):   # photos of regions that no longer qualify
        if int(stale.stem) not in stored:
            stale.unlink()
    pack["cluePhotos"] = sorted(photos, key=lambda p: p["building"])
    with pack_path.open("w", encoding="utf-8") as out:
        json.dump(pack, out, separators=(",", ":"), ensure_ascii=False)

    # How many upcoming dailies will show a photo (same choice as Expedition.clueBuilding).
    today = time.strftime("%Y-%m-%d")
    upcoming = [p for d, p in pack.get("daily", {}).items() if d >= today]
    def has_clue(puzzle):
        zx, zy = to_xy(puzzle["zone"][0], puzzle["zone"][1]) if isinstance(puzzle["zone"], list) else to_xy(
            puzzle["zone"]["lon"], puzzle["zone"]["lat"])
        return any(math.hypot(*(a - b for a, b in zip(to_xy(by_id[i]["lon"], by_id[i]["lat"]), (zx, zy)))) <= 80
                   for i in used)
    covered = sum(1 for p in upcoming if has_clue(p))
    size = sum(f.stat().st_size for f in out_dir.glob("*.jpg")) / 1e6
    for folder in (out_dir, out_dir.parent):   # no stored photos: leave no empty folders behind
        if folder.exists() and not any(folder.iterdir()):
            folder.rmdir()
    print(f"{campus}: {len(photos)} views ({size:.1f} MB stored) for {len(regions)} regions; "
          f"{covered}/{len(upcoming)} upcoming dailies have a photo")


if __name__ == "__main__":
    main()
