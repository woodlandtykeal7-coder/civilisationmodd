# MineColonies builder/pathfinding research → original Civilisation implementation plan

Status: architecture only; do **not** assume MineColonies code can be copied into our Minecraft 1.21.1 NeoForge mod. Phase 6.1 working baseline must be maintained.

## Exact source inspection (MineColonies `ldtteam/minecolonies`, `version/main`)

1. **Builder AI `EntityAIStructureBuilder.walkToConstructionSite` (approx lines 207–270)**: tracks a `workFrom` standing location near the target; invokes `PathJobMoveCloseToXNearY`, reuses the path result, and drops `workFrom` if navigation reports being stuck or world geometry changes. The builder does not naively path to the solid block being placed.
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/ai/workers/builder/EntityAIStructureBuilder.java#L207-L270
2. **`PathJobMoveCloseToXNearY` (approx 34–100)**: end-point search accepts walkable nodes near the desired position rather than a single exact coordinate, uses a secondary nearby reference point and a cost heuristic, and rejects same-column endpoints. Some of these behaviors are useful in a new, bounded NeoForge-compatible helper without replacing vanilla A*.
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/pathjobs/PathJobMoveCloseToXNearY.java#L34-L100
3. **`PathingStuckHandler` (approx 160–258, 329–425)**: distinguishes path existence from meaningful progress, maintains timers and increasing recovery steps, sometimes moves the actor away and recalculates. Some configs permit skip-node, ladder/block edits and even teleport. **We reject teleport, arbitrary block destruction and magical scaffolds** for the user's survival/autonomy requirements.
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/PathingStuckHandler.java#L160-L258
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/navigation/PathingStuckHandler.java#L329-L425
4. **`RecentTargetCache`**: temporarily penalizes recently used destination positions so workers spread out rather than repeatedly competing for one spot. This suggests a short-lived shared stance reservation/penalty per worksite.
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/pathfinding/RecentTargetCache.java
5. **`BuildingStructureHandler` / base builder structures**: use distinct placement phases, progress position, needed-resource logic, and worker positioning near blocks. Our own blueprint and supply systems already do some of this and must remain authoritative.
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/ai/workers/util/BuildingStructureHandler.java
   https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java

## Contrast with Civilisation 6.1 — what exists and what still fails

- `ConstructionInteractionPlanner.candidates`: enumerates real stance cells within 3 horizontal blocks on multiple height layers. `aimPoints`: exposed corner/edge/top rays. `FailureMemory`: 240-tick per-task local cooldown and alternating-target detection. This already follows the *safe near-block positioning* principle.
- `BuilderTask.moveToSite`: collision, support, line-of-sight/reach, and vanilla path are checked, with complete routes ranked before partial ones; a partial segment must actually move closer before replanning.
- `BuilderTask.travelToSite`: checks positions every 20 ticks; tracks movement/oscillation; bounded replans, then releases the current task. **This can cause task handoff ping-pong** if an entry is temporarily busy, as witnessed in-game. Real construction eventually completed, so don't replace the planner wholesale.
- Existing `ConstructionProject.claims`: prevents duplicate block claims but does **not** reserve separate builder *standing positions* or door-lane transit access, particularly when many builders enter/leave a one-wide doorway.
- Existing per-task failure memory is not shared across multiple builders. A successor may retry a recently unreachable or crowded access target and independently reach the same obstruction.
- The physical/material pipeline was stabilised in 6.1D; access recovery must not fabricate supplies or drop citizen inventory.

## Proposed original improvements, in ascending risk

### A. Shared short-lived worksite stance leases — first targeted feature

Create server-only `ConstructionStanceCoordinator`, keyed by settlement UUID + project UUID + dimension + stance block position. Leases hold citizen UUID, expiry tick and current target block index. Before adopting an otherwise valid vanilla route to a stance, check whether it is reserved by another builder and whether taking it would obstruct the sole doorway. Prefer unreserved stance cells and leave one 2-block-wide transit lane available. Release leases on success, change of task, death, unload, waking from sleep or timeout; never treat a lease as a block or inventory resource.

Strict invariants: **at most one owner per stance**, idempotent reacquire by owner, expired leases recover, project completion releases all, a chunk load/unload cannot keep a stale lease forever, no global static leak between servers. Add unit tests for contention, expiry and release.

### B. Temporary human congestion ≠ impossible geometry

When vanilla navigation stalls and another **moving citizen is in the next few path nodes**, classify transient congestion and:
- politely yield / step aside to a loaded, collision-safe **unreserved** ground square, or wait for a short bounded interval, then recalculate;
- retain current `ConstructionProject` claim and tracked physical cargo during this short waiting period;
- do **not** blacklist target stance just because another worker temporarily occupied it;
- after a bounded wait, change stance or hand off the claim while preserving carried items; never claim success without placing a real block.

Require a fast, bounded nearby entity query rather than scan the entire settlement. Use per-NPC cooldown so hundreds of citizens can't repeatedly compute paths every tick.

### C. Route-quality and change-aware replanning

Extend `BuilderTask` progress classification:
- `REACHED`: actual reachable stance plus reach and ray checks.
- `PARTIAL_WITH_PROGRESS`: cropped path end genuinely closer, continue from endpoint.
- `TEMPORARILY_OBSTRUCTED`: entity/path occupancy or doorway lane conflict; wait/yield/replan, don't immediately mark permanent candidate failure.
- `STATIC_UNREACHABLE`: support/collision/visibility/path invalid after multiple attempts; choose different stance or material-funded scaffold access.
- `WORLD_CHANGED`: completed blocks changed the corridor geometry; discard cached route and recompute.

Score usable candidates by actual path node count, reachable vs partial, vertical movement, exposed face/ray quality, recent-target cooldown, reserved stance collision and avoided doorway zones. Keep server-side path query count capped, and first test on a house roof, narrow entry, hillside and multiple workers.

### D. Safe building sequence and exterior access

Continue foundation/walls-before-roof, but group assignments by nearby work zones so multiple builders can build **different fronts or sections** without walking through each other. Last enclosed entrance/roof placements should be delayed while couriers still must enter. Offer an **original material-funded scaffolding task** when a legitimately blocked upper block cannot be reached. No placing through walls, mining protected structure, creative instabuild or teleport.

### E. Telemetry and acceptance

Add low-rate diagnostics: project ID, target block, selected stance, path complete/partial, progress samples, `blocker=TEMPORARY_CONGESTION|STATIC_NO_PATH|NO_MATERIALS|WORLD_CHANGED`, retry count, lease status. Expose debugging via existing `/civ debug construction S-0001` or a dedicated inspect command; avoid chat spam.

Acceptance tests:
1. Two builders approach the same 1-wide doorway from opposite sides; one yields, neither abandons or duplicates cargo; both make progress within a bounded time.
2. Two builders select different stances for separate claimed target blocks; coordinator prevents overlap.
3. Finish multiple real starter houses on ordinary landscape; roof access via physical travel, no teleport.
4. Save/reload during partial path and builder carry; no duplicate placements, invisible cargo, or orphaned claims; `/civ validate` PASS.
5. Sleeping/waking and settlement dashboard still work exactly as in Phase 6.1; no world migration and no deterioration in performance with 12+ citizens.
6. A genuinely impossible route is reported as blocked rather than spinning, teleporting or destroying surrounding blocks.

## Implementation boundary

Research and design ONLY in this PR. Do not merge this into `main` as a feature. First implement **A** with dedicated unit tests on a new branch based on Phase 6.1, then integrate **B** after in-game A testing; C/D/E are separate extensions. Do not blindly transplant MineColonies (GPL-3.0) code or copy graphical assets.
