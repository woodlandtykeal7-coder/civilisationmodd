# Phase 6.1 — Consolidated stability and acceptance

This branch is a **single Phase 6.1 test candidate** based on `fix/phase6-1d-batched-supply-recovery`, which already contains the earlier 6.1A dashboard, 6.1B construction/roof and 6.1C bed-collision changes. No individual fix branches need to be merged first.

## Already observed in Minecraft (previous branches)

- A starter house reached completion and was registered as an actual building.
- `/civ validate` returned **PASS** after that construction.
- The completed building record opened correctly from the Buildings dashboard.
- A sleeping citizen resisted ordinary collision pushes and woke normally.
- Later, a house completed and integrity validation passed on the supply recovery test branch after workers retried an obstruction.
- These observations are **not equivalent to an end-to-end test of this consolidated branch**.

## Newly reviewed in this final pass

- The small-GUI dashboard previously enforced minimum pixel dimensions larger than the scaled window, stacked seven tabs vertically even when too short, and allowed unbounded scrolling.
- The consolidated candidate preserves the regular dashboard at normal sizes; uses one compact, cycling tab selector only on small panels; clamps the scroll against actual rendered rows; ignores list clicks outside the visible content.
- Added fast pure-geometry JUnit tests. Existing tests cover roof access selection and material batching/reservation rules.

## Automated checks

Run `./gradlew test build` using Java 21. GitHub Actions CI must independently complete successfully on the HEAD of **this branch**. Note: `PhysicalEconomyConservationTest` remains **@Disabled**, therefore it is NOT counted as a passed physical-inventory integration test.

## Required in-game QA on a backed-up test world

1. **Identity and save:** `/civ validate` before and after saving/reloading; verify active citizen IDs and the already registered house are unchanged.
2. **Construction:** monitor `/civ colony construction S-0001` while a real housing demand creates a new project. Watch lower walls build before the rear-to-entry roof, with no magic materials. A builder may retry or hand off a temporarily blocked task; report if stalled.
3. **Crafting and logistics:** `/civ colony production S-0001`, `/civ debug deliveries S-0001`, `/civ colony resources S-0001`. Watch 1 log -> 4 planks at the actual workshop, couriers bring planks to the project's physical chest, and builders resume. Verify source/transport/workshop/project counts before and after. Repeat after save/reload while a delivery is active.
4. **GUI normal and small scale:** right-click a citizen; check readable unblurred dashboard, all tabs, Building list click on the completed house, multiple project rows with long reasons, scroll bounds, and no clicking hidden entries. In a smaller Minecraft GUI window, use < and > to reach every tab; return to the original scale afterward.
5. **Sleep:** push gently against an NPC while it is on a bed; it must stay aligned to the bed, then wake, walk, and resume work correctly. Save/reload during sleep.
6. **Knowledge preservation:** inspect existing recipe/knowledge progress after save/reload. No knowledge changes are included in this branch.
7. **Integrity:** `/civ validate` returns PASS; no duplicated cargo, deleted stacks, invisible building progress, or unknown citizens.

## Known limitations (not automatically included in Phase 6.1)

- No true doorway yielding/traffic-control system yet: workers can still block each other, retry or hand off jobs.
- No automatic one-builder-only Birth Era workforce rule; planned as a separate approved milestone.
- A genuinely unreachable roof block may still require resource-funded physical access.
- No offloaded-world physical progress, no free materials, no teleporting and no invented currency.

## Release gate

Do **not** merge the PR or begin Phase 7 until the integrated branch CI passes, manual Minecraft QA passes, and the user explicitly approves. If any in-game test fails, add a minimal repair to this same branch and repeat the failed gate.
