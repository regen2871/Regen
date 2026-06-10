package dlindustries.vigillant.system.module.modules.optimizer;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.InventoryUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public final class AutoTool extends Module implements TickListener {

    private int previousSlot = -1;
    private boolean swapped;

    public AutoTool() {
        super(
                EncryptedString.of("Auto Tool"),
                EncryptedString.of("Automatically swaps to the best tool possible"),
                -1,
                Category.optimizer
        );
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        if (mc.player != null) {
            previousSlot = mc.player.getInventory().selectedSlot;
        }
        swapped = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreSlot();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (mc.crosshairTarget instanceof BlockHitResult blockHit
                && !mc.world.getBlockState(blockHit.getBlockPos()).isAir()) {
            int toolSlot = getTool(blockHit.getBlockPos());
            if (toolSlot != -1 && mc.options.attackKey.isPressed()) {
                if (!swapped) {
                    previousSlot = mc.player.getInventory().selectedSlot;
                    swapped = true;
                }
                InventoryUtils.setInvSlot(toolSlot);
            } else {
                restoreSlot();
            }
        } else {
            restoreSlot();
        }
    }

    private void restoreSlot() {
        if (swapped && previousSlot >= 0 && previousSlot < 9 && mc.player != null) {
            InventoryUtils.setInvSlot(previousSlot);
        }
        swapped = false;
    }

    private int getTool(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return -1;
        if (mc.world.getBlockState(blockPos).isAir()) return -1;

        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getMaxDamage() - stack.getDamage() <= 10) continue;

            float miningSpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(blockPos));
            if (miningSpeed > bestSpeed) {
                bestSpeed = miningSpeed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
