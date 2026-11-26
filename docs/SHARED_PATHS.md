# Walking students: shared foot trails

Students walk around each campus along the paths real players took. The more people play, the busier the campus
looks. Today this works in the **Java app** with trails stored on that computer. This document plans the step to a
**shared online store**, so every player (including the GitHub Pages site) sees everyone's paths.

## How it works today (Java)

| Layer | Piece | Role |
|---|---|---|
| Domain | `FootTrail` | An anonymous path: campus ID, points, time. Thinned to a point every ~8 m. No player, score or target. |
| Application | `FootTrailRepository` (port) | `record(trail)` and `recent(campusId, limit)`. `NONE` is the Null Object. |
| Application | `RoundFinisher` | When a round ends, records its walked track as a trail (best effort: storage errors never affect the round). |
| Application | `CampusTrafficInteractor` | Use case behind the `traffic` action: up to 60 recent trails of at least 40 m. |
| Adapters | `ExploreController` / `ExplorePresenter` | `POST /api/traffic {"campus": "<id>"}` gives `{"type": "traffic", "trails": [[[lon, lat], ...], ...]}`. |
| Infrastructure | `FileFootTrailRepository` | `~/.campus-hot-and-cold/trails/<campus>.trails.jsonl`, newest 400 per campus, damaged lines skipped. |
| Browser | `index.html` `loadTraffic`, `setStudents`, `updateStudents` | One instanced blocky student per trail, walking it back and forth with shadows. Refreshed after each round. |

The static site (`STATIC` mode) shows no students yet: it has no server to ask.

## Going global

Only one new class is needed on the Java side: an `HttpFootTrailRepository` in `campus.infrastructure` that
implements the same port against a hosted API. `Main` wires it instead of (or alongside) the file store. Nothing in
the domain, use cases, controller or presenter changes, which is the point of the port.

### Store (recommended: Supabase, free tier)

```sql
create table foot_trails (
  id bigint generated always as identity primary key,
  campus_id text not null check (campus_id ~ '^[a-z0-9-]+$'),
  points jsonb not null,                       -- [[lon, lat], ...], 2 to 400 points
  recorded_at timestamptz not null default now()
);
create index on foot_trails (campus_id, recorded_at desc);
alter table foot_trails enable row level security;
create policy "anyone reads" on foot_trails for select using (true);
create policy "anyone adds" on foot_trails for insert with check (jsonb_array_length(points) between 2 and 400);
-- no update or delete policy: trails are append-only
```

- **Read:** `GET /rest/v1/foot_trails?campus_id=eq.<id>&order=recorded_at.desc&limit=120&select=points`
- **Write:** `POST /rest/v1/foot_trails` with `{"campus_id": ..., "points": ...}`.
- Both send the project's public `anon` key (safe to ship in the page; row level security is what protects data).

Firebase Firestore works the same way, with a `campuses/{id}/trails` collection and equivalent security rules.

### Browser (GitHub Pages)

In `STATIC` mode, `loadTraffic` calls the read URL directly and `finish` posts the trail (thinned the same way as
`FootTrail.of`). In the Java app, keep calling `/api/traffic` and let `HttpFootTrailRepository` talk to the store.

### Privacy and abuse

- Store **only** the path and a time: no names, accounts, IP addresses, scores or targets. Say so in the tutorial.
- Validate on insert (a database function or edge function): every point inside the campus pack's bounds, at most
  400 points, a plausible length (under ~5 km). Reject anything else.
- Rate-limit inserts (e.g. one per 30 s per client) with an edge function, or accept spam and rely on the cap: the
  game only ever reads the newest 120.
- Prune old rows on a schedule (keep e.g. the newest 2,000 per campus).

### Steps

1. Create the project and table above; note the URL and anon key (keep them out of Git if you prefer: a
   `-Dcampus.trails.url` system property in Java, a small config in the page).
2. Add `HttpFootTrailRepository` (plus tests against a fake HTTP server, like `ExploreWebServerTest` does).
3. Wire it in `Main`; keep `FileFootTrailRepository` as an offline fallback (a tiny composite repository).
4. Add the static-site read and write in `index.html`.
5. Deploy and check two browsers see each other's students.
