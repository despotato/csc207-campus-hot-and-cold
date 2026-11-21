# Three-person implementation handoff

Repository: https://github.com/despotato/csc207-campus-hot-and-cold

Working team confirmed by Tony: Tony Yin, Jinshi Li, and Takumi Goto. MarkUs still lists Hang Wu as a fourth member; this document does not change the official roster. Teammates must accept the GitHub invitations before pushing branches.

These are proposed responsibilities, not claims of completed individual contributions. Each member should understand, implement, test, and submit their own work through a pull request. Do not fabricate commit authors, backdate commits, or list generated code as another member's contribution.

| Person | Use case | Current state |
|---|---|---|
| Tony | Start a round | Input/output boundaries, interactor, controller, presenter, view model, HTTP gateway, Swing loading/retry integration, and JUnit tests implemented. |
| Jinshi | Submit a guess | Starter domain logic exists; needs its own Clean Architecture flow, GUI integration, and tests. |
| Takumi | Review saved results | Starter on-screen recap exists; needs its own Clean Architecture flow, file persistence, history screen, and tests. Map viewing can be included here. |

## Stable integration points

- `campus.Main` is the composition root. Add dependency wiring here.
- `GameSession.currentRound()` returns the active `Round`; it throws before a round is started. `GameSession.start(Round)` replaces it only after loading succeeds.
- Start workflow: `StartRoundController` → `StartRoundInputBoundary` → `StartRoundInteractor` → `StartRoundOutputBoundary` → `StartRoundPresenter` → `StartRoundViewModel`.
- `StartRoundResponse.Candidate` supplies ID/name pairs for the dropdown. It contains no secret target or coordinates.
- Domain rules belong in `Round`, `Guess`, and `Landmark`. `Round.submitGuess(long)` already rejects duplicates, unknown IDs, and completed rounds.
- `Round.revealedLandmark()` is empty until a win or six guesses. Only then may presenters expose exact distances or the answer.
- The Swing view currently contains starter `submitGuess`, `showResults`, and `openMap` methods. Jinshi replaces guessing; Takumi replaces results/map handling. Coordinate small edits to `CampusGame.java` and `Main.java` instead of replacing those files wholesale.
- Run network/file work on a worker and mutate Swing components on the event-dispatch thread. Disable conflicting controls while a worker is running. Use cases share a session and must not run concurrently.
- Preserve Tony's start/retry behavior: a failed load keeps the previous round and restores its controls. A successful load resets candidates/history and disables the map until a guess.

## Prompt for Jinshi

```text
Work in https://github.com/despotato/csc207-campus-hot-and-cold. Read README.md, docs/TEAM-HANDOFF.md, and the existing code first. My responsibility is Submit a guess. Tony has completed Start a round; Takumi owns saved results/history. Create my feature branch and implement only my use case, using Clean Architecture and SOLID.

Add a SubmitGuessInputBoundary, request/response DTOs, output boundary, interactor, controller, presenter, and view model. Inject the shared GameSession and call Round.submitGuess(id). Preserve the six-guess limit, Haversine heat bands, warmer/colder/same feedback, score, duplicate/unknown-guess rejection without consuming turns, and completed-round rejection. Output only public feedback while the round is active; hide the target and exact distances until completion. Replace the starter guessing logic in CampusGame with the controller flow and wire dependencies in campus.Main. Agree with Takumi on a completed-round trigger so results are saved/reviewed without duplicate submissions. Preserve Tony's start/retry behavior and avoid rewriting unrelated files.

Add JUnit tests for valid guesses, duplicates, invalid IDs, all heat/trend boundaries, wins, sixth-guess losses, and guesses after completion. Run mvn verify and manually test Swing. Open a focused PR with actual changes, test results, and any remaining limitations. Use my own Git identity; do not fabricate another member's work or claim tests that were not run.
```

## Prompt for Takumi

```text
Work in https://github.com/despotato/csc207-campus-hot-and-cold. Read README.md, docs/TEAM-HANDOFF.md, and the existing code first. My responsibility is Review saved results, including persistence. Tony owns Start a round; Jinshi owns Submit a guess. Create my feature branch and implement only my use case using Clean Architecture and SOLID.

Add a completed-round result entity, a RoundRepository port, a file-backed repository, and input/output boundaries, interactor, controller, presenter, and view model for saving/reviewing results and browsing prior rounds after restarting. Save only completed rounds, with stable IDs, campus, completion time, answer, ordered guesses/distances, win/loss, and score. Make retries idempotent; show recoverable errors for unreadable/corrupt files or failed writes without silently losing results. Use atomic file replacement, validate loaded data, and never deserialize arbitrary Java objects. Add a Swing history view and a way to reopen a saved recap; returning from history must preserve the current round. Move starter result formatting out of CampusGame. Keep map opening behind a gateway and restrict it to guessed/revealed landmarks. Wire dependencies in campus.Main and coordinate the completion trigger with Jinshi. Do not replace Tony's start flow or implement guessing.

Add JUnit tests for save/reload across repository instances, ordering, duplicate saves, active-round rejection, corrupt/missing files, write failures, and history navigation state. Run mvn verify and manually test Swing. Open a focused PR with truthful validation and your own Git identity.
```

## Integration order

1. Merge Tony's start-round PR.
2. Jinshi and Takumi branch from the updated `main`; agree on a small completed-round callback/DTO contract.
3. Merge guessing, then results/history; resolve shared-view wiring together.
4. Run `mvn verify`, play win/loss rounds, restart and reopen history, and test a failed API load.

## Remaining course requirements

The project still needs persistence and independently contributed use cases from both teammates. The three-person plan is implementation scope, not a guarantee of individual marks. The course explicitly checks each member's Git history and expects each member to write the majority of one use case. The 3D map remains optional and unimplemented. Final slides, rehearsal, and final deliverables are not completed by this code change.

Milestones from Quercus: near-complete prototype by November 27; polish/presentation preparation by December 4; presentations December 7/8. The repository URL was submitted late on November 21 at 08:47 EST; MarkUs warns that late changes are recorded but not graded, so course-staff acceptance remains unresolved.
