# Prompt: add a new campus to Campus Hot & Cold

Paste everything below the line into a new session (Claude, GPT, or another coding agent), replacing the bracketed
values on the first lines. It assumes the agent can run shell commands in a clone of the repository.

---

Add a new campus to **Campus Hot & Cold** (Java 21 / Maven, repository `csc207-campus-hot-and-cold`, branch
`feature/3d-viewer`).

- Campus: **[e.g. University of Waterloo — Main campus]**
- Short label for the start screen: **[e.g. UWaterloo]**
- Campus ID (lowercase letters, digits, hyphens): **[e.g. uwaterloo]**
- Time zone (IANA): **[e.g. America/Toronto]**
- Mascot: **[e.g. King Warrior (a goose? check the school's actual mascot)]** and school colours **[hex codes]**

## How campuses work (read first)

- Each campus is a **data pack**: `src/main/resources/campus/packs/<id>.json`, listed in
  `src/main/resources/campus/packs/index.json` (`id`, `name`, `short`, `file`, `timezone`). The Java app
  (`PackCatalog`) and the browser page (`src/main/resources/campus/explore/index.html`) both read this index,
  so **adding a campus needs no Java or page code changes**.
- Packs are built by `tools/build_pack.py` from OpenStreetMap data (Overpass API exports). A pack holds the curated
  landmarks (possible targets), every building outline with height, facade material, courtyard flag and OSM history,
  the footpath network, trees and green areas, the mascot, and 400 pre-generated **daily puzzles** (same target, zone
  and start for every player on a date, in both the Java app and the website).
- Game rules: a 90 m zone around (but not centred on) a landmark; the start is 450 to 900 m away; every zone must
  contain at least 5 buildings at least 4 m tall, at least 3 of them named. Read `README.md` (section
  "Explore mode (3D)") and `campus.domain.explore.Expedition` for details.

## Step 0: one-time refactor (skip if `tools/campuses/` already exists)

`tools/build_pack.py` was written for St. George and hard-codes its settings. Make it config-driven first:

1. Create `tools/campuses/st-george.json` with everything campus-specific currently in the script: `id`, `name`
   (the pack's `campus` field), `timezone`, `bounds` (`south`/`west`/`north`/`east`), `mascot`, `flat` (OSM ids
   highlighted at ground level, e.g. stadium fields), `dailyFrom` (ISO date), and `landmarks` (a list of
   `{osm, name, category, wikipedia}`, exactly the current `LANDMARKS` tuples). Categories in use: Library,
   Residence, College, Teaching, Campus life, Athletics.
2. Change the script to `python3 -I tools/build_pack.py tools/campuses/<id>.json <buildings.json> <out.json>
   [greenery.json]`, reading all of those from the config (also use the config bounds for `lat0`, which is currently
   hard-coded as `(43.6565 + 43.6695) / 2`). Keep `DAILY_DAYS`, radius and start distances as script constants.
3. Rebuild St. George and confirm the output is **identical** to the committed
   `src/main/resources/campus/packs/st-george.json` (`cmp` or a JSON diff). This proves the refactor changed nothing,
   including the daily puzzles players already have. The original Overpass exports may not be on this machine; if so,
   re-fetch them (see Step 2) and accept differences only in OSM data that changed upstream. Explain any diff.
4. Commit ("Make the campus pack builder config-driven").

## Step 1: choose bounds and landmarks

1. Pick a bounding box that covers the campus plus a margin of about 150 m (so starts 450 to 900 m from zones fit).
   Keep it to roughly 2 km x 2 km or less: the walkability grid is 1.5 m cells, and larger areas slow round setup in
   the browser.
2. Fetch named buildings in the box (Step 2's query, or a smaller `[building][name]` one) and curate **40 to 80
   landmarks** students would recognise: libraries, residences, colleges, main teaching buildings, student centres,
   athletics. For each, record the OSM element (`w123` / `r456`), a clean display name, a category, and its English
   Wikipedia title if one exists (check that the article really is that building). Prefer buildings, not open
   fields; put open-air ones (stadium fields) in `flat`.
3. Mascot: use the school's real mascot name and species, but an **original** blocky design in school colours. Don't
   copy official artwork (it's trademarked). The page can currently draw `beaver`; anything else falls back to a
   student. If the mascot isn't a beaver, either accept the student fallback or add a new body next to
   `beaverFeatures` in `index.html` (same box format: `[x0, x1, y0, y1, z0, z1, colour]`, metres before scaling).

## Step 2: fetch OpenStreetMap data (Overpass)

Use `https://overpass-api.de/api/interpreter` (fall back to `https://overpass.kumi.systems/api/interpreter` on
502/504/429). Be polite: one query at a time, wait and retry with backoff when busy, send a descriptive User-Agent.
Save exports outside the repo (they are large).

Buildings, footpaths and stadiums (**`out geom;`, not `out tags geom;`**, so multipolygon relations keep their
member geometry; courtyards come from relation `inner` members):

```
[out:json][timeout:180];
(
  way(S,W,N,E)[building];
  relation(S,W,N,E)[building];
  way(S,W,N,E)[leisure=stadium];
  way(S,W,N,E)[highway~"^(footway|path|pedestrian|steps|living_street|residential|service|tertiary|secondary|primary|unclassified|cycleway|corridor)$"];
);
out geom;
```

Greenery (optional but makes the ground look alive):

```
[out:json][timeout:180];
(
  node(S,W,N,E)[natural=tree];
  way(S,W,N,E)[landuse~"^(grass|meadow|village_green)$"];
  way(S,W,N,E)[leisure~"^(park|garden|pitch)$"];
  relation(S,W,N,E)[leisure~"^(park|garden)$"];
  way(S,W,N,E)[natural~"^(wood|scrub)$"];
);
out geom;
```

## Step 3: build and register the pack

1. Write `tools/campuses/<id>.json`, then build:
   `python3 -I tools/build_pack.py tools/campuses/<id>.json buildings.json src/main/resources/campus/packs/<id>.json greenery.json`.
   The script exits with `missing ... <osm id>` if a curated landmark isn't in the export. Fix the id, or the
   bounding box.
2. Check the printed summary: the landmark count matches your list, and there are hundreds of buildings, thousands of
   walk nodes and about 400 daily puzzles. **Far fewer daily puzzles means most zones fail the "real choice" rule.**
   Add more landmarks in built-up areas, or check the bounds. Look at a few puzzles: start points must be outdoors.
3. Add the campus to `src/main/resources/campus/packs/index.json` (`file` must match `[a-z0-9-]+\.json`). Order
   matters: the first entry is the default campus.
4. Keep the pack around 1 MB or less (St. George is about 1 MB). If it's much bigger, shrink the bounds.

## Step 4: tests

- `CampusPackLoaderTest` and `ExploreWebServerTest` assert St. George details (e.g. `List.of("st-george")` from
  `PackCatalog.campusIds()`). Update them to include the new campus, and add a test that loads the new pack: landmarks
  present, a daily puzzle for a date in range starts on open ground (`Expedition.daily`), and 10 random
  `Expedition.begin` rounds set up with `shortestToZone() > 300`.
- Run `mvn verify` (tests **and** Checkstyle; the build fails on style violations). Everything must pass.

## Step 5: check it visually (Playwright)

`playwright-cli` is installed globally (`playwright-cli open <url>`, `eval "() => ..."`, `screenshot --filename=x.png`,
then read the PNG).

1. Build and run a private debug server on an unused port (don't use 8790, which the user may be playing on):
   `mvn -q -DskipTests package && java -Djava.awt.headless=true -Dcampus.explore.port=8797 -Dcampus.explore.debug=true
   -Dcampus.explore.pageFile=src/main/resources/campus/explore/index.html -jar target/campus-hot-and-cold-0.2.0.jar`
2. Open `http://127.0.0.1:8797/?debug=1&campus=<id>`. Check that the title shows the campus and mascot and the
   selector lists both campuses. Pick Unlimited, press Enter, walk a little (hold W), screenshot. Buildings, trees and
   labels should sit where they belong.
3. Call `game.debugWalkToZone()` (it walks the shortest route), wait until `game.autopilot` is null, press Space
   (the check should find the zone), pick a hint card, guess, and screenshot the results panel. Facts must not name
   the building.
4. Overhead border check (recommended): for several landmarks, confirm each OSM outline matches the building drawn
   there. Fix wrong OSM picks in the config.

## Step 6: web demo (GitHub Pages), only if the user asks

The site is the `gh-pages` branch: `index.html` + `packs/index.json` + `packs/*.json`. Copy the page and **all**
packs plus the index into a checkout of `gh-pages`, commit, push, and wait for the Pages build. Then load
`https://despotato.github.io/csc207-campus-hot-and-cold/?campus=<id>` in Playwright and start a round to confirm.
On the website, game rules run in the browser (`LocalRules` in `index.html`). It reads the same pack, so nothing
campus-specific is needed there.

## Ground rules

- Commit in small steps on the feature branch with clear messages. Don't push the feature branch or deploy Pages
  unless asked.
- Don't edit teammates' use cases. Campus support is data plus tests.
- Attribution: map data is © OpenStreetMap contributors (ODbL); facts are from Wikipedia (CC BY-SA). Keep the existing
  credits.
- Report honestly: what you built, test results, screenshots you checked, and anything that looked wrong
  (missing landmarks, few daily puzzles, mislabelled buildings).
