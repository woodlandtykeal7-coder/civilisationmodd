# Phase 7A.1 — Builder's Hut walkable floor-marker prototype

**Status: isolated experimental prototype, not a functioning multi-worker guild.**
Parent baseline: `integration/phase6-1-complete-stability`. Phase 6.1 code untouched.

- Registered `civilisation:builder_hut_floor` as an ordinary full-height solid floor block, with `BlockItem` and an original 16×16 top texture (hammer mark).
- Full-cube walkability: its upper face is exactly flush with other full blocks of a constructed floor. It is **not** a block-sized miniature house.
- Simple survival crafting recipe: four oak planks + one iron nugget yields one physical marker; item consumption is ordinary Minecraft crafting, no automatic free supply.
- Persistent NeoForge `BuilderHutFloorBlockEntity` contains only optional workplace+settlement UUID pointers and supports idempotent server-side binding. An unbound marker does not give any NPC a job. The future `WorkplaceSavedData` will become the authority for workers, capacities, building ownership and lifecycle; not the block entity.
- Original custom top texture; ordinary vanilla oak plank side and bottom are a provisional model, **not** MineColonies artwork.
- Uses normal block loot and model JSON. No instant structures, no server teleport, no hidden magical materials.
- Does not modify existing starter-house blueprints or saved buildings. Existing worlds are not altered on load.
- JUnit tests verify identity save/load, unbound state and UUID separation. Real-world placement and chunk reload still require Minecraft gameplay testing.

**Next increments, deliberately not claimed as implemented here:**
1. Server-authoritative workplace registry and saved records; ownership validation and missing-anchor reconciliation.
2. Guild hut blueprint that physically places this marker as a counted floor block using a carried inventory item, then constructs walls and roof around it.
3. Connect multiple builders through a shared guild work queue, expand hiring by settlement stage and physical resources, and support the other profession offices.
4. Hook up future workplace dashboard and staff controls, and test save/reload plus 2+ simultaneous building projects.

Do not merge this prototype without user approval and an explicit in-game test.
