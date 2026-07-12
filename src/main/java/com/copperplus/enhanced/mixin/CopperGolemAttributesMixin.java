package com.copperplus.enhanced.mixin;

import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CopperGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * VERIFY-ME: the static attribute-builder method is conventionally named
 * `create<Mob>Attributes()` (see IronGolemEntity#createIronGolemAttributes,
 * VillagerEntity#createVillagerAttributes, etc.) and returns
 * DefaultAttributeContainer.Builder. Confirm the exact name for
 * CopperGolemEntity via a mapping viewer - it's very likely
 * `createCopperGolemAttributes()` but static-method mixin targets are worth
 * double-checking since the return type and descriptor must match exactly.
 *
 * We inject at the RETURN of the builder method and just call .add(...)
 * again on the same builder object before it's returned - attribute
 * builders support calling add() multiple times for the same attribute,
 * with the later call winning, so this cleanly overrides the base value
 * without needing to know what the original value even was.
 */
@Mixin(CopperGolemEntity.class)
public abstract class CopperGolemAttributesMixin {

	@Inject(
			method = "createCopperGolemAttributes",
			at = @At("RETURN"),
			require = 0
	)
	private static void copperGolemPlus$boostAttributes(
			CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
		DefaultAttributeContainer.Builder builder = cir.getReturnValue();
		// Lets it notice/track known chests from further away.
		builder.add(EntityAttributes.FOLLOW_RANGE, 48.0D);
		// A modest speed bump so "smarter" also feels "snappier".
		builder.add(EntityAttributes.MOVEMENT_SPEED, 0.32D);
	}
}
