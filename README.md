# Campus Hot & Cold

## Project overview

Campus Hot & Cold is a Java geography game for Canadian university students. Choose a campus, then identify a secret landmark in six guesses. After each guess, get a hot/cold label and see whether you moved closer or farther away. The campus is known at the start, and a list of landmark names gives new players a way in. The 3D view would fly to the building you just guessed and fly to the secret landmark when the round ends.

## Minimum viable product

- One playable U of T St. George campus pack with 12 landmarks. A round offers up to six distinct guesses.
- Choose one secret landmark from the loaded campus. Show the candidate list, guesses remaining, and guess history. Reject repeated guesses without consuming a turn. End the round on a correct guess or after six valid guesses.
- Compute straight-line distance using the Haversine formula. For an initial St. George playtest, use four bands: hot under 100 m, warm under 250 m, cool under 500 m, and cold at 500 m or more. Show warmer, colder, or same based on the actual distance relative to the previous guess, treating a change under 1 m as the same. Show the trend even when the band stays the same. Show exact distances in the post-round recap.
- A win earns 7 minus the number of guesses used. An unsolved round earns zero. The improvement between guesses appears as feedback. A deliberate probe in the opposite direction can still help you narrow down the answer.
- A campus pack contains campus metadata and a curated list of Wikipedia article titles. Add Waterloo, UBC Vancouver, or McGill downtown by supplying another pack and checking that its coordinates and clue bands play well.

## User stories and proposed ownership

Proposed four-person split. Teammate names and final ownership are pending roster confirmation.

| Owner | Use case | User story | Completion check |
|---|---|---|---|
| Tony Yin (proposed) | Start a round | As a player, I want to choose a campus so I can play somewhere I recognize. | Load the landmark list, pick a secret, and show six available guesses. Show a retry message if too few landmarks load. |
| Teammate 2 (TBD) | Submit a guess | As a player, I want to know how warm my guess is and whether I got closer so I can narrow down the answer. | Score the distance, append feedback to history, and handle a win, a loss, and duplicates. |
| Teammate 3 (TBD) | Explore a guessed landmark | As a player, I want to see the place I just guessed so I can recognize it next time I’m on campus. | Open the latest guess in a browser map. The browser viewer can later use a 3D fly-to. |
| Teammate 4 (TBD) | Review results | As a player, I want to see the secret location and how my guesses compared so I can understand the round. | Reveal the answer, show the score and ordered distances, for each guess. |

Optional fifth use case: As a player, I want to browse the landmarks for a campus before playing so I can learn the campus. Show the names and let the player inspect each landmark without starting a round.

## Core entities

| Entity | Responsibility |
|---|---|
| Campus | Stable ID, display name, and configuration for its landmark pack and distance bands. |
| Landmark | Stable ID, campus ID, display name, and latitude/longitude. |
| Round | Secret landmark ID, ordered guesses, guess limit, and status. |
| Guess | Guessed landmark ID, computed distance, heat band, and change from the previous guess. |

## External API and successful request

The Java gateway batches Wikipedia article titles using the [MediaWiki Action API](https://www.mediawiki.org/wiki/API:Geosearch/en). It requests primary Earth coordinates and follows article redirects. The coordinates drive distance feedback and map destinations. Loading takes place off Swing's event-dispatch thread. If fewer than six usable landmarks come back, the screen allows a retry.

```text
GET https://en.wikipedia.org/w/api.php
    ?action=query&prop=coordinates&redirects=1
    &coprimary=primary&colimit=max&format=xml
    &titles=Robarts%20Library%7CHart%20House%20(University%20of%20Toronto)
```

On November 13, 2026, the Java API check returned 12 landmarks from the full campus pack. Robarts Library returned latitude `43.664444` and longitude `-79.399444`. Convocation Hall resolved through a redirect.

## Run locally

Install JDK 21 or later. In a macOS, Linux, or Git Bash terminal, run:

```sh
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
java -cp out campus.ui.CampusGame
```

Run `java -cp out campus.ui.CampusGame --check-api` to verify the live API and list the imported coordinates without opening Swing.

## 3D campus view

The proposed 3D viewer uses the [Google Maps JavaScript 3D camera API](https://developers.google.com/maps/documentation/javascript/3d/animate-camera) in a browser page opened by the Java app. Each valid guess sends the guessed coordinates to the viewer and flies the camera to an angled view of the landmark. For a front-facing shot, try a per-landmark camera heading and offset. The final reveal uses the secret landmark as the destination.

First prototype one U of T building in a normal browser and confirm imagery, camera framing, and [key and billing setup](https://developers.google.com/maps/documentation/javascript/get-api-key). Check coverage before adding another campus. The map viewing use case can use a 2D map link while the 3D view is being evaluated. Keep the game and scoring in Java.

## November 13 milestone

The starter includes the four domain entities, the Wikipedia gateway, the start-round interactor, and the Swing screen. A player can load the campus, play a full round, view the results, and open the most recent guess or revealed answer in a browser map. The 3D fly-to is proposed and depends on testing camera framing and imagery coverage on the selected campus.
