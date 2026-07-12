# Copper Golem Plus — Technical Design & Implementation Plan

## 1. Finalized Feature Scope

Based on where we've landed, the mod does exactly three things:

| # | Feature | Behavior |
|---|---|---|
| A | **Item memory** | Remembers the **last 20 deposit events** (item type → chest location + timestamp), not unlimited/absolute memory. Oldest entries drop off (LRU). |
| B | **Smart deposit routing** | When the golem is holding an item it needs to put away, it first checks memory for "where did I last put this item type?" If that chest still exists **and has room**, path straight there. If the chest is gone, full, or unknown, **fall back to vanilla's default chest-search behavior** unchanged. |
| C | **Smarter pathfinding** | A byproduct of (B) — going straight to a known-good chest instead of vanilla's sequential up-to-10-chest search *is* the pathfinding improvement. No separate "wander toward known location" behavior needed. |

Explicitly **out of scope** for this pass: combat abilities, item-interaction abilities, config files, shared/multi-golem memory, debug UI. Those are easy to bolt on later once the core loop works.

Follow range / movement speed attribute boost (already implemented and confirmed working) stays as a minor supporting buff, not a core feature.

## 2. Phase 0 — Discovery Results

Before Phase 0, we had two failed assumptions: (1) that `CopperGolemEntity` uses `GoalSelector` AI (like most mobs), and (2) that it overrides `writeCustomDataToNbt`/`readCustomDataFromNbt`. Both were wrong. Phase 0 decompiled the actual sources (Yarn 1.21.10+build.2) and got the real answers:

### 2.1 AI System

- `CopperGolemEntity` **uses Brain/Task system** (same as Villagers, Piglins, Hoglins), **not** `GoalSelector`/`initGoals`.

- `CopperGolemBrain.java` defines two activity groups:
  - `Activity.CORE`: FleeTask, UpdateLookControlTask, MoveToTargetTask, OpenDoorsTask, cooldown ticks
  - `Activity.IDLE`: **MoveItemsTask** (item pickup/deposit), LookAtMob, StrollTask

### 2.2 The Deposit Task: `MoveItemsTask`

This is the single critical class. It extends `MultiTickTask` and handles **both** item pickup (from copper chests) and item deposit (to regular/trapped chests). It cycles through three navigation states:

| State | Behavior |
|---|---|
| `TRAVELLING` | Paths toward the target chest |
| `QUEUING` | Waits if another entity is viewing the chest |
| `INTERACTING` | 60-tick examine sequence, then item transfer at tick 60 |

The 60-tick sequence in `INTERACTING`:
- Tick 1: `onOpen(chest)`, sets state to `GETTING_ITEM`/`DROPPING_ITEM`, sets `targetContainer` position
- Tick 9: Plays sound (item get/drop)
- Tick 60: `selectInteractionState()` performs actual item transfer (`takeStack`/`placeStack`), then transitions back to `TRAVELLING`

Constant references: `INTERACTION_TICKS = 60`, `OPEN_INTERACTION_TICKS = 1`, `PLAY_SOUND_INTERACTION_TICKS = 9`.

### 2.3 NBT Method Signatures (1.21.10)

NOT `writeCustomDataToNbt`/`readCustomDataFromNbt`. The real signatures:

```java
@Override
public void writeCustomData(WriteView view) {
    super.writeCustomData(view);
    view.putLong("next_weather_age", this.nextOxidationAge);
    view.put("weather_state", Oxidizable.OxidationLevel.CODEC, this.getOxidationLevel());
}

@Override
public void readCustomData(ReadView view) {
    super.readCustomData(view);
    this.nextOxidationAge = view.getLong("next_weather_age", -1L);
    this.setOxidationLevel(view.read("weather_state", Oxidizable.OxidationLevel.CODEC)
        .orElse(Oxidizable.OxidationLevel.UNAFFECTED));
}
```

`WriteView` is NOT `NbtCompound` — it's a new abstraction layer (backed by NBT via Fabric mixins). Key methods:
- `putInt(String, int)`, `putLong(String, long)`, `putString(String, String)` — primitives
- `put(String, Codec<T>, T)` — Codec-serialized values
- `WriteView get(String key)` — nested sub-views
- `<T> ListAppender<T> getListAppender(String key, Codec<T> codec)` — list appender
- `ReadView.getOptionalTypedListView(String key, Codec<T> codec)` — read typed list

### 2.4 Held Item Tracking

The golem uses the standard equipment slot:
- `getMainHandStack()` — read held item
- `setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY)` — clear on pickup
- `equipStack(EquipmentSlot.MAINHAND, itemStack)` — set on deposit outcome

No separate `DataTracker` key or `MemoryModuleType` for "currently holding item."

### 2.5 Copper Chest Handling

`CopperChestBlock extends ChestBlock`. Uses standard `ChestBlockEntity`. Inventory accessed via `ChestBlock.getInventory(chestBlock, state, world, pos, false)`. The `MoveItemsTask.Storage.getInventory()` helper method handles this already.

### 2.6 Existing Memory Modules (can't piggyback)

- `VISITED_BLOCK_POSITIONS` (Set<GlobalPos>, cap ~10, 6000-tick expiry) — tracks positions, not item types
- `UNREACHABLE_TRANSPORT_BLOCK_POSITIONS` (Set<GlobalPos>, cap ~50, 6000-tick expiry) — tracks unreachable positions
- `TRANSPORT_ITEMS_COOLDOWN_TICKS` (int, 140 ticks) — cooldown between transport operations

Our memory is item-type-keyed with different semantics. We use a **private entity field** (already added via `@Unique` in the mixin) rather than registering a new `MemoryModuleType`.

### 2.7 Attributes Method Exists

`createCopperGolemAttributes()` is a real static method returning `DefaultAttributeContainer.Builder` with `MOVEMENT_SPEED: 0.2`, `STEP_HEIGHT: 1.0`, `MAX_HEALTH: 12.0`. The existing `CopperGolemAttributesMixin` targeting this method **works**.

## 3. Non-Destructive Design Constraints — What We Must NOT Break

This is the core of "detailed proposal" you asked for: an explicit list of existing golem behavior that has to keep working exactly as vanilla intends, and the design rules that guarantee it. Every phase below gets checked against this list before being considered done.

### 3.1 Vanilla behaviors that must stay completely untouched

- **Oxidation and statue transformation.** The golem's copper aging (unoxidized → exposed → weathered → oxidized → statue) and the ~0.58%/tick statue-conversion chance are entirely unrelated systems. Our mixins must never inject into anything on that code path, even indirectly.
- **Waxing.** Honeycomb interactions and waxed-state checks stay vanilla.
- **Held-item rendering/animation.** We only ever decide *where* the golem walks. We never touch how it holds, animates, or displays an item.
- **The 60-tick (3s) examine chest interaction and its associated state machine.** Our shortcut only changes *which chest position* the `MoveItemsTask` paths toward — it must still run the normal `TRAVELLING`→`QUEUING`→`INTERACTING` sequence, including `onOpen`/`onClose`, sounds, and the `interactionTicks` counter.
- **Non-copper vanilla chests, trapped chests, and their existing fallback search.** This logic is only *bypassed early* when we have high confidence (a valid, non-full remembered chest for the exact item type). It is never rewritten, and it must still run byte-for-byte as before whenever our memory doesn't have a confident answer. The bypass happens inside `MoveItemsTask.findStorage()` by returning our `Storage` early via `@Inject(cancellable = true)`.
- **Item-type compatibility rules golem already enforces** — e.g. it can't distinguish potion contents, tipped-arrow types, or suspicious stew types from each other, and ignores durability/enchantments/custom names/shulker-box or bundle contents when deciding if a chest "has the same item." We do **not** reimplement or "improve" this logic ourselves. Our memory only stores *which chest a given item type went to before* — the actual "is this a valid deposit target" check is always deferred to vanilla's own `canInsert()`/`ItemStack.areItemsAndComponentsEqual()`, never our own guess.
- **Copper chest oxidation/wax state as a container.** A chest is a chest for our purposes regardless of its oxidation stage — we don't special-case any particular stage.

### 3.2 Safety rules for how the mixins themselves are written

- **Prefer `@Inject` at `TAIL`/`HEAD` with cancellation over `@Redirect`/`@Overwrite` wherever possible.** `@Redirect` and `@Overwrite` claim exclusive ownership of a call site — if another mod also touches copper golem chest logic, only one of us can redirect the same call and the other mod breaks or fails to load. `@Inject` allows many mods' mixins to coexist on the same method.
- **Never use `@Overwrite`.** Full method replacement is the highest-risk, least-compatible mixin technique and is not needed for anything in this plan.
- **Every injection stays additive-with-fallback.** If our memory is empty, invalid, or the lookup throws for any reason, control must fall through to unmodified vanilla behavior — never a broken/half state.
- **No new `MemoryModuleType` registered on the vanilla `Brain` schema.** That's a global, registry-level change that affects every golem-related system and datapack compatibility; we don't need it because we use a `@Unique` field on the entity mixin.
- **Cheap by construction.** Memory is capped at 20 entries and only checked when the golem actually needs a deposit decision (inside `MoveItemsTask.keepRunning()`), not every tick.
- **Mixin handler context.** Because mixin handlers are injected into the target class, handlers in a `MoveItemsTask` mixin can access `MoveItemsTask`'s private members (`findStorage`, `placeStack`, `canInsert`, `targetStorage`, etc.) directly.

### 3.3 World / save compatibility

- **Old worlds and existing golems without our custom data must load with zero errors** — our read hook only acts `if (view.contains("CopperGolemPlusMemory"))`, so any golem saved before this mod was installed just gets an empty memory, not a crash.
- **Removing the mod later must not corrupt saves.** Our data lives in one clearly-namespaced WriteView sub-key (`CopperGolemPlusMemory`). Vanilla ignores unknown WriteView keys it doesn't recognize, so uninstalling the mod leaves harmless orphaned data rather than breaking the entity.
- **Server-authoritative.** All of this logic must run identically on a dedicated server (where the real game-state decisions happen) — no assumptions that only hold true on a integrated/client-side world.
- **Use Codec-based serialization** via `Codec.list(DepositRecord.CODEC)` for the memory list, stored with `view.put("CopperGolemPlusMemory", codec, list)` / `view.getOptionalTypedListView("CopperGolemPlusMemory", codec)`.

### 3.4 Multiplayer / mod-compatibility considerations

- Golem AI and pathing are server-side; our mixins only need to target the common/server source set, not client-only code.
- Because we use additive `@Inject`s rather than exclusive `@Redirect`s (§3.2), other mods that also add copper golem behavior (e.g. a mod adding new golem abilities) should be able to coexist with this one, as long as they follow the same courtesy.

## 4. Data Model

### 4.1 Core records

```java
public record DepositRecord(Item itemType, BlockPos chestPos, long worldTime) {
    public static final Codec<DepositRecord> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Registries.ITEM.getCodec().fieldOf("item").forGetter(DepositRecord::itemType),
            BlockPos.CODEC.fieldOf("pos").forGetter(DepositRecord::chestPos),
            Codec.LONG.fieldOf("time").forGetter(DepositRecord::worldTime)
        ).apply(instance, DepositRecord::new)
    );
}
```

### 4.2 ItemMemory

```java
public class ItemMemory {
    private static final int MAX_ENTRIES = 20;
    private final Deque<DepositRecord> history = new ArrayDeque<>();

    /** Scan most-recent → oldest for matching item type. O(n), n ≤ 20. */
    @Nullable
    public DepositRecord findLastDeposit(Item itemType) { ... }

    /** Add new entry, evict oldest if over MAX_ENTRIES. */
    public void record(Item item, BlockPos pos, long time) { ... }

    /** Persist via Codec list. */
    public void writeTo(WriteView view) {
        view.put("items", Codec.list(DepositRecord.CODEC), List.copyOf(history));
    }

    /** Load from Codec list, oldest-first. */
    public void readFrom(ReadView view) {
        view.getOptionalTypedListView("items", DepositRecord.CODEC)
            .ifPresent(list -> list.stream().forEach(history::addLast));
    }
}
```

Lookup is a linear scan from newest to oldest. 20-entry cap keeps it cheap. No separate index structure needed.

## 5. Architecture: Where the Hooks Go

### 5.1 Mixin files

| Mixin file | Target class | Package |
|---|---|---|
| `CopperGolemEntityMixin.java` (rewrite) | `CopperGolemEntity` | `mixin` |
| `CopperGolemAttributesMixin.java` (keep) | `CopperGolemEntity` | `mixin` |
| `MoveItemsTaskMixin.java` (new) | `MoveItemsTask` | `mixin` |

### 5.2 Read-side hook — substitute remembered chest position

**Target**: `MoveItemsTask.findStorage(ServerWorld, PathAwareEntity)` — private method, returns `Optional<Storage>`

**Injection**: `@Inject(method = "findStorage", at = @At("HEAD"), cancellable = true)`

This method is called on each tick of `keepRunning()` when the golem has no valid target storage. It scans nearby chunks for chests and returns the nearest valid one.

**Handler logic**:
1. Get `entity.getMainHandStack()` — if empty, golem needs to **pick up** (not deposit), skip our logic
2. If non-empty, cast to `CopperGolemMemoryAccess` and look up `findLastDeposit(heldItem.getItem())`
3. If a record is found:
   - Verify chunk is loaded at that position
   - Verify the block is still a valid output chest (compare against `OUTPUT_CHEST_PREDICATE`: `state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)`)
   - Get the `BlockEntity`, if it's a `ChestBlockEntity`, get its `Inventory` via `ChestBlock.getInventory()`
   - Check `canInsert(entity, inventory)` — this is the same check `MoveItemsTask` uses for its own decision (calls vanilla `hasExistingStack()` logic)
4. If all checks pass, construct `Storage.forContainer(blockEntity, world)`, call `cir.setReturnValue(Optional.of(storage))`, mark visited, transition to TRAVELLING
5. If any check fails, fall through (let vanilla `findStorage()` run normally)

### 5.3 Write-side hook — record successful deposit

**Target**: `MoveItemsTask.placeStack(PathAwareEntity, Inventory)` — private method, returns void

**Injections**:
- `@Inject(method = "placeStack", at = @At("HEAD"))` — capture the held item type before `placeStack` modifies it
- `@Inject(method = "placeStack", at = @At("TAIL"))` — record the deposit

**Why two injections**: `placeStack` modifies the entity's held stack via `insertStack()`. By the time TAIL runs, the stack may already be empty. We capture the item type at HEAD to know what was deposited.

**HEAD handler**: Store `entity.getMainHandStack().getItem()` in a `@Unique` field `copperGolemPlus$pendingItem`. Only if entity is `CopperGolemEntity`.

**TAIL handler**: If entity is `CopperGolemEntity` + `CopperGolemMemoryAccess` and `copperGolemPlus$pendingItem` is non-null:
1. Get `this.targetStorage.pos()` (the chest position stored in the `MoveItemsTask` instance)
2. Get `entity.getEntityWorld().getTime()` (world time)
3. Call `memory.record(pendingItem, pos, time)`
4. Clear `copperGolemPlus$pendingItem`

**Why `placeStack` is the right signal**: It's only called when `selectInteractionState()` determines the entity can place items (`!canPickUpItem(entity) && canInsert(entity, inventory)` — i.e., entity is holding an item and there's room in the chest). It fires at the end of the 60-tick interaction sequence, after the vanilla examine animation completes.

### 5.4 Persistence hook

**Target**: `CopperGolemEntity.writeCustomData(WriteView)` / `CopperGolemEntity.readCustomData(ReadView)`

`@Inject(method = "writeCustomData", at = @At("TAIL")`:
```java
view.put("CopperGolemPlusMemory", Codec.list(DepositRecord.CODEC), memory.toList());
```

`@Inject(method = "readCustomData", at = @At("TAIL")`:
```java
memory.readFrom(view.getReadView("CopperGolemPlusMemory"));
// readFrom handles absent key gracefully → empty memory
```

### 5.5 Deprecation of existing (unused) code

The following files were written for the old GoalSelector-based approach and are now dead code — they will be deleted:

| File | Reason |
|---|---|
| `ai/MemoryScanGoal.java` | GoalSelector-based; incompatible with Brain system; replaced by write-side hook |
| `ai/SmartReturnToKnownChestGoal.java` | Same; replaced by read-side hook |
| `memory/GolemMemory.java` | Old BlockPos→Long model; replaced by `ItemMemory` with `DepositRecord` |

## 6. Phases

### Phase 0 — Discovery ✅ DONE

Decompiled sources confirmed Brain/Task system, `MoveItemsTask` as the deposit task, `writeCustomData(WriteView)`/`readCustomData(ReadView)` for persistence, and `createCopperGolemAttributes()` as a valid mixin target.

### Phase 1 — Rewrite persistence layer

1. Create `DepositRecord.java` (record + Codec)
2. Create `ItemMemory.java` (Deque<DepositRecord>, 20-entry LRU, writeTo/readFrom with WriteView/ReadView)
3. Rewrite `CopperGolemEntityMixin.java`:
   - Remove `initGoals` stub, `goalSelector` references
   - Change `@Inject` targets from `writeCustomDataToNbt`/`readCustomDataFromNbt` to `writeCustomData(WriteView)`/`readCustomData(ReadView)`
   - Wire `ItemMemory` through `CopperGolemMemoryAccess`
4. Delete `GolemMemory.java`, `MemoryScanGoal.java`, `SmartReturnToKnownChestGoal.java`
5. Verify: place golem, force memory (debug log), save+reload world, confirm entry survives
6. Verify: pre-existing golem (summoned before this mod) loads with no errors and empty memory

### Phase 2 — Read-side hook (routing)

1. Create `MoveItemsTaskMixin.java` with `@Unique` field for pending item
2. Add `@Inject(method = "findStorage", at = @At("HEAD"), cancellable = true)` handler
3. Implement memory lookup + chest validation + `canInsert` check + fallback
4. Verify: golem deposits an item, remembers the chest, deposits same item type again and paths straight there
5. Verify: examine/interact animation still plays normally on arrival (§3.1)

### Phase 3 — Write-side hook (recording)

1. Add `@Inject(method = "placeStack", at = @At("HEAD"))` to capture held item type
2. Add `@Inject(method = "placeStack", at = @At("TAIL"))` to record deposit
3. Verify: deposit creates a memory entry
4. Verify: 21st deposit evicts the oldest of the 20 remembered entries
5. Verify: `put_fail` (no room) does NOT create a memory entry

### Phase 4 — Testing pass

- Golem deposits an item, remembers the chest, deposits the same item type again later and paths straight there.
- Memory survives a world save/reload.
- Remembered chest becomes full → golem detects this and falls back to vanilla's default search instead of getting stuck.
- Remembered chest destroyed → golem doesn't crash, falls back to vanilla search.
- 21st deposit evicts the oldest of the 20 remembered entries.
- A golem that existed before the mod was installed loads and behaves correctly (§3.3).
- Oxidation and statue transformation still happen normally on a golem with active memory (§3.1).
- (If feasible to test) another mod's copper-golem-related mixin still loads alongside ours without conflict (§3.2/3.4).

### Phase 5 — Polish

- Flip `require = 0` → `require = 1` on all mixin `@Inject` annotations once every target is confirmed
- Revisit the speed/follow-range boost — may want to dial back from 0.32 closer to vanilla 0.2 now that direct routing already cuts travel time

## 7. Files

### Source files (after Phase 1)

| File | Status | Purpose |
|---|---|---|
| `CopperGolemPlusMod.java` | Keep | Entrypoint |
| `memory/CopperGolemMemoryAccess.java` | Keep | Duck interface |
| `memory/ItemMemory.java` | **New** | Deque-based 20-entry item memory |
| `memory/DepositRecord.java` | **New** | Record: (Item, BlockPos, long) + Codec |
| `mixin/CopperGolemEntityMixin.java` | **Rewrite** | NBT hooks → WriteView/ReadView |
| `mixin/CopperGolemAttributesMixin.java` | Keep | Attribute boost (confirmed correct) |
| `mixin/MoveItemsTaskMixin.java` | **New** | Read-side + write-side hooks |
| `memory/GolemMemory.java` | **Delete** | Replaced by ItemMemory |
| `ai/MemoryScanGoal.java` | **Delete** | Incompatible with Brain system |
| `ai/SmartReturnToKnownChestGoal.java` | **Delete** | Replaced by MoveItemsTask mixin |

### Build / config files

| File | Status |
|---|---|
| `coppergolemplus.mixins.json` | Update to add `MoveItemsTaskMixin` |
| `build.gradle` | Keep |
| `fabric.mod.json` | Keep |
