# Copper Golem Plus

**A Fabric mod for Minecraft Java Edition 1.21.10** that gives copper golems
persistent chest memory and intelligent item routing — they remember where
they've deposited items, search smarter when a chest is full, and patrol
their known chests during idle to keep their memory fresh.

## Features

- **Chest snapshot memory** — each golem remembers up to 20 chests it has
  interacted with, storing a full inventory scan (`Map<Item, Integer>`) per
  chest. Persisted with the entity NBT across save/load so memory survives
  chunk unloads and server restarts.
- **Multi-chest deposit search** — when holding an item, the golem checks
  all known chests (newest-first) for one with room. Falls through to
  vanilla's chunk scan if none of its remembered chests have space.
- **Stack-size-aware eviction** — when memory is full (20 chests), the
  least useful chest is evicted: most full (slot-weighted by `maxStack`
  to correctly compare 64-stack items, 16-stack items, and non-stackable
  tools/armor). Tiebroken by newest timestamp (keeps the older, more
  stale snapshot since it's more valuable to keep around).
- **Proactive memory patrol** — during idle, the golem periodically visits
  the 5 oldest chests to refresh their snapshots. 60-second cooldown
  between patrol cycles. Automatically stops patrolling once all chests
  have been refreshed recently.
- **Vanilla fallback** — when no known chest has the held item, the golem
  searches nearby chunks for *new* chests it hasn't memorized yet, skipping
  any it already knows about.

## How it works (detailed)

### Deposit flow

```
Golem picks up items (e.g. 16 copper ingots)
  ↓
MoveItemsTask.findStorage fires
  ↓ (mixin intercepts at HEAD)
MoveItemsTaskMixin.onFindStorage:
  1. Calls ItemMemory.findChestsWithItem(copper_ingot)
  2. Iterates known chests (newest-first)
  3. For each: validates the chest still exists, checks canInsert
     + hasInsertSpace (empty slot or matching stack with room)
  4. Returns the first valid chest
  5. If none valid → falls through to vanilla chunk scan
  ↓
Golem travels → queues → enters INTERACTING state
  ↓ (mixin fires every tick)
tickInteracting HEAD:
  → scanInventory(storage.inventory)
  → recordChest(pos, contents, worldTime) — upsert by position
  ↓ (tick 60)
placeStack succeeds → items deposited
placeStack fails (all full) → invalidateTargetStorage → findStorage runs again
```

### Patrol flow

```
Golem idle, no items to sort
  ↓
MemoryRefreshTask (added to Activity.IDLE at priority 1):
  - Cooldown ticks down only during true idle (when MoveItemsTask isn't running)
  - At 0: picks the 5 oldest chests via findOldestChests(5)
  - Sets WALK_TARGET to chest #1
  - On arrival within 2.5 blocks: scanChest → recordChest
  - Advances to chest #2, #3, ..., #5
  - Finishes → cooldown resets to 1200 ticks (60s)
  - If all chests are fresh (<60s since last scan) → skips patrol entirely
```

### Eviction flow

```
recordChest called when memory already has 20 entries:
  ↓
evictLeastImportant:
  For each chest:
    fullness = Σ(count / item.getMaxCount()) across all item types
    (normalizes: 64 cobblestone = 1.0 slots, 16 ender pearls = 1.0 slots,
     1 diamond sword = 1.0 slots)
  Evict chest with HIGHEST fullness (most full = least useful)
  Tiebreak: evict NEWER chest (keep the older, more stale one)
```

## Installation

1. Install **Fabric Loader** ≥0.16.0 for Minecraft 1.21.10
2. Install **Fabric API** ≥0.138.0+1.21.10
3. Download the mod jar and place it in your `mods/` folder
4. Run the game

### Quick test

```minecraft
/summon minecraft:copper_golem ~ ~ ~
```

Place a chest or copper chest nearby, give the golem an item (drop it near
the input chest), and watch it pick up and route the item.

## Building from source

### Prerequisites

- Java 21 (JDK)
- Internet connection (first build downloads Minecraft sources)

### Commands

```bash
./gradlew genSources       # (optional) decompile Minecraft for reference
./gradlew build            # produce the mod jar in build/libs/
./gradlew runClient        # launch a dev client with the mod loaded
./gradlew runServer        # launch a dedicated server
./gradlew clean            # remove build artifacts
```

The output jar is at `build/libs/coppergolemplus-*.jar`.

## Project layout

```
src/main/java/com/copperplus/enhanced/
  CopperGolemPlusMod.java          — mod entrypoint (ModInitializer)
  memory/
    ChestSnapshot.java             — record (BlockPos, Map<Item,Integer>, long)
                                   — CODEC for NBT serialization
    ItemMemory.java                — 20-slot LRU deque
                                   — findChestsWithItem, findOldestChests,
                                     recordChest, evictLeastImportant
    CopperGolemMemoryAccess.java   — duck interface for mixin access
  mixin/
    CopperGolemEntityMixin.java    — memory persistence (write/read NBT hooks)
    CopperGolemAttributesMixin.java— follow range & speed boost
    MoveItemsTaskMixin.java        — deposit scan hook, hasInsertSpace
    CopperGolemBrainMixin.java     — injects MemoryRefreshTask into IDLE
  task/
    MemoryRefreshTask.java         — idle patrol: visits oldest chests

src/main/resources/
  fabric.mod.json                  — Fabric mod metadata
  coppergolemplus.mixins.json      — mixin registration
```

## Dependencies

| Dependency | Version | Required |
|---|---|---|
| Fabric Loader | ≥0.16.0 | Yes |
| Fabric API | ≥0.138.0+1.21.10 | Yes |
| Minecraft | 1.21.10 | Yes |

Version numbers in `gradle.properties` may need updating when new builds
of Fabric Loader or the API are published. Check
[fabricmc.net/develop](https://fabricmc.net/develop) for current values.

## Configuration

This mod has no configuration file yet. All constants are hardcoded in
their respective classes:

| Constant | Location | Default |
|---|---|---|
| `MAX_ENTRIES` | `memory/ItemMemory.java` | 20 |
| `COOLDOWN_TICKS` | `task/MemoryRefreshTask.java` | 1200 (60s) |
| `MAX_CHESTS_PER_CYCLE` | `task/MemoryRefreshTask.java` | 5 |
| `SCAN_DISTANCE` | `task/MemoryRefreshTask.java` | 2.5 blocks |

## Compatibility

- Requires Fabric API — not compatible with Forge or NeoForge
- Server-side only if you don't need visuals; client-side only if you're
  connecting to a server without the mod (the golem's memory won't persist)
- Recommended: install on both client and server for full persistence

## Troubleshooting

### "I gave the golem an item but it just stands there"

Copper golems only deposit into chests that have a **player viewing the
GUI** (vanilla mechanic). Open the chest GUI and stand near it — the golem
will route items into that chest while you're looking at the inventory.

### The golem keeps walking to the same full chest

Check that there are other chests nearby with space. The golem tries all
its remembered chests first, then falls through to a vanilla chunk scan.
If no chest has room, the 140-tick vanilla cooldown triggers.

### The golem forgot a chest after restart

Memory is persisted via NBT (`"CopperGolemPlusChests"` key). If the golem
despawns or dies, memory is lost. If it survives save/load and memory is
gone, check that `CopperGolemEntityMixin` is functioning (look for
`writeCustomData` / `readCustomData` hooks in logs).

## License

MIT — see `LICENSE` for details.
