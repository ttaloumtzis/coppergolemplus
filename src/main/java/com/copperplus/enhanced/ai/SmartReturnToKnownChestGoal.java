package com.copperplus.enhanced.ai;

import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import com.copperplus.enhanced.memory.GolemMemory;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * When the golem has nothing better to do (its navigation is idle - meaning
 * none of the higher-priority vanilla goals, like "carry item to chest",
 * currently have it moving somewhere) this goal sends it toward the
 * nearest chest it remembers, rather than leaving it to vanilla's blind
 * random wandering. This is what actually improves "pathfinding" from the
 * player's perspective: the golem starts looking like it knows where it's
 * going.
 * <p>
 * Register this with a LOWER priority number than the vanilla chest-seeking
 * goals so vanilla always wins when it has something real to do (lower
 * number = higher priority in GoalSelector). A value of 6-8 works well
 * alongside typical vanilla wander goals which usually sit around 7-8.
 */
public class SmartReturnToKnownChestGoal extends Goal {

	private static final double MOVE_SPEED = 0.55D;
	private static final int GIVE_UP_AFTER_TICKS = 200; // ~10 seconds

	private final PathAwareEntity golem;
	private final GolemMemory memory;
	private BlockPos targetPos;
	private int ticksMoving;

	public SmartReturnToKnownChestGoal(PathAwareEntity golem) {
		this.golem = golem;
		this.memory = ((CopperGolemMemoryAccess) golem).copperGolemPlus$getMemory();
		this.setControls(EnumSet.of(Goal.Control.MOVE));
	}

	@Override
	public boolean canStart() {
		if (memory.isEmpty()) {
			return false;
		}
		if (!golem.getNavigation().isIdle()) {
			// Something higher priority (vanilla chest logic, panic, etc.)
			// is already driving movement - don't fight it.
			return false;
		}
		BlockPos candidate = memory.closestTo(golem.getBlockPos());
		if (candidate == null || candidate.isWithinDistance(golem.getBlockPos(), 3)) {
			// Either nothing remembered, or we're basically already there.
			return false;
		}
		if (!isStillValidChest(candidate)) {
			memory.forget(candidate);
			return false;
		}
		this.targetPos = candidate;
		return true;
	}

	@Override
	public void start() {
		ticksMoving = 0;
		golem.getNavigation().startMovingTo(
				targetPos.getX() + 0.5,
				targetPos.getY(),
				targetPos.getZ() + 0.5,
				MOVE_SPEED
		);
	}

	@Override
	public boolean shouldContinue() {
		ticksMoving++;
		if (ticksMoving > GIVE_UP_AFTER_TICKS) {
			return false;
		}
		return !golem.getNavigation().isIdle();
	}

	@Override
	public void stop() {
		targetPos = null;
	}

	private boolean isStillValidChest(BlockPos pos) {
		World world = golem.getEntityWorld();
		if (!world.isChunkLoaded(pos)) {
			// Don't forget it - we just can't check right now.
			return true;
		}
		String path = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).getPath();
		return path.contains("chest");
	}
}
