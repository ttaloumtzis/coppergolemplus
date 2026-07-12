package com.copperplus.enhanced.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class ItemMemory {
    private static final int MAX_ENTRIES = 20;
    private final Deque<ChestSnapshot> history = new ArrayDeque<>();

    @Nullable
    public ChestSnapshot findLastDeposit(Item itemType) {
        for (ChestSnapshot record : history) {
            if (record.contents().containsKey(itemType)) {
                return record;
            }
        }
        return null;
    }

    public List<ChestSnapshot> findChestsWithItem(Item itemType) {
        List<ChestSnapshot> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (ChestSnapshot record : history) {
            if (record.contents().containsKey(itemType) && seen.add(record.pos())) {
                result.add(record);
            }
        }
        return result;
    }

    @Nullable
    public ChestSnapshot findOldestChest() {
        ChestSnapshot oldest = null;
        for (ChestSnapshot snapshot : history) {
            if (oldest == null || snapshot.lastSeenTime() < oldest.lastSeenTime()) {
                oldest = snapshot;
            }
        }
        return oldest;
    }

    public List<ChestSnapshot> findOldestChests(int count) {
        List<ChestSnapshot> sorted = new ArrayList<>(history);
        sorted.sort(java.util.Comparator.comparingLong(ChestSnapshot::lastSeenTime));
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    public void recordChest(BlockPos pos, Map<Item, Integer> contents, long time) {
        history.removeIf(snapshot -> snapshot.pos().equals(pos));
        history.addFirst(new ChestSnapshot(pos, new HashMap<>(contents), time));
        if (history.size() > MAX_ENTRIES) {
            evictLeastImportant();
        }
    }

    private void evictLeastImportant() {
        ChestSnapshot worst = null;
        double worstScore = -1;
        long worstTime = Long.MIN_VALUE;
        for (ChestSnapshot snapshot : history) {
            double fullness = 0;
            for (Map.Entry<Item, Integer> entry : snapshot.contents().entrySet()) {
                fullness += (double) entry.getValue() / entry.getKey().getMaxCount();
            }
            if (fullness > worstScore + 1e-6 || (Math.abs(fullness - worstScore) < 1e-6 && snapshot.lastSeenTime() > worstTime)) {
                worstScore = fullness;
                worstTime = snapshot.lastSeenTime();
                worst = snapshot;
            }
        }
        if (worst != null) history.remove(worst);
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    public List<ChestSnapshot> toList() {
        return List.copyOf(history);
    }

    public void readFrom(List<ChestSnapshot> list) {
        history.clear();
        list.forEach(history::addLast);
    }
}
