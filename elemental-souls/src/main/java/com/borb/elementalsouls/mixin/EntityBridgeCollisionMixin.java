package com.borb.elementalsouls.mixin;

import com.borb.elementalsouls.BridgeRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityBridgeCollisionMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void elementalSouls$walkOnParticleBridge(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;
        Vec3 adjusted = cir.getReturnValue();
        Vec3 supported = BridgeRegistry.adjustMovement(entity, adjusted);
        if (supported != adjusted) {
            cir.setReturnValue(supported);
        }
    }
}
