# Copper Golem Plus

A Fabric mod that gives copper golems (Java Edition 1.21.10) smarter behavior:

- **Item/chest memory** — each golem keeps a small persistent memory (saved
  with the world) of chest locations it has seen nearby.
- **Smarter pathfinding** — when a golem isn't already busy with vanilla's
  own chest logic, it proactively walks toward a remembered chest instead
  of wandering randomly. It also gets a longer follow/detection range and a
  small speed boost.

## Before you build: things to verify

I wrote this without access to your exact decompiled Minecraft sources, so
a couple of names are **educated guesses** based on standard Mojang/Yarn
naming conventions used for other mobs. Build will likely fail (loudly, at
compile time — safe) until you confirm these. Use
[linkie.shedaniel.dev](https://linkie.shedaniel.dev) (pick Yarn + your MC
version, search "CopperGolem") or run:

```
./gradlew genSources
```

then open the generated `CopperGolemEntity` source in
`.gradle`/your IDE's library sources to check against:

1. **`gradle.properties`** — `yarn_mappings`, `loader_version`, and
   `fabric_version` need to match what's currently published for your MC
   version. Check https://fabricmc.net/develop for current numbers.
2. **`mixin/CopperGolemEntityMixin.java`**
   - Confirm the class is really `net.minecraft.entity.passive.CopperGolemEntity`.
   - Confirm the goal-registration method is really called `initGoals`.
   - Confirm `writeCustomDataToNbt` / `readCustomDataFromNbt` are still the
     real method names — Mojang has been migrating some entity NBT code to
     a `WriteView`/`ReadView` system in recent versions. If yours uses that,
     the comment in the file explains what to change.
3. **`mixin/CopperGolemAttributesMixin.java`**
   - Confirm the static attributes method is really called
     `createCopperGolemAttributes()`.

Once confirmed, flip `require = 0` to `require = 1` on each `@Inject` in
both mixin files — that makes Mixin fail loudly if a target ever goes
missing (e.g. after updating Minecraft) instead of silently doing nothing.

## Project layout

```
src/main/java/com/copperplus/enhanced/
  CopperGolemPlusMod.java          - mod entrypoint, just logs on load
  mixin/
    CopperGolemEntityMixin.java    - adds goals + NBT save/load hooks
    CopperGolemAttributesMixin.java- boosts follow range & speed
  ai/
    MemoryScanGoal.java            - periodically remembers nearby chests
    SmartReturnToKnownChestGoal.java - walks to a remembered chest when idle
  memory/
    GolemMemory.java               - the actual memory data structure + NBT
    CopperGolemMemoryAccess.java   - interface for reaching a golem's memory
```

## Building & testing

```
./gradlew build          # produces the jar in build/libs/
./gradlew runClient       # launches a dev client with the mod loaded
```

Summon a copper golem (`/summon minecraft:copper_golem`), place a chest
within ~8 blocks, wait a few seconds for it to "notice" the chest, then
walk it away and watch it path back toward the chest once it's idle.

## Ideas for extending this further

- Make `MAX_ENTRIES` in `GolemMemory`, the scan radius, and the speed/range
  boost configurable via a JSON config file instead of hardcoded constants.
- Have golems on the same team/base share memory (a per-world memory bank
  keyed by chunk, rather than per-entity) so a new golem "inherits"
  knowledge from others.
- Add a "danger memory" too — remember lava/hazard positions to route
  around, not just chests to route toward.
