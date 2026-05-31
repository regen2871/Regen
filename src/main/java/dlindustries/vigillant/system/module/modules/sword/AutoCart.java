package dlindustries.vigillant.system.module.modules.sword;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.mixin.MinecraftClientAccessor;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.InventoryUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class AutoCart extends Module implements TickListener {

	private final BooleanSetting autoSwitch = new BooleanSetting(EncryptedString.of("Auto Switch"), true);

	private boolean isActive;
	private int originalSlot = -1;
	private int tickCounter;
	private boolean hasRail;
	private boolean hasTntCart;

	public AutoCart() {
		super(EncryptedString.of("Auto Cart"),
				EncryptedString.of("Places TNT minecarts on rails when shooting arrows"),
				-1,
				Category.sword);
		addSettings(autoSwitch);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		resetState();
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		if (isActive) {
			stopPlacing();
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null)
			return;

		if (mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.BOW)) {
			if (!isActive) {
				startPlacing();
			}
		} else if (isActive && !mc.player.isUsingItem()) {
			if (tickCounter == 0) {
				tickCounter = 1;
			}
		}

		if (!isActive)
			return;

		if (tickCounter == 1) {
			placeRail();
			tickCounter = 2;
		} else if (tickCounter == 2) {
			placeTntCart();
			stopPlacing();
		}
	}

	private void startPlacing() {
		if (isActive || mc.player.getMainHandStack().getItem() != Items.BOW)
			return;

		if (getTargetPosition() == null)
			return;

		isActive = true;
		tickCounter = 0;
		originalSlot = mc.player.getInventory().selectedSlot;
	}

	private void stopPlacing() {
		if (!isActive)
			return;

		if (autoSwitch.getValue() && originalSlot != -1) {
			InventoryUtils.setInvSlot(originalSlot);
		}

		resetState();
	}

	private void resetState() {
		isActive = false;
		originalSlot = -1;
		tickCounter = 0;
		hasRail = false;
		hasTntCart = false;
	}

	private void placeRail() {
		if (hasRail)
			return;

		int railSlot = findAnyRailInHotbar();
		if (railSlot == -1)
			return;

		InventoryUtils.setInvSlot(railSlot);
		((MinecraftClientAccessor) mc).invokeDoItemUse();
		hasRail = true;
	}

	private void placeTntCart() {
		if (hasTntCart) {
			stopPlacing();
			return;
		}

		int tntCartSlot = findTntCartInHotbar();
		if (tntCartSlot == -1)
			return;

		InventoryUtils.setInvSlot(tntCartSlot);
		((MinecraftClientAccessor) mc).invokeDoItemUse();
		hasTntCart = true;
	}

	private BlockPos getTargetPosition() {
		HitResult hitResult = mc.crosshairTarget;
		if (hitResult == null)
			return null;

		if (hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult blockHit = (BlockHitResult) hitResult;
			return blockHit.getBlockPos().offset(blockHit.getSide());
		}

		if (hitResult.getType() == HitResult.Type.ENTITY) {
			return mc.player.getBlockPos().up();
		}

		Vec3d cameraPos = mc.player.getCameraPosVec(1.0f);
		Vec3d rotation = mc.player.getRotationVec(1.0f);
		Vec3d end = cameraPos.add(rotation.multiply(5.0));

		BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
				cameraPos, end,
				RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE,
				mc.player));

		if (blockHit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos().offset(blockHit.getSide());
		}

		return mc.player.getBlockPos().up();
	}

	private int findTntCartInHotbar() {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!stack.isEmpty() && stack.isOf(Items.TNT_MINECART)) {
				return i;
			}
		}
		return -1;
	}

	private int findAnyRailInHotbar() {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!stack.isEmpty() && isRail(stack.getItem())) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isRail(Item item) {
		return item == Items.RAIL
				|| item == Items.POWERED_RAIL
				|| item == Items.DETECTOR_RAIL
				|| item == Items.ACTIVATOR_RAIL;
	}
}
