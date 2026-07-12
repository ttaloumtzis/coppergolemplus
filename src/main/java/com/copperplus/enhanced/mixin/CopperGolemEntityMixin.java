package com.copperplus.enhanced.mixin;

import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import com.copperplus.enhanced.memory.DepositRecord;
import com.copperplus.enhanced.memory.ItemMemory;
import com.mojang.serialization.Codec;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.entity.passive.CopperGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolemEntity.class)
public abstract class CopperGolemEntityMixin implements CopperGolemMemoryAccess {

    @Unique
    private final ItemMemory copperGolemPlus$memory = new ItemMemory();

    @Override
    public ItemMemory copperGolemPlus$getMemory() {
        return copperGolemPlus$memory;
    }

    @Inject(method = "writeCustomData", at = @At("TAIL"), require = 0)
    private void copperGolemPlus$writeMemory(WriteView view, CallbackInfo ci) {
        view.put("CopperGolemPlusMemory", Codec.list(DepositRecord.CODEC), copperGolemPlus$memory.toList());
    }

    @Inject(method = "readCustomData", at = @At("TAIL"), require = 0)
    private void copperGolemPlus$readMemory(ReadView view, CallbackInfo ci) {
        view.read("CopperGolemPlusMemory", Codec.list(DepositRecord.CODEC))
            .ifPresent(copperGolemPlus$memory::readFrom);
    }
}
