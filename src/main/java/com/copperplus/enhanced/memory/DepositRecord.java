package com.copperplus.enhanced.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public record DepositRecord(Item itemType, BlockPos chestPos, long worldTime) {
    public static final Codec<DepositRecord> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Registries.ITEM.getCodec().fieldOf("item").forGetter(DepositRecord::itemType),
            BlockPos.CODEC.fieldOf("pos").forGetter(DepositRecord::chestPos),
            Codec.LONG.fieldOf("time").forGetter(DepositRecord::worldTime)
        ).apply(instance, DepositRecord::new)
    );
}
