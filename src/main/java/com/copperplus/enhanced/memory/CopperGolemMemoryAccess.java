package com.copperplus.enhanced.memory;

/**
 * "Duck interface" implemented by CopperGolemEntityMixin so that other
 * classes (like our custom Goal) can read/write a golem's memory without
 * needing to know it's actually backed by a mixin-injected field.
 */
public interface CopperGolemMemoryAccess {
	GolemMemory copperGolemPlus$getMemory();
}
