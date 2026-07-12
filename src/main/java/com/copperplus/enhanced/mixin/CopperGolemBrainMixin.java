package com.copperplus.enhanced.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.copperplus.enhanced.task.MemoryRefreshTask;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.passive.CopperGolemBrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CopperGolemBrain.class)
public abstract class CopperGolemBrainMixin {

    @SuppressWarnings("unchecked")
    @ModifyArg(
        method = "addIdleActivities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/ai/brain/Brain;setTaskList(Lnet/minecraft/entity/ai/brain/Activity;Lcom/google/common/collect/ImmutableList;)V"
        ),
        index = 1
    )
    private static ImmutableList<Pair<Integer, ? extends Task<? super LivingEntity>>>
            copperGolemPlus$addMemoryRefreshTask(
            ImmutableList<Pair<Integer, ? extends Task<? super LivingEntity>>> tasks) {
        ImmutableList.Builder builder = ImmutableList.builder();
        builder.add(tasks.get(0));
        builder.add(Pair.of(1, new MemoryRefreshTask()));
        builder.addAll(tasks.subList(1, tasks.size()));
        return builder.build();
    }
}
