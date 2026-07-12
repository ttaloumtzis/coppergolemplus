# Copper Golem Plus

A Fabric mod for Minecraft Java Edition 1.21.10 that gives copper golems
persistent chest memory and intelligent item routing.

## Features

- **Chest snapshot memory** — each golem remembers up to 20 chests it has
  interacted with, storing a full inventory scan (`Map<Item, Integer>`) per
  chest. Persisted with the entity NBT across save/load.
- **Multi-chest deposit search** — when holding an item, the golem checks
  all known chests (newest-first) for one with room. Falls through to
  vanilla's chunk scan if none of its remembered chests have space.
- **Stack-size-aware eviction** — when memory is full (20 chests), the
  least useful chest is evicted: most full (slot-weighted by `maxStack`
  to handle 64/16/1 limits), tiebroken by newest (keep the older, more
  stale snapshot).
- **Proactive memory patrol** — during idle the golem periodically visits
  the 5 oldest chests to refresh their snapshots. 60s cooldown between
  cycles. Stops patrolling once all chests have been refreshed recently.
- **Vanilla fallback** — no special behavior for items the golem has never
  seen; it uses vanilla's built-in chest search.

## Project layout

```
src/main/java/com/copperplus/enhanced/
  CopperGolemPlusMod.java          — mod entrypoint
  memory/
    ChestSnapshot.java             — record (BlockPos, Map<Item,Integer>, long)
    ItemMemory.java                — 20-slot LRU deque + eviction logic
    CopperGolemMemoryAccess.java   — duck interface for mixin access
  mixin/
    CopperGolemEntityMixin.java    — memory persistence (write/read NBT)
    CopperGolemAttributesMixin.java— attribute boost
    MoveItemsTaskMixin.java        — deposit scan hook, inventory scan
    CopperGolemBrainMixin.java     — injects MemoryRefreshTask into IDLE
  task/
    MemoryRefreshTask.java         — idle patrol, visits oldest chests
```

## Building

```bash
./gradlew build          # produces the jar in build/libs/
./gradlew runClient      # launches a dev client with the mod loaded
./gradlew runServer      # launches a dedicated server
```

## How it works

1. Golem picks up items → `MoveItemsTask.findStorage` fires →
   `MoveItemsTaskMixin.onFindStorage` iterates all remembered chests for
   the held item type, checks `canInsert` + actual room, returns the first
   valid chest. Falls through to vanilla chunk scan if none match.

2. Golem reaches a chest → `tickInteracting` fires every tick of the
   interaction → `scanInventory` reads all slot contents →
   `ItemMemory.recordChest` upserts the snapshot (deduped by position).

3. Golem is idle → `MemoryRefreshTask` counts down 60s → picks the 5
   oldest chests from memory → navigates to each → scans and updates
   snapshots. Stops once all chests have been refreshed within 60s.

4. Memory is full → `evictLeastImportant` picks the chest with the highest
   slot-weighted fullness (sum of `count / maxStackSize` across all item
   types), tiebroken by newest scan time. That chest is removed.

## License

MIT — see `LICENSE` for details.
