# Loader portability

The shipped target is **NeoForge 21.1.248 for Minecraft 1.21.1 on Java 21**, with no required companion mod. A shared source layer is present; a Fabric release is not claimed without building and testing that target.

The previous target was Forge 1.20.1 / Java 17 with Architectury API 9.2.14. Architectury is no longer a dependency: its only uses were `DeferredRegister`/`RegistrySupplier` for registration and `EventBuses.registerModEventBus` to reach the mod bus, and NeoForge 21.1 provides both directly. See "What the 1.21.1 port changed" below.

## Shared code

`Augment` uses NeoForge `DeferredRegister` and `DeferredHolder` for items, blocks, block entities, menus and the creative tab. Shared gameplay depends on vanilla Mojang-mapped classes only. `ImplantCatalog`, `BodySlot`, `ImplantData`, `ImplantEffects`, chamber classes, screens, the renderer, and resource files are shared.

`neoforge/build.gradle` compiles `common/src/main/java` and `common/src/main/resources` straight into the NeoForge jar through ModDevGradle. A Fabric module can do the same using Loom and official mappings, or the project can be migrated to Architectury Loom. Do not compile the shared code twice into one jar.

## Adapter contract

Everything loader-specific lives in `neoforge/`. A second loader has to supply the same surface:

| Service | Current NeoForge implementation | Port requirement |
|---|---|---|
| Registration bootstrap | `Augment.init(IEventBus)` called from the `@Mod` constructor | Invoke `Augment.init(...)` at the loader's registration stage |
| Loadout sync | `CustomPacketPayload` (`Loadout`) registered on `RegisterPayloadHandlersEvent`, sent with `PacketDistributor.sendToPlayer` | Send only the recipient's loadout on join, respawn, travel and change; set `ImplantData.client` on the client thread; clear it on logout |
| Extended attributes | `Platform.attributes`, `Attributes.BLOCK_INTERACTION_RANGE`, `ENTITY_INTERACTION_RANGE`, `STEP_HEIGHT`, `NeoForgeMod.SWIM_SPEED` | Bind equivalent attributes or carefully scoped mixins; keep the modifier `ResourceLocation` ids identical |
| Server tick | `PlayerTickEvent.Post` | Invoke `ImplantEffects.tick` once per server player per tick |
| Mining and harvest | `PlayerEvent.BreakSpeed`, `HarvestCheck` | Forward speed and stone-tool harvest behavior |
| Healing, fall and damage | `LivingHealEvent`, `LivingFallEvent`, `LivingIncomingDamageEvent` | Preserve modifier order, damage type checks, and direct melee checks |
| Shield disabling | `AttackEntityEvent` before blocked-hit damage | Disable blocking shields for five seconds on an eligible melee hit |
| Food and drawing | `LivingEntityUseItemEvent.Finish` and `.Tick` | Add food after consumption; reduce bow/crossbow draw time to 85%, once per item-use tick |
| Toxin immunity and targeting | `MobEffectEvent.Applicable` and `LivingChangeTargetEvent` | Reject poison, notify Giga Ear once when targeted, and reject sneaking Sensory Module users for wardens |
| Client registration | `@EventBusSubscriber(value = Dist.CLIENT)`: `RegisterMenuScreensEvent`, `FMLClientSetupEvent`, `RegisterKeyMappingsEvent`, `ViewportEvent.ComputeFov`, `RenderGuiEvent.Post`, `ClientTickEvent.Post`, `ClientPlayerNetworkEvent.LoggingOut` | Register the screen, renderer, keybind, FOV modifier, HUD and target-health display on the client only |
| Creative tab | `DeferredRegister<CreativeModeTab>` with `CreativeModeTab.builder()` | Create an equivalent tab using the loader API |

The common mixins implement orb XP, movement hunger, negative-effect duration, game-event silence, Warden eligibility, ore drops, and client item/orb outlines. Ore detection uses the `c:ores` common tag, falling back to the legacy `forge:ores` so packs that still define it keep working.

NeoForge 1.20.2 and later run mods on official Mojang names, so the mixin configuration carries **no refmap** and its `compatibilityLevel` is `JAVA_21`. Mixin targets are written against official names in both the annotation values and the descriptors. A Fabric port needs its own intermediary-mapped refmap and a remap step.

## What the 1.21.1 port changed

Version-level changes that are not loader-specific, all of which are in the shared layer:

| Area | 1.20.1 | 1.21.1 |
|---|---|---|
| Attribute modifiers | `UUID` + `ADDITION` / `MULTIPLY_TOTAL` | `ResourceLocation` id + `ADD_VALUE` / `ADD_MULTIPLIED_TOTAL` |
| Stored item stacks | `ItemStack.save` / `ItemStack.of` on raw NBT | `ItemStack.CODEC` with `NbtOps`; slot values are item-component compounds that still carry a top-level `id` string, so the fast `count`/`has` path stays cheap |
| `SavedData` | `computeIfAbsent(loader, factory, name)` and `save(CompoundTag)` | `new SavedData.Factory<>(ctor, loader)` and `save(CompoundTag, HolderLookup.Provider)` |
| Mob effects | `MobEffects.X` are `MobEffect` constants | they are `Holder<MobEffect>`; compare `.value()` |
| Block | `use(...)`, `void playerWillDestroy(...)` | `useWithoutItem(...)` / `useItemOn(...)` returning `ItemInteractionResult`, `BlockState playerWillDestroy(...)`, plus a required `codec()` |
| Menu screens | public `MenuScreens.register` | private; use `RegisterMenuScreensEvent` |
| Item tooltips | `appendHoverText(ItemStack, Level, ...)` | `appendHoverText(ItemStack, Item.TooltipContext, ...)` |
| Vertex writing | `vertex`/`color`/`uv`/`overlayCoords`/`uv2`/`normal`/`endVertex` | `addVertex`/`setColor`/`setUv`/`setOverlay`/`setLight`/`setNormal`, no `endVertex` |
| Entity ignition | `setSecondsOnFire(int)` | `igniteForSeconds(float)` |
| Datapack folders | `recipes`, `loot_tables`, `tags/blocks` | `recipe`, `loot_table`, `tags/block` |
| Recipe result | `"result": {"item": ...}` | `"result": {"id": ...}` |
| `ResourceLocation` | public constructor | `fromNamespaceAndPath` |

## Persistence and transaction rules

`data/augment_implants.dat` in the overworld holds one compound per player UUID. Keys are the ten body-slot IDs and underscore-prefixed internal XP/cooldown fields. Slot values are `ItemStack.CODEC` output, so they are 1.21 item-component compounds rather than 1.20.1 legacy NBT. Keep IDs stable and migrate explicitly if the shape changes again. A 1.20.1 player file is not read by this build; the slots would have to be re-encoded.

Only the server accepts installs/removals. It checks the open chamber, occupant UUID, living player, proximity, item ownership, compatible slot and vacancy before splitting one item. The menu button protocol uses IDs 0–9 for selecting the target slot, 10–19 for removal, and 32–71 for installing the catalog entry. This stays inside the signed-byte button packet. Preserve ordered delivery, or replace it with one validated custom packet when extending the catalog beyond 96 entries.

Chamber occupancy is deliberately transient. Chunk reloads and restarts recover a saved closed state to an unlocked door. GUI close, disconnect, death, teleport-away and block removal release occupancy. Do not persist an exclusive lock without a recovery path.

## Validation for each new loader

Use a separate test instance and the exact distributable jar. Check registration/assets, normal two-block placement in all four orientations, walking entry, closing/opening, each compatible slot, item/data-component conservation, packet rejection, full inventory removal, death/travel/restart persistence, and the effect behavior. Test dedicated-server class loading separately from client loading. Keep Marionette tooling and test worlds outside this source project; `docs/VERIFICATION.md` records what the NeoForge 1.21.1 build was actually run through.
