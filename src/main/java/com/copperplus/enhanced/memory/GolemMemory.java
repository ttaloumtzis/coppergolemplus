package com.copperplus.enhanced.memory;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small, bounded "spatial memory" for a single copper golem.
 * <p>
 * Every time the golem successfully interacts with (or simply notices) a
 * chest or item stack, we record the block position and a timestamp. Older
 * / less-useful entries are evicted once we hit MAX_ENTRIES so this never
 * grows unbounded or bloats the save file.
 * <p>
 * This class has zero dependency on entity internals, so it's easy to unit
 * test and easy to keep correct even if Mojang reshuffles the NBT/View API
 * around it in a given version - only the (de)serialize methods below need
 * to change to match your target version, see the notes at the bottom.
 */
public class GolemMemory {

	/** How many locations a single golem remembers at once. Tune to taste. */
	private static final int MAX_ENTRIES = 12;

	// LinkedHashMap with accessOrder=true gives us LRU eviction for free:
	// the least-recently-touched entry is always first in iteration order.
	private final LinkedHashMap<BlockPos, Long> knownLocations =
			new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
					return size() > MAX_ENTRIES;
				}
			};

	/** Record (or refresh) a location the golem has just interacted with. */
	public void remember(BlockPos pos, long worldTime) {
		knownLocations.put(pos.toImmutable(), worldTime);
	}

	/** Forget a specific location, e.g. because the chest is gone/destroyed. */
	public void forget(BlockPos pos) {
		knownLocations.remove(pos);
	}

	/**
	 * Returns the closest remembered location to origin, or null if we don't
	 * remember anything yet. Callers should validate the block is still
	 * there (still a chest, etc.) before trusting this blindly, and call
	 * forget() if it isn't.
	 */
	public BlockPos closestTo(BlockPos origin) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos candidate : knownLocations.keySet()) {
			double dist = candidate.getSquaredDistance(origin);
			if (dist < bestDist) {
				bestDist = dist;
				best = candidate;
			}
		}
		return best;
	}

	public boolean isEmpty() {
		return knownLocations.isEmpty();
	}

	public int size() {
		return knownLocations.size();
	}

	// ---------------------------------------------------------------
	// NBT persistence
	//
	// Confirmed for 1.21.10: entity NBT still uses plain NbtCompound
	// (not the WriteView/ReadView system), but Mojang changed the getter
	// methods to return Optional<T> instead of raw values/defaults, so
	// every read goes through .orElse(...) below.
	// ---------------------------------------------------------------

	public NbtCompound writeNbt() {
		NbtCompound out = new NbtCompound();
		NbtList list = new NbtList();
		for (Map.Entry<BlockPos, Long> entry : knownLocations.entrySet()) {
			NbtCompound e = new NbtCompound();
			e.putInt("x", entry.getKey().getX());
			e.putInt("y", entry.getKey().getY());
			e.putInt("z", entry.getKey().getZ());
			e.putLong("t", entry.getValue());
			list.add(e);
		}
		out.put("Locations", list);
		return out;
	}

	public void readNbt(NbtCompound in) {
		knownLocations.clear();
		if (!in.contains("Locations")) {
			return;
		}
		NbtList list = in.getList("Locations").orElse(new NbtList());
		for (int i = 0; i < list.size(); i++) {
			NbtCompound e = list.getCompound(i).orElse(new NbtCompound());
			int x = e.getInt("x").orElse(0);
			int y = e.getInt("y").orElse(0);
			int z = e.getInt("z").orElse(0);
			long t = e.getLong("t").orElse(0L);
			knownLocations.put(new BlockPos(x, y, z), t);
		}
	}
}
