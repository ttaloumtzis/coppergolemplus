package com.copperplus.enhanced.mixin;

import com.copperplus.enhanced.memory.ChestSnapshot;
import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ai.brain.task.MoveItemsTask;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(MoveItemsTask.class)
public abstract class MoveItemsTaskMixin {

    @Shadow
    private MoveItemsTask.Storage targetStorage;

    @Shadow
    private static boolean canInsert(PathAwareEntity entity, Inventory inventory) {
        return false;
    }

    @Inject(method = "tickInteracting", at = @At("HEAD"), require = 0)
    private void onTickInteracting(MoveItemsTask.Storage storage, World world, PathAwareEntity entity, CallbackInfo ci) {
        if (entity instanceof CopperGolemEntity golem) {
            CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) golem;
            Map<Item, Integer> contents = copperGolemPlus$scanInventory(storage.inventory());
            access.copperGolemPlus$getMemory().recordChest(storage.pos(), contents, world.getTime());
        }
    }

    @Inject(method = "findStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private void onFindStorage(ServerWorld world, PathAwareEntity entity, CallbackInfoReturnable<Optional<MoveItemsTask.Storage>> cir) {
        if (!(entity instanceof CopperGolemEntity golem)) return;
        ItemStack held = golem.getMainHandStack();
        if (held.isEmpty()) return;

        CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) golem;
        List<ChestSnapshot> records = access.copperGolemPlus$getMemory().findChestsWithItem(held.getItem());

        for (ChestSnapshot snapshot : records) {
            BlockPos pos = snapshot.pos();
            if (!world.isChunkLoaded(pos)) continue;

            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.CHEST) && !state.isOf(Blocks.TRAPPED_CHEST)) continue;

            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof ChestBlockEntity)) continue;

            MoveItemsTask.Storage storage = MoveItemsTask.Storage.forContainer(be, world);
            if (storage == null) continue;

            if (canInsert(golem, storage.inventory()) && copperGolemPlus$hasInsertSpace(held, storage.inventory())) {
                cir.setReturnValue(Optional.of(storage));
                return;
            }
        }
    }

    @Unique
    private static boolean copperGolemPlus$hasInsertSpace(ItemStack held, Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack slot = inventory.getStack(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.areItemsEqual(slot, held) && slot.getCount() < slot.getMaxCount()) return true;
        }
        return false;
    }

    @Unique
    private static Map<Item, Integer> copperGolemPlus$scanInventory(Inventory inventory) {
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
