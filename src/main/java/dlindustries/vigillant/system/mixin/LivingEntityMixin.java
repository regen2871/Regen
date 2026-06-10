package dlindustries.vigillant.system.mixin;

import dlindustries.vigillant.system.module.modules.optimizer.ShieldOptimizer;
import dlindustries.vigillant.system.module.modules.render.HitAnimations;
import dlindustries.vigillant.system.system;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected boolean handSwinging;

    @Shadow
    protected int handSwingTicks;

    @ModifyConstant(
            method = "isBlocking",
            constant = @Constant(intValue = 5)
    )
    private int modifyShieldDelay(int original) {
        ShieldOptimizer module = system.INSTANCE.getModuleManager().getModule(ShieldOptimizer.class);
        return module != null && module.isEnabled() ? 0 : original;
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void modifySwingDuration(CallbackInfoReturnable<Integer> info) {
        HitAnimations module = system.INSTANCE.getModuleManager().getModule(HitAnimations.class);
        if (module == null || !module.isEnabled()) return;

        double speed = module.getSwingSpeed();
        if (speed <= 0.1) speed = 0.1;
        info.setReturnValue((int) (6 / speed));
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"))
    private void allowReSwing(Hand hand, boolean fromServer, CallbackInfo ci) {
        HitAnimations module = system.INSTANCE.getModuleManager().getModule(HitAnimations.class);
        if (module == null || !module.isEnabled()) return;

        handSwinging = false;
        handSwingTicks = -1;
    }
}
