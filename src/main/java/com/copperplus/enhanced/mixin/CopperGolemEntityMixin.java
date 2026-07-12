package com.copperplus.enhanced.mixin;

import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import com.copperplus.enhanced.memory.GolemMemory;
import net.minecraft.entity.passive.CopperGolemEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * ============================================================================
 * VERIFY-ME NOTES (read before you first ./gradlew build):
 *
 * 1. Target class: guessed as net.minecraft.entity.passive.CopperGolemEntity,
 *    matching the package other golems (IronGolemEntity) live in. Confirm
 *    with a mapping viewer (https://linkie.shedaniel.dev, pick your MC
 *    version + Yarn) by searching "CopperGolem".
 *
 * 2. @Inject method="initGoals" - PathAwareEntity subclasses conventionally
 *    have a protected `initGoals()` that populates `this.goalSelector`.
 *    Confirm the exact method name/signature for CopperGolemEntity the same
 *    way. If it doesn't exist under that name, look for wherever
 *    `this.goalSelector.add(...)` calls happen in the golem's constructor
 *    or an equivalent init method, and inject there instead (inject at
 *    "TAIL" so all vanilla goals are already registered before ours).
 *
 * 3. @Inject method="writeCustomDataToNbt"/"readCustomDataFromNbt" - this is
 *    the classic NbtCompound-based signature. Some 1.21.x builds have begun
 *    migrating entity serialization to a WriteView/ReadView system instead.
 *    If your decompiled CopperGolemEntity (or its Entity superclass) uses
 *    `writeCustomData(WriteView view)` / `readCustomData(ReadView view)`,
 *    swap the injection signatures below to match and pull an NbtCompound
 *    out of/into the view (View types generally expose a way to get/put
 *    nested compounds - check the View interface for the exact call).
 *
 * 4. All three @Inject calls below use `require = 0` on purpose so a wrong
 *    guess fails silently instead of crashing your build - great while
 *    you're getting things compiling, bad once you think it's working.
 *    Once you've confirmed the real method names/signatures, change these
 *    to `require = 1` so Mixin loudly errors if a target ever goes missing
 *    (e.g. after a Minecraft update) instead of quietly doing nothing.
 * ============================================================================
 */
@Mixin(CopperGolemEntity.class)
public abstract class CopperGolemEntityMixin implements CopperGolemMemoryAccess {

	// NOTE: goalSelector-based injection removed for now - the game crashed
	// with "@Shadow field goalSelector was not located in the target class",
	// meaning CopperGolemEntity does NOT use the classic GoalSelector system.
	// It's very likely Brain/Task-based instead (like Villagers/Piglins).
	// We need to confirm this via the decompiled source before re-adding
	// goal logic - see the grep commands from earlier in the conversation.

	@Unique
	private final GolemMemory copperGolemPlus$memory = new GolemMemory();

	@Override
	public GolemMemory copperGolemPlus$getMemory() {
		return copperGolemPlus$memory;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"), require = 0)
	private void copperGolemPlus$writeMemory(NbtCompound nbt, CallbackInfo ci) {
		nbt.put("CopperGolemPlusMemory", copperGolemPlus$memory.writeNbt());
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"), require = 0)
	private void copperGolemPlus$readMemory(NbtCompound nbt, CallbackInfo ci) {
		nbt.getCompound("CopperGolemPlusMemory").ifPresent(copperGolemPlus$memory::readNbt);
	}
}
