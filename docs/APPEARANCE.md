# Civilian appearance architecture

Phase 2 stores an immutable `CitizenAppearanceProfile` on each `CitizenRecord`. Its stable
`civilisation:civilian_NN` ID selects an immutable definition in `CitizenAppearanceService`.
The current definitions use Minecraft's resource-backed wide-arm default human skins as complete
base textures; rendering never derives or rerolls a profile.

The saved profile also contains independent skin, hair, hair-colour, clothing, and clothing-colour
indices. A later data-driven catalogue can map those same fields to separate renderer layers in
this order: base body, hair, civilian clothing, profession clothing, real equipment/armour, age,
then cultural accessories. That migration should retain existing profile IDs and index values,
replace only the definition catalogue, and add renderer layers; it does not require changing
citizen UUIDs or rerolling saved appearances.

Only a compact catalogue index is synced through entity data. The persistent record remains the
authority, and missing client resources fall back to the valid wide-arm Steve texture with a
once-per-resource warning.
