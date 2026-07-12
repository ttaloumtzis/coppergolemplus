package com.copperplus.enhanced.task;

import com.copperplus.enhanced.memory.ChestSnapshot;
import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryRefreshTask extends MultiTickTask<CopperGolemEntity> {
    private static final int COOLDOWN_TICKS = 1200;
    private static final double SCAN_DISTANCE = 2.5;
    private static final int MAX_CHESTS_PER_CYCLE = 5;

    private int cooldown;
    private final List<BlockPos> pendingTargets = new ArrayList<>();
    private int currentIndex;

    public MemoryRefreshTask() {
        super(Map.of(
            MemoryModuleType.WALK_TARGET, MemoryModuleState.REGISTERED,
            MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED
        ), 60, 800);
    }

    @Override
    protected boolean shouldRun(ServerWorld world, CopperGolemEntity entity) {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!entity.getMainHandStack().isEmpty()) return false;
        CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) entity;
        if (access.copperGolemPlus$getMemory().isEmpty()) return false;

        ChestSnapshot oldest = access.copperGolemPlus$getMemory().findOldestChest();
        if (oldest != null && world.getTime() - oldest.lastSeenTime() < COOLDOWN_TICKS) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }
        return true;
    }

    @Override
    protected void run(ServerWorld world, CopperGolemEntity entity, long time) {
        pendingTargets.clear();
        currentIndex = 0;

        CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) entity;
        for (ChestSnapshot snapshot : access.copperGolemPlus$getMemory().findOldestChests(MAX_CHESTS_PER_CYCLE)) {
            pendingTargets.add(snapshot.pos());
        }

        if (!pendingTargets.isEmpty()) {
            navigateToNext(entity);
        }
    }

    @Override
    protected void keepRunning(ServerWorld world, CopperGolemEntity entity, long time) {
        if (currentIndex >= pendingTargets.size()) return;

        BlockPos target = pendingTargets.get(currentIndex);
        double dist = entity.squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        if (dist < SCAN_DISTANCE * SCAN_DISTANCE) {
            scanChest(world, entity, target);
            currentIndex++;
            if (currentIndex < pendingTargets.size()) {
                navigateToNext(entity);
            }
        }
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, CopperGolemEntity entity, long time) {
        return currentIndex < pendingTargets.size();
    }

    @Override
    protected void finishRunning(ServerWorld world, CopperGolemEntity entity, long time) {
        cooldown = COOLDOWN_TICKS;
        pendingTargets.clear();
        currentIndex = 0;
        entity.getBrain().forget(MemoryModuleType.WALK_TARGET);
        entity.getBrain().forget(MemoryModuleType.LOOK_TARGET);
    }

    private void navigateToNext(CopperGolemEntity entity) {
        entity.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(pendingTargets.get(currentIndex), 0.5f, 0));
    }

    private void scanChest(ServerWorld world, CopperGolemEntity entity, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return;

        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.CHEST) && !state.isOf(Blocks.TRAPPED_CHEST)) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest)) return;

        Inventory inv = chest;
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Inventory merged = ChestBlock.getInventory(chestBlock, state, world, pos, false);
            if (merged != null) inv = merged;
        }

        Map<Item, Integer> contents = scanInventory(inv);
        CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) entity;
        access.copperGolemPlus$getMemory().recordChest(pos, contents, world.getTime());
    }

    private Map<Item, Integer> scanInventory(Inventory inventory) {
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return contents;
    }
}
