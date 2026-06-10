package dlindustries.vigillant.system.mixin;

import dlindustries.vigillant.system.module.modules.render.HitAnimations;
import dlindustries.vigillant.system.system;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    private static final ThreadLocal<Hand> currentHand = new ThreadLocal<>();
    private static final ThreadLocal<ItemStack> currentItem = new ThreadLocal<>();

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float modifyEquipProgress(float originalEquipProgress, AbstractClientPlayerEntity player) {
        HitAnimations module = system.INSTANCE.getModuleManager().getModule(HitAnimations.class);
        if (module != null && module.isEnabled() && module.isInstantEquipEnabled() && !player.isUsingItem()) {
            return 0.0f;
        }
        return originalEquipProgress;
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void onApplySwingOffset(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        HitAnimations module = system.INSTANCE.getModuleManager().getModule(HitAnimations.class);
        if (module == null || !module.isEnabled()) return;

        Hand hand = currentHand.get();
        ItemStack stack = currentItem.get();
        if (hand == null || stack == null) return;

        if (module.onRenderFirstPerson(matrices, swingProgress, stack, hand)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onRenderFirstPersonItemHead(
            AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci
    ) {
        currentHand.set(hand);
        currentItem.set(item);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("TAIL"))
    private void onRenderFirstPersonItemTail(
            AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci
    ) {
        currentHand.remove();
        currentItem.remove();
    }
}
