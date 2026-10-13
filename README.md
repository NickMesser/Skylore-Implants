# Skylore Implants

A Minecraft **1.21.1 NeoForge** mod with 39 functional cybernetic implants, a documented Cortex Connector placeholder, and a two-block-tall walk-in modification chamber.

## Install

Use Java 21, Minecraft 1.21.1, and NeoForge 21.1.248 or later in the 21.1.x series. Put the Skylore Implants jar in the `mods` directory on both the client and server. There is no other required dependency; the mod uses NeoForge's own registry and event APIs.

The 1.20.1 Forge build this replaces lived on Architectury API. That dependency is gone: NeoForge 21.1 supplies everything the shared layer needed, so nothing else has to be installed alongside the jar. See [docs/PORTING.md](docs/PORTING.md) for what moved and where.

## Use

1. Craft a Modification Chamber, or find it and the implant items in the **Skylore Implants** creative tab.
2. Place it on a floor with two free blocks above the floor. The doorway faces you.
3. Carry an implant and walk into the chamber. The six door leaves close over 1.4 seconds, then the augmentation screen opens.
4. Select **head, torso, right/left eye, right/left hand, right/left arm, or right/left leg**. Hover an implant to read its effect. The list shows all compatible implants and how many you carry.
5. Install consumes one item. Each body slot holds one implant. Remove returns that item with its data components intact; a full inventory drops the returned item at your position.
6. Choose **Finish & exit**, or press Escape, to reopen the chamber. Walk out through the front.

The left panel displays your actual player model with a short zoom-in transition. Hyper Vision Lens uses **Z** to zoom while playing; rebind it in Controls. Installed implants survive death, dimension travel, and restarts.

Every installed implant shows on your body, and other players see it too. Each one sits on the body part it is installed in and moves with it. Implants fit under worn armour, so a helmet, chestplate, leggings or boots hide them. Lights on the implants glow in the dark.

Cortex Connector is intentionally blank: it has a clearly labelled placeholder tooltip and the vanilla structure-void placeholder icon, with no effect, recipe, or installable body slot.

See [the complete implant and recipe catalog](docs/IMPLANTS.md). Each implant recipe uses four iron ingots, two copper ingots, two redstone, and its unique core ingredient. These recipes are initial balance choices because the cards do not prescribe recipes.

## Build

With Java 21 selected:

```powershell
./gradlew.bat build
```

The installable jar is `neoforge/build/libs/skylore-implants-neoforge-0.2.0+1.21.1.jar`. The `-sources.jar` is for development. Gradle resolves Minecraft and NeoForge through the ModDevGradle plugin from the official NeoForged Maven repository.

## Run the development client

With Java 21 selected, run this from the project directory:

```powershell
./gradlew.bat :neoforge:runClient
```

This starts the NeoForge development client with Skylore Implants. Marionette is not included in this project, its dependencies, or its distributable jar. Automated testing tools are maintained separately.

## Project layout

- `common/`: registries; implant catalog, persistence and behavior; chamber block, menu, renderer and screen; vanilla mixins; textures and recipes.
- `neoforge/`: NeoForge entrypoint, networking and events, extended attribute bindings, client event registration and build configuration.
- `art/implants/`: body-model source. `implant_art.py` holds every implant's cubes, paints the textures, checks each one fits under armour, and exports the models the game draws. `models/` holds the matching Blockbench files.
- `docs/PORTING.md`: the loader boundary and what a Fabric port would still need.
- `docs/VERIFICATION.md`: the runtime evidence for this build.

The common sources are compiled straight into the NeoForge jar using official Mojang mappings. They contain no loader imports at all; the only loader surface is the two `Platform` function fields that `neoforge/` fills in.

The display name is **Skylore Implants**. The internal mod ID and registry namespace remain `augment` to preserve chambers, inventories, and installed implants in worlds created with the initial build. Commands continue to use IDs such as `augment:modification_chamber`; the original Trello item names, including Augment Brain Chip and Augment Shield, are unchanged.

## Assets and provenance

The [Trello Implants column](https://trello.com/b/FPK4H0oH) supplied the 39 item names, effect descriptions, supported slots, and original 32×32 textures. The forty-card snapshot is in `common/src/main/resources/assets/augment/implants.json` with links to each source card.

The chamber is derived from the supplied Mystical Index `art/blockbench/modification_chamber` assets. The runtime geometry retains all 214 cubes, face UVs, the three original textures, and six telescoping door groups. Its original 2.5-block height is adapted to two blocks, preserving a 12-unit-wide entrance and 29.5-unit-high interior. GUI panels and the player zoom are rendered in code. Asset ownership remains with their respective authors; no third-party assets are relicensed here.

The editable original is included in `art/modification_chamber`. After editing it, run `python tools/export_chamber.py` to regenerate the runtime quads and inventory model. The exporter performs the two-block height adaptation.
