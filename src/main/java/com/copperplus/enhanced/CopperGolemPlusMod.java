package com.copperplus.enhanced;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopperGolemPlusMod implements ModInitializer {

	public static final String MOD_ID = "coppergolemplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[CopperGolemPlus] Loading enhanced copper golem behaviors...");
		// All the actual behavior is added via Mixin into CopperGolemEntity
		// (see mixin/CopperGolemEntityMixin.java) so there's nothing to
		// register here yet. This is a good place to add a config loader
		// later if you want memory size / speed bonuses to be tweakable
		// without recompiling.
	}
}
