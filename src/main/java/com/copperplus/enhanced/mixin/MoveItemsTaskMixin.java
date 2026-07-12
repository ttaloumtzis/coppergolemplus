package com.copperplus.enhanced.mixin;

import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import com.copperplus.enhanced.memory.DepositRecord;
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

import java.util.Optional;

@Mixin(MoveItemsTask.class)
public abstract class MoveItemsTaskMixin {

    @Shadow
    private MoveItemsTask.Storage targetStorage;

    @Shadow
    private static boolean canInsert(PathAwareEntity entity, Inventory inventory) {
        return false;
    }

    @Unique
    private Item copperGolemPlus$pendingItem;

    @Unique
    private BlockPos copperGolemPlus$pendingPos;

    @Inject(method = "tickInteracting", at = @At("HEAD"), require = 0)
    private void onTickInteracting(MoveItemsTask.Storage storage, World world, PathAwareEntity entity, CallbackInfo ci) {
        if (entity instanceof CopperGolemEntity) {
            this.copperGolemPlus$pendingPos = storage.pos();
        }
    }

    @Inject(method = "findStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private void onFindStorage(ServerWorld world, PathAwareEntity entity, CallbackInfoReturnable<Optional<MoveItemsTask.Storage>> cir) {
        if (!(entity instanceof CopperGolemEntity golem)) return;
        ItemStack held = golem.getMainHandStack();
        if (held.isEmpty()) return;

        CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) golem;
        DepositRecord record = access.copperGolemPlus$getMemory().findLastDeposit(held.getItem());
        if (record == null) return;

        BlockPos pos = record.chestPos();
        if (!world.isChunkLoaded(pos)) return;

        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.CHEST) && !state.isOf(Blocks.TRAPPED_CHEST)) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity)) return;

        MoveItemsTask.Storage storage = MoveItemsTask.Storage.forContainer(be, world);
        if (storage == null) return;

        if (!canInsert(golem, storage.inventory())) return;

        cir.setReturnValue(Optional.of(storage));
    }

    @Inject(method = "placeStack", at = @At("HEAD"), require = 0)
    private void onPlaceStackHead(PathAwareEntity entity, Inventory inventory, CallbackInfo ci) {
        this.copperGolemPlus$pendingItem = null;
        if (entity instanceof CopperGolemEntity golem) {
            ItemStack held = golem.getMainHandStack();
            if (!held.isEmpty()) {
                this.copperGolemPlus$pendingItem = held.getItem();
            }
        }
    }

    @Inject(method = "placeStack", at = @At("TAIL"), require = 0)
    private void onPlaceStackTail(PathAwareEntity entity, Inventory inventory, CallbackInfo ci) {
        if (this.copperGolemPlus$pendingItem != null && this.copperGolemPlus$pendingPos != null) {
            if (entity instanceof CopperGolemEntity golem) {
                CopperGolemMemoryAccess access = (CopperGolemMemoryAccess) golem;
                long time = golem.getEntityWorld().getTime();
                access.copperGolemPlus$getMemory().record(this.copperGolemPlus$pendingItem, this.copperGolemPlus$pendingPos, time);
            }
        }
        this.copperGolemPlus$pendingItem = null;
        this.copperGolemPlus$pendingPos = null;
    }
}
