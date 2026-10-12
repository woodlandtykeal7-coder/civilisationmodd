# Phase 7A proposal — floor-embedded autonomous workplaces

Status: **researched, architecture only; NOT implemented or gameplay-tested**.
Parent baseline: `integration/phase6-1-complete-stability` at `d26e6da` (keep Phase 6.1 intact).

## Player experience and the floor block

A Builder's Guild begins as a small real structure. Builders physically construct its foundation; one ordinary-looking **full-height floor block** in the building is the `civilisation:builder_hut_floor` marker. The block occupies **the same floor grid cell** that an oak-plank/stone floor would occupy. Its *top surface is flush with surrounding walkable floor*, not a decorative mini house on top. Its pixel-art top has an understated hammer/compass inset; sides use the same material family as the floor. No collision protrusion and no required empty block above it. Citizens **place the marker from a real carried crafted item** during the hut blueprint construction; do not spawn it for free or overwrite existing project progress.

The first builders operate temporarily from existing settlement camp storage and the existing construction queue. They can therefore build their own guild. Only **after real placement** does the new marker register/activate a completed or under-construction workplace. The project transitions work assignments from the camp queue to the building without restarting construction.

The hut remains a **complete built structure around the floor block**, with a two-wide main access path, actual tool storage, crafting/repair workstation and a connected physical supply chest. Its indoor work area must not block its doorway. Other workplaces use the *same foundation architecture*, with their own block texture + worker roles: farmhouse, forestry office, mine office, smithy/crafting hall, courier depot, teaching room, guardhouse. Buildings use modular original schematics, not copied MineColonies blueprints.

## Canonical data model — one workplace, many workers

**Authority belongs to server-side, saved settlement/workplace records**; the block entity is a placed-world anchor and cache, *not* a second authority for job ownership.

- `WorkplaceRecord` — immutable UUID; settlement UUID; human-readable `W-####`; role/type; dimension and block pos of the floor marker; linked `BuildingRecord` UUID; level; capacity; staffing policy; lifecycle (`PLANNED/BUILDING/ACTIVE/DAMAGED/MISSING_ANCHOR/RELOCATING`); linked endpoint IDs; assigned citizen UUID set; current shared work order IDs.
- `WorkplaceSavedData` — keyed by workplace UUID, indexed by dimension+block position and settlement+type; schema/version tags, datafixers if changed; idempotent load/re-register; never duplicate a record on chunk reload.
- `BuilderHutFloorBlock` — one registered NeoForge Block with walkable full-block collision, property orientation if necessary, right-click read-only status, pick block; compatible resource/loot/recipe definitions. Block can be naturally installed by builder from a real inventory stack.
- `WorkplaceBlockEntity` — registered NeoForge BlockEntityType, stores ONLY stable `workplaceId`, `settlementId`, and a recovery/version marker. Resolve the server authority each time; don't store worker inventories or duplicate live claims here.
- `WorkplaceService` — server-only reconciliation, incremental save, job eligibility, capacity, anchor placement/break/relocation events, loaded-chunk guard, and ordinary-world lifecycle. Missing marker pauses *new* work assignments but keeps cargo, projects, and records for repair. No teleport, free resources or silent deletion on break.
- `WorkplaceWorkboard` — a shared queue per workplace for `ConstructionProject` references; claim each block exactly once, favor physical inventory availability, and rotate fair access among workers. Different builders may work different reachable sections/projects; no assigned building or citizen ID is duplicated.
- `WorkplaceStaffingPolicy` — controlled by demand, availability of citizens, physical work area, workforce proficiency and settlement age. **Birth Era 1 active builder**, later additional builders at capacity gates (proposed Village 2, Town 3, City 5, *numbers to tune in game*). One workplace supports multiple staff; multiple huts are possible later, each with its own coverage radius and workload. Do not override normal meals, sleep, careers or citizen choice.

## Main flow

1. Housing/other construction demand appears via existing `BuildingDemandService`.
2. Existing `ConstructionService` creates a project, chooses an original hut blueprint if the first workplace is required, and validates no overlap with existing/registered buildings.
3. Citizens gather real timber/stone, craft the marker and hut materials through `SettlementEconomyService`, deliver to a physical construction stockpile.
4. Builder physically places the floor marker *as one counted blueprint block*, then the enclosing structure. Marker on-place registration is idempotent; project finish binds the `BuildingRecord`.
5. WorkplaceService connects eligible builders and assigns project queues. The builders physically walk, collect supplies and place blocks in loaded chunks only; tool and time/survival constraints remain active.
6. Once an expansion trigger is satisfied, citizens plan a **real material-funded workplace upgrade**, not an instant role-slot unlock. Staffing increases only after the upgrade is completed and validated.

## Current code to extend, not replace

- `SettlementState`: settlement identity, member set, saved storage coordinates. Extend through separate `WorkplaceSavedData` rather than overloading member persistence.
- `BuildingRecord`: actual completed structure ID and world anchor; link by ID, do not fake housing occupancy.
- `ConstructionService`: real project claims, stockpiles, block placement and final building registration.
- `ConstructionDecisionProvider`: currently accepts builders and several labor roles. Extend with capacity-aware workplace assignment only after stable linking.
- `SettlementEconomyService`: existing production requests, reservations and real container deliveries; no duplicates.
- `ModItems` and `CivilisationMod`: register block, BlockItem and BlockEntity using NeoForge 1.21.1 DeferredRegister. Existing mod does not yet register workplace blocks; do not use MineColonies' Java classes.

## Safety / correctness invariants

1. At most one `WorkplaceRecord` owns an anchor block, and an active worker belongs to at most one active role slot per profession.
2. `activeWorkers <= capacity`; assignment respects career, sleep, health and work time; idle builders may temporarily assist another worksite via an explicit reservation, not double ownership.
3. Work order and per-block construction claims are atomic on the server, persisted/reconciled on restart; the block entity is NOT a claim holder.
4. A marker's physical stack is consumed once on placement. Its inventory cannot duplicate when breaking/creative cloning/loading. Breaking a marker never magically repairs or deletes work.
5. A completed `BuildingRecord` is registered once; rebuilding or upgrading preserves the existing workplace UUID unless a verified relocation transaction occurs.
6. Each request is bound to a known settlement; verify distance/dimension and permissions before GUI access or registering a floor block. Ordinary block collision and block breaking apply.
7. If the existing house is built with no marker, don't overwrite the floor on load. Offer future **non-destructive voluntary conversion** or autonomous, material-funded retrofitting project.
8. Client skins and textures are all **new originals** (16×16 block pixel art, 64×64 citizen skin templates), with real JSON assets and optional layered variants. Generated concept art is a reference, not a Minecraft texture asset.

## Incremental coding order (keep separate review PRs)

- 7A.1: register marker Block/BlockItem/BlockEntity + NBT identity + original textures, loot/recipe, test placement/restore, no employment changes.
- 7A.2: `WorkplaceRecord`, versioned SavedData, anchor association, lifecycle and permissions, deterministic idempotent recovery tests.
- 7A.3: one-to-many worker assignments and a shared construction workboard with real staff-capacity gates; tests for claim collision and save/reload.
- 7A.4: automatic camp-to-hut upgrade as physical blueprint, ground-floor block placement, original workstation layouts and two-wide entry.
- 7A.5: apply tested workplace interface to farming, forestry, mining, crafting and courier offices; build distinct upgrade paths.
- Path improvements in `PHASE_07A_BUILDER_PATHFINDING_RESEARCH.md` should be separately tested before linking to busy shared huts.

## Sources / legal

Study of design only; **no MineColonies Java, textures, skins, schematics or assets copied**.

- MineColonies Builder AI: https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/entity/ai/workers/builder/EntityAIStructureBuilder.java
- MineColonies worker module: https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/colony/buildings/modules/WorkerBuildingModule.java
- MineColonies Builder hut block: https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/core/blocks/huts/BlockHutBuilder.java
- MineColonies repository licence is listed as GPL-3.0. This proposal is an independent specification, *not* a code port; do not copy GPL code or copyrighted game assets into this project without separately handling licence compliance.
- NeoForge 1.21.1 blocks: https://docs.neoforged.net/docs/1.21.1/blocks/
- NeoForge 1.21.1 block entities: https://docs.neoforged.net/docs/1.21.1/blockentities/

## Release boundary

Phase 6.1 remains unchanged. This is research/design only; no hut block or worker workplace logic is claimed to work yet. No merges without explicit user approval. Phase 7A development should begin in small testable implementations only after the user approves the concrete block appearance and behaviour.
