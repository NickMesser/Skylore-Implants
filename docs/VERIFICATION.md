# NeoForge 1.21.1 runtime verification

Verified on 2026-09-14 against **Minecraft 1.21.1, NeoForge 21.1.248, Java 21.0.11 (Temurin)**.

Tested file: `skylore-implants-neoforge-0.2.0+1.21.1.jar`

SHA-256: `b95b8c0e6ca40767d5c5fe8c5f6a8411ce03c85bc3fc49743f86f1e7a62b3bf0`

The jar loaded by the client is byte-identical to the one `./gradlew clean build` produced. It was
installed in the `mods` directory of an ordinary NeoForge 1.21.1 client, alongside Marionette 0.5.1.
No development source classes were loaded.

## How it was driven

The client exposed Marionette's HTTP API on `127.0.0.1:25602`. Worlds, the rig, the player, the
chamber menu and every assertion were driven over that socket; the game window was never focused and
no input was synthesized.

## Runtime results

### Registry sweep — `POST /test/sweep {"namespace":"augment"}`

| Category | Total | Failed |
| --- | --- | --- |
| blocks | 1 | **0** |
| items | 41 | **0** |
| models | 42 | **0** |
| lang keys | 42 | **0** |
| effects | 0 | **0** |

The sweep places every block and reads it back, gives one of every item, applies every effect, checks
every block and item model against the missing model, and checks every translation key. It is the
reason "all forty items exist and render" does not need forty hand-written assertions.

### Assertion suite — `augment-implants`

**15 of 15 tests passed, 0 failed.** Two worlds were used, one per run of the suite, both created by
the suite itself and named `augment-verify`.

| Test | What it establishes |
| --- | --- |
| the chamber's two halves place and read back | both block states, `half`, `facing` and `closed` |
| the chamber drops itself when it is broken | the 1.21 `loot_table` path and the `half=lower` condition |
| an implant recipe actually crafts | `IRI/CXC/IRI` in a real crafting table yields `augment:cyber_arm` |
| walking into the chamber closes the door and opens the menu | occupancy, both halves going `closed=true`, the menu opening with 10 synced properties |
| an implant installs into the slot it fits | the server accepts button `9` then `69`; torso reads catalog index 38; the item leaves the inventory |
| the installed implant puts its effect on the player | `minecraft:water_breathing` appears in `/state` |
| removing the implant gives the item back | torso returns to 0 and the item is in the inventory again |
| an implant that does not fit the selected slot is refused | Cyber Arm into Head is rejected, the item is kept |
| the placeholder implant cannot be installed at all | Cortex Connector is rejected |
| an implant with no matching item in the inventory is refused | the server also checks ownership, not just slot compatibility |
| the augmented nervous system shortens a negative effect | 100 s of Slowness lands as 1395 ticks instead of 2000 — the duration mixin is live |
| the cyber brain boosts the experience an orb gives | a 100-point orb grants ≥120 — the orb mixin is live |
| catalog implants and the chamber item are registered and obtainable | `give` succeeds and `inventory_contains` matches for a spread of IDs |
| the chamber closes again for a player who comes back | the transient lock is re-acquired after a release |
| the chamber opens again when the player walks out | the door releases when the occupant leaves |

Reports: `marionette/reports/augment-implants-final.json`, plus every intermediate run. The sweep
result is `marionette/reports/`-adjacent; the raw sweep answer was kept as JSON alongside the run.

### Persistence across a save

Driven as a separate sequence rather than a suite, because a suite cannot leave and re-enter its world:

1. create a void world, place the chamber, walk in;
2. install `synthetic_lungs` — torso property reads `38`, `/state` shows Water Breathing;
3. `POST /world/leave`, which saves;
4. `POST /world/open` the same save, walk back in.

Torso still reads `38` and Water Breathing is re-applied. This exercises the rewritten
`SavedData.Factory` / `HolderLookup.Provider` / `ItemStack.CODEC` path, which is the part of the port
with the largest API change and no compile-time safety net.

### Logs

The client log for the verified run contains **no `[ERROR]` lines at all** and no `augment`-related
warnings. `RecipeManager` reports `Loaded 1330 recipes`, so all 40 mod recipes parse. An earlier run
did log `Parsing error loading recipe augment:*` for all 40; that was a genuine 1.21 schema break
(`"result": {"item": ...}` had to become `"result": {"id": ...}`) and is fixed in the tested jar.

## Screenshots

- `screenshots/augment-chamber-gui.png` — the Modification Chamber screen with the player model, the
  body-slot picker and a filtered install list.
- `screenshots/augment-chamber-open.png` — the chamber in the world with its door leaves telescoped
  back into the frame, which is the custom block entity renderer running on the rebuilt vertex API.
- `screenshots/sweep-augment.png` — the sweep's top-down grid.

## Scope

This covers the behaviour listed above on NeoForge 21.1.248 with no other gameplay mods installed.
Not covered: a dedicated server, other NeoForge 21.1.x builds, other modpacks, multiplayer,
non-creative play, and the effects that need a mob or a fight to observe (shield disabling, Warden
targeting, ore bonus drops, fall damage, bow draw speed). Those code paths compile and are wired to
the same events as the tested ones, but they were not exercised here.
