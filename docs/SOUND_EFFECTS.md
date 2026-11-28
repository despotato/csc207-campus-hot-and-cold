# Sound effects for Campus Hot & Cold Explore

The game already calls every sound below at the right moment. A sound that has no file yet is simply silent, so
files can be added one at a time, in any order.

## How to deliver a sound

- **Name:** exactly the slot id below, plus `.mp3` (for example `check-cold.mp3`). Lower case, hyphens only.
- **Where:** `src/main/resources/campus/explore/sounds/`. The Java app serves them from there; the website gets the
  same folder copied next to `index.html` when it is deployed.
- **Format:** MP3, 44.1 kHz, mono is fine (stereo for the ambience loops), 128–192 kbps. Keep each one-shot under
  about 50 KB; ambience loops under about 400 KB.
- **Level:** master everything to roughly the same loudness (around -16 LUFS) with no clipping. The game sets each
  sound's relative volume itself (the `SOUNDS` table near the top of the sound code in `index.html`), so don't bake
  quietness into a file. Trim silence at the start so sounds land on the action.
- **Style:** the game is pixel art with a warm, toy-like miniature campus. Think chiptune-adjacent but soft: short,
  rounded, a little bit of real-world texture (paper, footsteps, a pin), never harsh. Most one-shots should be
  short (0.1–0.6 s) because the next action is available straight away. The two big moments (zone found, correct
  guess) can be longer and grander.
- **Loops** (`walk`, `ambience-*`) must loop seamlessly (no click at the loop point).
- Players can mute everything (🔊 Sound on/off on the title screen and in the Esc controls panel).

## The list

### Menus and campus choice

| Slot | When it plays | What it should sound like | Length |
|---|---|---|---|
| `ui-click` | Any ordinary button press (Start, How to play, History, Back, Look around…) | A soft, dry pixel "tick": a tiny muted key or wooden click. Very quiet and unobtrusive; it plays a lot. | 0.05–0.1 s |
| `ui-select` | Choosing an option in a row: Daily/Unlimited, Light (Live/Day/Night), turning sound on | Slightly brighter than `ui-click`: a two-note rising blip, like flipping a toggle on a toy. | 0.1–0.2 s |
| `picker-open` | "Visit another campus" / "Change home" opens the map of Canada | A paper map unfolding, with a light airy swell. | 0.4–0.7 s |
| `campus-fly` | A campus is chosen and the camera flies across Canada to it | A smooth whoosh that rises then settles, like a small plane passing; it accompanies a ~3 s flight, so it can tail off gently. | 1.5–3 s |

### Stage 1: find the zone

| Slot | When it plays | What it should sound like | Length |
|---|---|---|---|
| `round-start` | A round (or the guided practice) begins and the camera arrives on campus | An upbeat "let's go" sting: a short rising arpeggio with a soft bell on top. Inviting, not loud. | 0.6–1.0 s |
| `pin-drop` | The player taps the ground and the gold pin drops into place (also when the pin is moved) | A light, satisfying "thunk-tink": a pin pushed into a corkboard, with a tiny metallic ring. | 0.15–0.3 s |
| `blocked` | The tap can't become a pin (inside a building, off the map) | A soft, low "bonk" or muted double buzz. Clearly "no", but friendly, not an error alarm. | 0.15–0.25 s |
| `walk` | **Loop** while the mascot walks to the pin (0.7–2.2 s) | Quick, light footsteps on pavement in a cheerful rhythm, slightly cartoonish. Must loop cleanly; it fades out on arrival. | 0.5–1 s loop |
| `arrive` | The mascot lands at the pin with a little hop | A small bouncy "boop" landing, like a toy figure set down on a table. | 0.1–0.25 s |
| `map-open` | The campus map opens (M or the Map button) | A sheet of paper unfolding quickly. | 0.2–0.4 s |
| `map-close` | The map closes back to the 3D view | The same paper, folding (shorter, lower than `map-open`). | 0.15–0.3 s |
| `photo-open` | The pinned evidence photo is enlarged | A photo slid out of an envelope / a camera-shutter snap, crisp and short. | 0.2–0.4 s |
| `photo-close` | The enlarged photo is pinned back to the side | A thumbtack press: soft "tuck". | 0.1–0.2 s |
| `check-cold` | A temperature check reads **cold** (500 m or more from the zone) | An icy, airy chime: glassy, high, slightly hollow, with a tiny wind gust. The "far away" sound. | 0.4–0.7 s |
| `check-cool` | A check reads **cool** (300–500 m) | Like cold but warmer: a softer, mid-pitch chime with less air. | 0.4–0.7 s |
| `check-warm` | A check reads **warm** (150–300 m) | A warm, rounded marimba/bell tone with a gentle swell. Encouraging. | 0.4–0.7 s |
| `check-hot` | A check reads **hot** (under 150 m) but not yet in the zone | An excited, bright, slightly sizzling tone (a short crackle or kettle hiss under a high bell). Clearly "very close". | 0.4–0.8 s |
| `trend-warmer` | Right after the check sound, when this check is warmer than the last | A short rising two-note "up" figure. Should read as "better" even without looking. | 0.2–0.35 s |
| `trend-colder` | Right after the check sound, when this check is colder than the last | The mirror of `trend-warmer`: a short falling two-note "down" figure. Not sad, just "wrong way". | 0.2–0.35 s |
| `direction` | After the first missed check, when the gold arrow appears pointing the way to search | A quick directional "swish" with a small compass-needle tick at the end. | 0.3–0.5 s |
| `last-check` | The player has just used their last check | A gentle warning: two soft low pulses, like a clock ticking down. Builds tension without alarm. | 0.4–0.6 s |
| `final-search-armed` | With no checks left, the first press asks "Final search?" (a second press commits) | A held, suspenseful note (drum roll start or a rising tremolo), as if asking "are you sure?". | 0.5–0.8 s |

### Stage 2: guess the building

| Slot | When it plays | What it should sound like | Length |
|---|---|---|---|
| `zone-found` | A check lands inside the zone: the camera flies up and the candidate buildings flash gold | **One of the two biggest moments.** A bright triumphant fanfare: rising arpeggio into a sparkling chord with shimmer, about as long as the gold flash. Celebratory but it leads into the next stage, so it ends on an "open" (not final) chord. | 1.2–2 s |
| `hint-flip` | A face-down hint card is chosen and flips over | A playing card flipped onto a table: a crisp paper "flick" with a tiny reveal sparkle. | 0.2–0.4 s |
| `guess-wrong` | A guessed building isn't the one | A short, soft "nope": muted descending two-note buzz or wooden thud. Mild; the player still has guesses. | 0.25–0.4 s |
| `guess-right` | The correct building is guessed: gold sparks burst from its roof and the beacon rises | **The biggest moment of the game.** A full victory sting: bright fanfare with sparkle/firework crackle and a resolving final chord. It plays with sparks that fall for about 1.4 s. | 1.5–2.5 s |
| `round-lost` | The round ends without a correct guess (final search missed, or out of guesses) and the answer is revealed | A gentle, slightly comic "aww": a soft descending phrase that still feels friendly (the player gets a score either way). | 0.8–1.4 s |

### Results and the guided practice

| Slot | When it plays | What it should sound like | Length |
|---|---|---|---|
| `results` | The score sheet slides in (the total counts up over about 1.4 s) | A light rolling tally: a soft coin/abacus run that finishes on a small "ding" around 1.4 s. | 1.2–1.6 s |
| `achievement` | A badge marked NEW appears on the results (one per new badge, staggered) | A sparkly unlock chime, like a coin into a jar with a twinkle. Distinct from `results` so it cuts through. | 0.4–0.7 s |
| `coach-step` | The guided practice shows its next instruction | A soft, friendly notification "pop" (one gentle bell). Quiet; it plays every step. | 0.15–0.3 s |

### Ambience (loops, very quiet, under everything)

| Slot | When it plays | What it should sound like | Length |
|---|---|---|---|
| `ambience-day` | **Loop** during a round in the Day look | A sunny campus: distant chatter, birds, a breeze through trees, a far-off bike bell now and then. Low and even, no attention-grabbing events. | 20–40 s loop |
| `ambience-evening` | **Loop** during a round at Dawn or Golden hour | Calmer than day: fewer voices, evening birds, a warm breeze, very distant traffic. | 20–40 s loop |
| `ambience-night` | **Loop** during a round in the Night look | Crickets, a soft hum of street lamps, an occasional distant car or door. Peaceful. | 20–40 s loop |

The ambience starts when a round starts and fades out when the round ends.
