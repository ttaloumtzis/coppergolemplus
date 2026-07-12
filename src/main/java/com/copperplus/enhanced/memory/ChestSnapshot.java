package com.copperplus.enhanced.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

public record ChestSnapshot(BlockPos pos, Map<Item, Integer> contents, long lastSeenTime) {
    public static final Codec<ChestSnapshot> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChestSnapshot::pos),
            Codec.unboundedMap(Registries.ITEM.getCodec(), Codec.INT).fieldOf("contents").forGetter(ChestSnapshot::contents),
            Codec.LONG.fieldOf("time").forGetter(ChestSnapshot::lastSeenTime)
        ).apply(instance, ChestSnapshot::new)
    );
}
