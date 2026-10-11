# Civilisation

Minecraft Java 1.21.1 / NeoForge 21.1.235 mod. Phases 0–3 are implemented: persistent citizens, autonomous daily routines, settlements, and a physical resource/production economy with explicit inventory ownership, reservations, couriers, workshops, recipe-driven crafting, vanilla furnaces, and durable tools.

The Phase 1 presentation pass synchronises only a compact semantic activity (`IDLE`, `WALKING`, `WORKING`, `EATING`, `SLEEPING`, or `RECOVERING`). The server-side brain and tasks remain authoritative. Tracking clients use that state for arm movement, a visual-only held food item, the normal sleeping pose, and short damage recovery without driving task results or inventory changes.

This is deliberately not yet a full civilisation simulator. Currency, construction, careers, research, trade, government, diplomacy, and war remain out of scope until later phases.

## Requirements

- JDK 21
- Minecraft Java 1.21.1
- NeoForge 21.1.235 or later in the 21.1 line

## Build and test

```sh
./gradlew test build
```

The distributable JAR is created under `build/libs/`.

For a development client that immediately joins a running local development server:

```sh
./gradlew runClient -PquickPlayServer=localhost:25565
```

## Commands

- `/civ spawn`, `/civ list`, `/civ inspect <citizen>`
- `/civ setup` (single-citizen Phase 1 developer setup)
- `/civ colony setup` (reproducible 12-citizen Founders Camp)
- `/civ colony create`, `/civ colony list`
- `/civ colony info <settlement>`
- `/civ colony citizens <settlement>`
- `/civ colony resources <settlement>`
- `/civ colony economy <settlement>` and `/civ colony production <settlement>`
- `/civ colony request <settlement> <minecraft:item> <quantity>`
- `/civ colony join <settlement> <citizen>` and `/civ colony leave <citizen>`
- `/civ colony register-storage <settlement>` (stand on a physical container)
- `/civ debug colony setup` (legacy developer alias for the same setup)
- `/civ debug citizen <citizen>` and `/civ debug citizen <citizen> needs <hunger> <fatigue>`
- `/civ debug reservations|deliveries|workshops <settlement>`
- `/civ validate`

`/civilisation` remains an alias for compatibility. Citizen selectors accept a readable ID (`C-0001`), unique name, full UUID, `nearest`, or `looking`; settlement selectors accept readable ID, unique name, or UUID.

Commands require permission level 2. `CitizenRecord` is authoritative; `CivilianEntity` is only its loaded physical body, and `CitizenBrain` exclusively owns that body's active physical task. A citizen record is retained when its body unloads, goes missing, or dies.

`/civ validate` reports citizen and settlement records, active and duplicate bodies, broken references, invalid assignments/storage, membership errors, active work claims, and an overall `PASS`/`FAIL`. One active body for one loaded citizen is expected and is not an error.

## Phase 2 notes

- Settlement membership is explicit and settlement-owned; a citizen persists only the settlement reference.
- Registered Minecraft containers are the authority for shared stock. The runtime resource index is a reconciled cache, and reservations cannot exceed the last physical observation.
- The work board scans a bounded 24-block radius incrementally by column, caches candidate targets, and uses expiring one-target claims instead of persistent per-block jobs.
- Harvest drops are produced by vanilla block loot, carried in the citizen inventory, and deposited into registered storage. Crops consume one real harvested seed/item when replanted.
- The spawn egg follows the normal record creation and binding path; it cannot create an unregistered body.

## Phase 3 notes

- Items exist only in Minecraft inventories, a citizen inventory, furnace slots, or dropped item entities. The resource index is an incrementally reconciled observation cache, never ownership.
- Every automatically accessed container is a persistent endpoint with explicit settlement/workplace ownership. Unregistered player containers are ignored.
- Reservations are endpoint-scoped, persistent, partial, expiring, and protected-reserve-aware. A reservation is a claim, not an item stack.
- Couriers physically load reserved stacks, carry them in their persistent citizen inventory, and unload them. Full or broken destinations preserve cargo and trigger rerouting/retry.
- Production requests are persistent and use the live RecipeManager through an output cache. The planner has bounded recursion and cycle detection and reports blockers.
- Craft execution preflights output capacity, consumes real input stacks, and inserts real output atomically. Smelting loads real input/fuel into a vanilla furnace and waits for its actual output slot.
- Lumberjacks must carry an axe; the actual carried stack loses durability. Replacement tools are checked out from explicitly owned physical storage.
- No currency, coins, wages, prices, taxes, banking, or monetary economy exist.

## Phase 4 construction foundation

- Persistent projects use immutable, fully resolved blueprints and separately registered completed buildings.
- Temperate Timber, Desert Masonry, and Jungle Elevated profiles separate cultural shape rules from locally resolved materials.
- Construction requirements enter the Phase 3 reservation, production, and delivery pipeline and arrive at a real registered stockpile.
- CitizenBrain-owned builders claim individual placements, physically collect blocks, walk to the site, and consume one carried stack per successful server-side placement.
- `/civ colony build <settlement> starter_house`, `/civ colony construction <settlement>`, `/civ colony buildings <settlement>`, and the construction debug commands support development testing.

## Visual interaction notes

- Work animation is model-side presentation driven by the synced `WORKING` state; task timing remains server-side.
- Food is transferred from the real container on the server. The held stack is a count-one synced display copy that is never inserted, dropped, or consumed as gameplay inventory.
- Minecraft 1.21.1's `ChestBlockEntity.startOpen`/`stopOpen` API requires a `Player` and menu interaction. A civilian therefore sends the vanilla chest block event and open/close sound/game event while the actual container mutation remains server-authoritative; it never opens a GUI or invents a fake player.
- Sleeping resolves an assigned bed foot to the head block before calling vanilla sleeping APIs, preserving the vanilla full-body pose and facing.
- Common/server code does not import client-only classes. Renderer/model code is isolated under `com.tyes.civilisation.client`.
