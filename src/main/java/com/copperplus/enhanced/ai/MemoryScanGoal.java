package com.copperplus.enhanced.ai;

import com.copperplus.enhanced.memory.CopperGolemMemoryAccess;
import com.copperplus.enhanced.memory.GolemMemory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Passively "teaches" a copper golem where nearby chests are, by scanning a
 * small box around it every couple of seconds and remembering anything
 * whose block id contains "chest" (matches vanilla chest, trapped chest,
 * and copper chest without us needing to guess exact class names, which
 * change more often than registry ids do).
 * <p>
 * This goal never blocks or overrides other behavior - it doesn't touch
 * movement at all, it just keeps the golem's memory topped up so
 * {@link SmartReturnToKnownChestGoal} has something to work with.
 */
public class MemoryScanGoal extends Goal {

	private static final int SCAN_INTERVAL_TICKS = 100; // ~5 seconds
	private static final int HORIZONTAL_RADIUS = 8;
	private static final int VERTICAL_RADIUS = 3;

	private final PathAwareEntity golem;
	private final GolemMemory memory;
	private int cooldown;

	public MemoryScanGoal(PathAwareEntity golem) {
		this.golem = golem;
		this.memory = ((CopperGolemMemoryAccess) golem).copperGolemPlus$getMemory();
		// This goal never "occupies" movement/look control, so it's safe to
		// run concurrently with every other vanilla goal.
		this.setControls(EnumSet.noneOf(Goal.Control.class));
	}

	@Override
	public boolean canStart() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return false; // one-shot scan, restarts itself via canStart()/cooldown
	}

	@Override
	public void start() {
		cooldown = SCAN_INTERVAL_TICKS;
		scan();
	}

	private void scan() {
		World world = golem.getEntityWorld();
		BlockPos origin = golem.getBlockPos();

		for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
			for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
				for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
					BlockPos pos = origin.add(dx, dy, dz);
					BlockState state = world.getBlockState(pos);
					String path = Registries.BLOCK.getId(state.getBlock()).getPath();
					if (path.contains("chest")) {
						memory.remember(pos, world.getTime());
					}
				}
			}
		}
	}
}
