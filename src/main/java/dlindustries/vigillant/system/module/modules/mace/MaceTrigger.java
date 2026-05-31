package dlindustries.vigillant.system.module.modules.mace;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.mixin.MinecraftClientAccessor;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public final class MaceTrigger extends Module implements TickListener {

    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 1.0, 6.0, 4.5, 0.1);
    private final NumberSetting attackDelay = new NumberSetting(EncryptedString.of("Attack Delay"), 0.0, 10.0, 3.0, 1.0);
    private final NumberSetting fovCheck = new NumberSetting(EncryptedString.of("FOV Check"), 0.0, 180.0, 30.0, 5.0);

    private int attackCooldown = 0;

    public MaceTrigger() {
        super(EncryptedString.of("Mace Trigger"),
                EncryptedString.of("Automatically attacks players in your crosshair when holding a mace"),
                -1,
                Category.mace);
        addSettings(range, attackDelay, fovCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (!isHoldingMace()) {
            attackCooldown = 0;
            return;
        }

        Entity entity = null;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
        }

        if (entity instanceof PlayerEntity target && entity != mc.player) {
            if (isTargetValid(target)) {
                if (attackCooldown <= 0) {
                    ((MinecraftClientAccessor) mc).invokeDoAttack();
                    attackCooldown = attackDelay.getValueInt();
                } else {
                    attackCooldown--;
                }
            }
        }
    }

    private boolean isHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().getItem() == Items.MACE;
    }

    private boolean isTargetValid(PlayerEntity target) {
        double distance = mc.player != null ? mc.player.distanceTo(target) : Double.MAX_VALUE;
        if (distance > range.getValue()) return false;

        if (!target.isAlive()) return false;

        if (fovCheck.getValue() > 0) {
            if (mc.player == null) return false;

            Vec3d playerLook = mc.player.getRotationVec(1.0F);
            Vec3d toTarget = target.getPos().subtract(mc.player.getPos()).normalize();

            double angle = Math.toDegrees(Math.acos(playerLook.dotProduct(toTarget)));
            return !(angle > fovCheck.getValue());
        }

        return true;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        attackCooldown = 0;
        super.onDisable();
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }
}
