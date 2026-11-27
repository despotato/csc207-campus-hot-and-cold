# Start-of-round clue views

At the start of a round the player sees a street view of an ordinary building inside the hidden zone (never a
landmark: that would give the zone away, and it is never the target). It stays as a "Zone photo" button for the rest
of the round.

- **Domain:** `ZoneClue` (used by `Expedition.clueBuilding()` / `clueText()`) chooses from ordinary buildings 10 m or
  more inside the zone, else ordinary ones anywhere in it, picked from the zone's centre so a daily's clue is the same
  for everyone. Only when every building in the zone is a landmark does it use another landmark, and then only as
  words: an obscure, dated fact from its reviewed hint facts (the one the hard hint uses), else its OSM history, else its look; never a photo, never a fact naming the target. The page mirrors it (`LocalRules`, measuring with
  the same haversine formula so both agree at the boundaries).
- **Pack:** `cluePhotos` lists one view per ~60 m region of the campus, chosen ahead of time by
  `tools/fetch_clue_photos.py`. Players never trigger that step.
- **Display:** a Google Street View entry (`pano`, `heading`, `pitch`, `fov`) is shown through the Maps Embed API.
  A stored photo (`file`, from Mapillary or our own camera) is served from `packs/clues/<campus>/<building>.jpg`.

## Cost: why it's guaranteed to be $0

| Step | Google product | Google's price |
|---|---|---|
| Choosing views (a developer, once per campus) | Street View Static API **metadata** | No charge, no quota used |
| Showing a view (every player, every round) | **Maps Embed API** | No charge, unlimited |

No Google images are downloaded or stored (Google's terms don't allow that), and no paid product is used. The keys
below are restricted so that, even if someone copies the public key, it can only call the free Embed API.

## Setting up the keys (once)

Google Maps Platform needs a billing account on the project even for free products. The restrictions below are what
make spending impossible.

1. In Google Cloud Console, create a project and enable **Maps Embed API** and **Street View Static API**.
2. **Embed key** (public; goes in `index.html` as `GOOGLE_EMBED_KEY`):
   - API restrictions: **Maps Embed API only**.
   - Application restrictions: Websites: `https://despotato.github.io/*`, `http://127.0.0.1:8790/*`,
     `http://localhost:8790/*`.
3. **Metadata key** (private; never commit it):
   - API restrictions: **Street View Static API only**.
   - Use it only on your machine: `GOOGLE_METADATA_KEY=... python3 tools/fetch_clue_photos.py <pack>`.
   - Under APIs & Services → Street View Static API → Quotas, lower the request quota to the minimum. Metadata
     requests use no quota, so this only blocks paid image requests.
   - When all campuses are done, delete the key (or disable the Street View Static API).
4. Optional: a $1 budget alert on the billing account. It only alerts; the restrictions are what block spending.

If Google ever starts charging for the Embed API, set its daily request quota to the free amount in the Console.

## Running it

```
GOOGLE_METADATA_KEY=... python3 tools/fetch_clue_photos.py src/main/resources/campus/packs/st-george.json
```

The script prints how many upcoming dailies have a view. Regions that already have one keep it (so a view players
have seen doesn't change); `--refresh` redoes them. Then copy the packs to gh-pages as usual (and `packs/clues/`
if any stored photos were added).

`--source mapillary` (needs `MAPILLARY_TOKEN` and `pip install pillow`) stores cropped CC BY-SA photos instead,
for campuses or regions without Street View.
