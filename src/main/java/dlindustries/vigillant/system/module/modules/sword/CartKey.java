package dlindustries.vigillant.system.module.modules.sword;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.mixin.MinecraftClientAccessor;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.KeybindSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.InventoryUtils;
import dlindustries.vigillant.system.utils.KeyUtils;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class CartKey extends Module implements TickListener {

	private final KeybindSetting activateKey = new KeybindSetting(EncryptedString.of("Key"), GLFW.GLFW_KEY_C, false);
	private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 50, 500, 150, 25);
	private final BooleanSetting silent = new BooleanSetting(EncryptedString.of("Silent"), true);
	private final BooleanSetting switchBack = new BooleanSetting(EncryptedString.of("Switch Back"), true);
	private final NumberSetting switchDelay = new NumberSetting(EncryptedString.of("Switch Delay"), 100, 1000, 250, 50);
	private final BooleanSetting bow = new BooleanSetting(EncryptedString.of("Auto Bow"), true);
	private final NumberSetting bowWindow = new NumberSetting(EncryptedString.of("Bow Window"), 500, 2000, 1000, 100);

	private boolean wasKeyPressed;
	private boolean charging;
	private int originalSlot = -1;
	private long lastAction;
	private long bowStart;
	private long restoreAt = -1;
	private int slotToRestore = -1;
	private State state = State.IDLE;

	public CartKey() {
		super(EncryptedString.of("Cart Key"),
				EncryptedString.of("Places rail + TNT cart, auto switches to bow"),
				-1,
				Category.sword);
		addSettings(activateKey, delay, silent, switchBack, switchDelay, bow, bowWindow);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		wasKeyPressed = false;
		reset();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		if (slotToRestore != -1 && mc.player != null) {
			InventoryUtils.setInvSlot(slotToRestore);
		}
		reset();
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null || mc.currentScreen != null)
			return;

		long now = System.currentTimeMillis();

		if (restoreAt != -1 && now >= restoreAt) {
			if (slotToRestore != -1) {
				InventoryUtils.setInvSlot(slotToRestore);
			}
			slotToRestore = -1;
			restoreAt = -1;
			reset();
			return;
		}

		boolean keyDown = KeyUtils.isKeyPressed(activateKey.getKey());
		boolean rightClick = mc.options.useKey.isPressed();

		if (keyDown && !wasKeyPressed && state == State.IDLE && canPlace()) {
			originalSlot = mc.player.getInventory().selectedSlot;
			state = State.RAIL;
			lastAction = now;
		}
		wasKeyPressed = keyDown;

		switch (state) {
			case IDLE -> {
			}
			case RAIL -> {
				if (now - lastAction >= delay.getValueInt()) {
					placeRail(now);
				}
			}
			case CART -> {
				if (now - lastAction >= delay.getValueInt()) {
					placeCart(now);
				}
			}
			case SWITCH -> {
				if (now - lastAction >= switchDelay.getValueInt()) {
					switchSlot();
				}
			}
			case BOW_WAIT -> {
				if (rightClick) {
					activateBow();
				} else if (now - bowStart >= bowWindow.getValueInt()) {
					reset();
				}
			}
			case BOW_CHARGE -> {
				if (!rightClick && charging) {
					finishBow();
				}
			}
		}
	}

	private boolean canPlace() {
		if (!(mc.crosshairTarget instanceof BlockHitResult hit))
			return false;

		BlockPos pos = hit.getBlockPos().offset(hit.getSide());
		return mc.player.getPos().distanceTo(pos.toCenterPos()) <= 4.5
				&& mc.world.getBlockState(pos).isAir()
				&& hasItem(Items.RAIL)
				&& hasItem(Items.TNT_MINECART);
	}

	private void placeRail(long now) {
		if (useItem(Items.RAIL)) {
			state = State.CART;
			lastAction = now;
		} else {
			reset();
		}
	}

	private void placeCart(long now) {
		if (useItem(Items.TNT_MINECART)) {
			if (bow.getValue()) {
				state = State.BOW_WAIT;
				bowStart = now;
			} else if (switchBack.getValue() && originalSlot != -1) {
				state = State.SWITCH;
				lastAction = now;
			} else {
				reset();
			}
		} else {
			reset();
		}
	}

	private void switchSlot() {
		if (originalSlot != -1) {
			InventoryUtils.setInvSlot(originalSlot);
		}
		reset();
	}

	private void activateBow() {
		int bowSlot = findBow();
		if (bowSlot != -1) {
			InventoryUtils.setInvSlot(bowSlot);
			state = State.BOW_CHARGE;
			charging = true;
		} else {
			reset();
		}
	}

	private void finishBow() {
		charging = false;
		if (switchBack.getValue() && originalSlot != -1) {
			slotToRestore = originalSlot;
			restoreAt = System.currentTimeMillis() + 100;
			state = State.IDLE;
			originalSlot = -1;
		} else {
			reset();
		}
	}

	private boolean hasItem(Item item) {
		return findItem(item) != -1;
	}

	private boolean useItem(Item item) {
		int slot = findItem(item);
		if (slot == -1)
			return false;

		if (silent.getValue()) {
			int current = mc.player.getInventory().selectedSlot;
			InventoryUtils.setInvSlot(slot);
			((MinecraftClientAccessor) mc).invokeDoItemUse();
			InventoryUtils.setInvSlot(current);
		} else {
			InventoryUtils.setInvSlot(slot);
			((MinecraftClientAccessor) mc).invokeDoItemUse();
		}
		return true;
	}

	private int findItem(Item item) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!stack.isEmpty() && stack.isOf(item)) {
				return i;
			}
		}
		return -1;
	}

	private int findBow() {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!stack.isEmpty() && stack.getItem() instanceof BowItem) {
				return i;
			}
		}
		return -1;
	}

	private void reset() {
		state = State.IDLE;
		originalSlot = -1;
		charging = false;
	}

	private enum State {
		IDLE, RAIL, CART, SWITCH, BOW_WAIT, BOW_CHARGE
	}
}
