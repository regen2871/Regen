package dlindustries.vigillant.system.module.modules.crystal;

import dlindustries.vigillant.system.event.events.HudListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Random;

public final class AutoTotem extends Module implements HudListener {

    private final NumberSetting fastDelay = new NumberSetting(EncryptedString.of("Fast Delay (ms)"), 50, 300, 67, 1);
    private final NumberSetting slowDelay = new NumberSetting(EncryptedString.of("Fumble Delay (ms)"), 300, 800, 325, 1);
    private final NumberSetting fumbleChance = new NumberSetting(EncryptedString.of("Fumble Chance %"), 0, 100, 55, 1);
    private final BooleanSetting autoOpen = new BooleanSetting(EncryptedString.of("Auto Open Inv"), true);
    private final BooleanSetting shutInventory = new BooleanSetting(EncryptedString.of("Auto Close"), true);

    private State currentState = State.IDLE;
    private long actionTimer = -1;
    private Slot targetSlot;
    private boolean openedByBot;
    private final Random random = new Random();

    public AutoTotem() {
        super(
                EncryptedString.of("Auto Totem"),
                EncryptedString.of("Automatically equips totems to offhand with humanized timing"),
                -1,
                Category.CRYSTAL
        );
        addSettings(fastDelay, slowDelay, fumbleChance, autoOpen, shutInventory);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        resetState();
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }
        if (mc.player.isDead() || mc.player.getHealth() <= 0 || mc.currentScreen instanceof DeathScreen) {
            resetState();
            return;
        }

        if (mc.currentScreen == null) {
            handlePlayingLogic();
        } else if (mc.currentScreen instanceof HandledScreen<?> screen) {
            handleInventoryLogic(screen);
        } else {
            resetState();
        }
    }

    private void handlePlayingLogic() {
        if (currentState == State.IDLE) {
            openedByBot = false;
        }

        boolean needsRefill = !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (!autoOpen.getValue() || !needsRefill || !hasTotemInInventory()) {
            currentState = State.IDLE;
            return;
        }

        switch (currentState) {
            case IDLE, COOLDOWN, CLOSING -> scheduleNextState(State.PRE_OPENING, getBimodalDelay());
            case PRE_OPENING -> {
                if (System.currentTimeMillis() >= actionTimer) {
                    if (mc.currentScreen == null) {
                        openedByBot = true;
                        mc.setScreen(new InventoryScreen(mc.player));
                        scheduleNextState(State.OPENING_WAIT, 50 + random.nextInt(50));
                    } else {
                        resetState();
                    }
                }
            }
            default -> resetState();
        }
    }

    private void handleInventoryLogic(HandledScreen<?> screen) {
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
            currentState = State.IDLE;
            actionTimer = System.currentTimeMillis() + 200;
            return;
        }

        switch (currentState) {
            case IDLE, PRE_OPENING, OPENING_WAIT -> scheduleNextState(State.SCANNING, 50 + random.nextInt(50));
            case SCANNING -> {
                if (System.currentTimeMillis() < actionTimer) return;

                if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    Slot totem = findTotem(screen);
                    if (totem != null) {
                        targetSlot = totem;
                        scheduleNextState(State.SCHEDULED, getBimodalDelay());
                        return;
                    }
                }

                if (openedByBot && shutInventory.getValue()) {
                    scheduleNextState(State.CLOSING, 100 + random.nextInt(100));
                } else {
                    currentState = State.IDLE;
                    actionTimer = System.currentTimeMillis() + 500;
                }
            }
            case SCHEDULED -> {
                if (System.currentTimeMillis() >= actionTimer) {
                    currentState = State.EXECUTING;
                }
            }
            case EXECUTING -> {
                performClick();
                scheduleNextState(State.SCANNING, 60 + random.nextInt(60));
            }
            case CLOSING -> {
                if (System.currentTimeMillis() >= actionTimer) {
                    if (openedByBot) {
                        mc.player.closeHandledScreen();
                    }
                    resetState();
                }
            }
            case COOLDOWN -> scheduleNextState(State.SCANNING, 10);
            default -> {
            }
        }
    }

    private void performClick() {
        if (targetSlot == null || mc.interactionManager == null) return;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, targetSlot.id, 40, SlotActionType.SWAP, mc.player);
    }

    private long getBimodalDelay() {
        boolean isFumble = random.nextInt(100) < fumbleChance.getValueInt();
        long mean = isFumble ? slowDelay.getValueLong() : fastDelay.getValueLong();
        long delay = isFumble
                ? (long) (mean + random.nextGaussian() * 50.0)
                : (long) (mean + random.nextGaussian() * 20.0);
        return Math.max(50, Math.min(delay, 1500));
    }

    private void scheduleNextState(State state, long delayMs) {
        currentState = state;
        actionTimer = System.currentTimeMillis() + delayMs;
    }

    private void resetState() {
        currentState = State.IDLE;
        actionTimer = -1;
        targetSlot = null;
        openedByBot = false;
    }

    private boolean hasTotemInInventory() {
        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }

    private Slot findTotem(HandledScreen<?> screen) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.hasStack() || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) continue;
            if (slot.id == 45) continue;
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == 40) continue;
            return slot;
        }
        return null;
    }

    private enum State {
        IDLE,
        PRE_OPENING,
        OPENING_WAIT,
        SCANNING,
        SCHEDULED,
        EXECUTING,
        COOLDOWN,
        CLOSING
    }
}
