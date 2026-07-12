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
	// IMPORTANT / VERSION NOTE:
	// Mojang has been migrating entity save/load away from raw NbtCompound
	// toward a WriteView / ReadView codec-based system in recent 1.21.x
	// releases. Depending on your exact Minecraft version, the mixin
	// injection points in CopperGolemEntityMixin that call these methods
	// may need to hand you a WriteView/ReadView instead of an NbtCompound.
	//
	// If that's the case for your version, wrap the view's NbtCompound
	// accessor (or use its own list-writing helpers) and adapt the two
	// methods below accordingly - the logic (list of "x,y,z,time" strings)
	// stays the same either way. Check with a mapping viewer
	// (https://linkie.shedaniel.dev) by searching "CopperGolemEntity" and
	// looking at its write/read-data method signature.
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
		NbtList list = in.getList("Locations", 10); // 10 = NbtCompound type id
		for (int i = 0; i < list.size(); i++) {
			NbtCompound e = list.getCompound(i);
			BlockPos pos = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
			knownLocations.put(pos, e.getLong("t"));
		}
	}
}
