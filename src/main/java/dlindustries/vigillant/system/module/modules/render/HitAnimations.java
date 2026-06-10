package dlindustries.vigillant.system.module.modules.render;

import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.ModeSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class HitAnimations extends Module {

    public enum SwingAnimation {
        Slice, Block, Chop, Spin, Pop
    }

    private final ModeSetting<SwingAnimation> swingAnimation = new ModeSetting<>(
            EncryptedString.of("Swing Animation"), SwingAnimation.Slice, SwingAnimation.class
    );
    private final NumberSetting swingSpeed = new NumberSetting(
            EncryptedString.of("Swing Speed"), 0.1, 3.0, 1.0, 0.1
    );
    private final BooleanSetting onlySwords = new BooleanSetting(EncryptedString.of("Only Swords"), false);
    private final BooleanSetting instantEquip = new BooleanSetting(EncryptedString.of("Instant Equip"), true);
    private final BooleanSetting ignoreOffhand = new BooleanSetting(EncryptedString.of("Ignore Offhand"), true);

    public HitAnimations() {
        super(
                EncryptedString.of("Hit Animations"),
                EncryptedString.of("Custom hit and swing animations"),
                -1,
                Category.RENDER
        );
        addSettings(swingAnimation, swingSpeed, instantEquip, onlySwords, ignoreOffhand);
    }

    public double getSwingSpeed() {
        return swingSpeed.getValue();
    }

    public boolean isInstantEquipEnabled() {
        return instantEquip.getValue();
    }

    public boolean onRenderFirstPerson(MatrixStack matrices, float swingProgress, ItemStack itemStack, Hand hand) {
        if (ignoreOffhand.getValue() && hand == Hand.OFF_HAND) {
            return false;
        }
        if (onlySwords.getValue() && !(itemStack.getItem() instanceof SwordItem)) {
            return false;
        }

        float sqrtProgress = MathHelper.sqrt(swingProgress);
        float sinProgress = MathHelper.sin(sqrtProgress * (float) Math.PI);

        switch (swingAnimation.getMode()) {
            case Slice -> applySliceAnimation(matrices, sinProgress);
            case Block -> applyBlockAnimation(matrices, sinProgress);
            case Chop -> applyChopAnimation(matrices, sinProgress);
            case Spin -> applySpinAnimation(matrices, swingProgress);
            case Pop -> applyPopAnimation(matrices, sinProgress);
        }
        return true;
    }

    private void applySliceAnimation(MatrixStack matrices, float sinProgress) {
        matrices.translate(0.27f, 0.2f, -0.16f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-46.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.46f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-46.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-96.97f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.46f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(36.83f * sinProgress));
    }

    private void applyBlockAnimation(MatrixStack matrices, float sinProgress) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-84.23f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-4.58f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.99f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-23.7f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(52.76f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8.16f * sinProgress));
    }

    private void applyChopAnimation(MatrixStack matrices, float sinProgress) {
        matrices.translate(0.27f, 0.2f, -0.16f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-20.5f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-20.5f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-11.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-106.5f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-23.7f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(8.2f * sinProgress));
    }

    private void applyPopAnimation(MatrixStack matrices, float sinProgress) {
        matrices.translate(0.34f, 0.2f, -0.16f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.6f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(4.97f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(52.76f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(8.16f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.46f * sinProgress));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-46.0f * sinProgress));
    }

    private void applySpinAnimation(MatrixStack matrices, float progress) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360.0f * progress));
    }
}
