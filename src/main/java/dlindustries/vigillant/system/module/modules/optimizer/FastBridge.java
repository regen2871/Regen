package dlindustries.vigillant.system.module.modules.optimizer;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.utils.EncryptedString;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;

public final class FastBridge extends Module implements TickListener {

    private boolean bridging;

    public FastBridge() {
        super(
                EncryptedString.of("Fast Bridge"),
                EncryptedString.of("Automatically sneaks on block edges"),
                -1,
                Category.optimizer
        );
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        bridging = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        stopBridging();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        boolean holdingBlock = mc.player.getMainHandStack().getItem() instanceof BlockItem
                || mc.player.getOffHandStack().getItem() instanceof BlockItem;
        if (!holdingBlock) {
            stopBridging();
            return;
        }

        if (mc.player.getPitch() < 70) {
            stopBridging();
            return;
        }

        BlockPos below = BlockPos.ofFloored(mc.player.getPos()).down();
        if (mc.world.getBlockState(below).isReplaceable()
                && mc.world.getBlockState(below.down()).isReplaceable()
                && mc.world.getBlockState(below.down().down()).isReplaceable()) {
            mc.options.sneakKey.setPressed(true);
            bridging = true;
        } else {
            stopBridging();
        }
    }

    private void stopBridging() {
        if (bridging) {
            mc.options.sneakKey.setPressed(false);
            bridging = false;
        }
    }
}
