package com.copperplus.enhanced.memory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class ItemMemory {
    private static final int MAX_ENTRIES = 20;
    private final Deque<DepositRecord> history = new ArrayDeque<>();

    @Nullable
    public DepositRecord findLastDeposit(Item itemType) {
        for (DepositRecord record : history) {
            if (record.itemType().equals(itemType)) {
                return record;
            }
        }
        return null;
    }

    public void record(Item item, BlockPos pos, long time) {
        history.addFirst(new DepositRecord(item, pos, time));
        if (history.size() > MAX_ENTRIES) {
            history.removeLast();
        }
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    public List<DepositRecord> toList() {
        return List.copyOf(history);
    }

    public void readFrom(List<DepositRecord> list) {
        history.clear();
        list.forEach(history::addLast);
    }
}
