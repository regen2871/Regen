package dlindustries.vigillant.system.module.modules.mace;

import dlindustries.vigillant.system.event.events.GameRenderListener;
import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.event.events.GameRenderListener.GameRenderEvent;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.ModeSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.RotationUtils;
import dlindustries.vigillant.system.utils.rotation.Rotation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

public final class AutoMace extends Module implements GameRenderListener, TickListener {

	public enum AimMode {
		STRICT("Strict"),
		LOOSE("Loose");

		private final String name;

		AimMode(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private final NumberSetting swingRange = new NumberSetting(EncryptedString.of("Swing Range"), 2.5, 4.5, 3.2, 0.1);
	private final NumberSetting aimRange = new NumberSetting(EncryptedString.of("Aim Range"), 0.0, 15.0, 15.0, 0.1);
	private final BooleanSetting autoSwitch = new BooleanSetting(EncryptedString.of("Auto Switch"), true);
	private final NumberSetting rotationSpeed = new NumberSetting(EncryptedString.of("Aim Speed"), 0.0, 35.0, 24.0, 1.0);
	private final NumberSetting minFallDist = new NumberSetting(EncryptedString.of("Min Fall Dist"), 0.0, 5.0, 1.5, 0.1);
	private final NumberSetting cooldown = new NumberSetting(EncryptedString.of("Cooldown (ms)"), 100.0, 2000.0, 500.0, 10.0);
	private final BooleanSetting stunSlam = new BooleanSetting(EncryptedString.of("Stun Slam"), true);
	private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon Only"), false);
	private final ModeSetting<AimMode> aimMode = new ModeSetting<>(EncryptedString.of("Aim Mode"), AimMode.STRICT, AimMode.class);
	private final BooleanSetting ignoreFriends = new BooleanSetting(EncryptedString.of("Ignore Friends"), true);
	private final BooleanSetting silentRotation = new BooleanSetting(EncryptedString.of("Silent Rotation"), false)
			.setDescription(EncryptedString.of("Rotates server-side only; your camera does not turn"));
	private final BooleanSetting smoothRotations = new BooleanSetting(EncryptedString.of("Smooth Rotations"), true);

	private PlayerEntity currentTarget = null;
	private int maceClicksLeft = 0;
	private int originalSlot = -1;
	private long lastComboTime = 0L;
	private long axeHitTime = 0L;
	private int resetTimer = 0;
	private boolean hasAttackedThisCycle = false;
	private double highestY = 0.0;
	private boolean wasOnGround = true;

	// Rotation state
	private boolean rotating = false;
	private Rotation targetRotation = null;
	private Runnable rotationAction = null;
	private float currentYaw;
	private float currentPitch;
	private float startYaw;
	private float startPitch;
	private float rotationProgress = 0.0f;
	private float totalRotationDistance = 0.0f;
	private long lastFrameNanos;

	public AutoMace() {
		super(EncryptedString.of("Auto Mace"),
				EncryptedString.of("Automatically uses mace for smash attacks"),
				-1,
				Category.mace);

		addSettings(swingRange, aimRange, autoSwitch, rotationSpeed, minFallDist, cooldown, stunSlam,
				weaponOnly, aimMode, ignoreFriends, silentRotation, smoothRotations);
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		eventManager.add(TickListener.class, this);
		lastFrameNanos = System.nanoTime();
		if (mc.player != null) {
			highestY = mc.player.getY();
			wasOnGround = mc.player.isOnGround();
		}
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(TickListener.class, this);
		dlindustries.vigillant.system.system.INSTANCE.rotatorManager.endPacketSpoof();
		dlindustries.vigillant.system.system.INSTANCE.rotatorManager.disable();
		currentTarget = null;
		maceClicksLeft = 0;
		lastComboTime = 0L;
		resetTimer = 0;
		originalSlot = -1;
		wasOnGround = true;
		hasAttackedThisCycle = false;
		rotating = false;
		targetRotation = null;
		rotationAction = null;
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (!isEnabled()) return;
		if (rotating && smoothRotations.getValue()) {
			updateSmoothRotation();
			return;
		}
		if (rotating) {
			finishRotation();
			return;
		}
		runLogic();
	}

	@Override
	public void onTick() {
		if (!isEnabled()) return;
		hasAttackedThisCycle = false;
	}

	private double getSmartReach() {
		if (mc.player == null) {
			return swingRange.getValue();
		}
		double velocity = Math.abs(mc.player.getVelocity().y);
		double minR = swingRange.getValue();
		double maxR = 3.0;
		double factor = MathHelper.clamp((velocity - 0.5) / 2.0, 0.0, 1.0);
		return minR + factor * (maxR - minR);
	}

	private Vec3d getAimPos(Entity target) {
		if (target == null || mc.player == null) {
			return Vec3d.ZERO;
		}
		Vec3d eyePos = mc.player.getEyePos();
		Box targetBox = target.getBoundingBox();
		double shrinkFactor = 0.3;
		Vec3d center = targetBox.getCenter();
		double xSize = (targetBox.maxX - targetBox.minX) * shrinkFactor;
		double ySize = (targetBox.maxY - targetBox.minY) * shrinkFactor;
		double zSize = (targetBox.maxZ - targetBox.minZ) * shrinkFactor;
		Box shrunkBox = new Box(center.x - xSize / 2.0, center.y - ySize / 2.0, center.z - zSize / 2.0,
				center.x + xSize / 2.0, center.y + ySize / 2.0, center.z + zSize / 2.0);
		Vec3d closestPoint = new Vec3d(
				MathHelper.clamp(eyePos.x, shrunkBox.minX, shrunkBox.maxX),
				MathHelper.clamp(eyePos.y, shrunkBox.minY, shrunkBox.maxY),
				MathHelper.clamp(eyePos.z, shrunkBox.minZ, shrunkBox.maxZ));

		if (mc.player.distanceTo(target) < 2.0) {
			return closestPoint;
		}
		return closestPoint;
	}

	private boolean canExecuteAttack() {
		if (mc.player == null || currentTarget == null) {
			return false;
		}
		double effectiveRange = getSmartReach();
		HitResult hit = mc.crosshairTarget;
		if (hit instanceof EntityHitResult ehr && ehr.getEntity() == currentTarget) {
			return mc.player.distanceTo(currentTarget) <= effectiveRange;
		}
		Vec3d eyePos = mc.player.getEyePos();
		Vec3d lookVec = mc.player.getRotationVec(1.0f);
		Vec3d reachVec = eyePos.add(lookVec.multiply(effectiveRange));
		Box box = currentTarget.getBoundingBox().expand(0.0);
		return box.raycast(eyePos, reachVec).isPresent();
	}

	private boolean shouldRotate() {
		return aimRange.getValue() > 0.1;
	}

	private void runLogic() {
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (isInLiquidOrWeb()) {
			stopAiming();
			return;
		}

		boolean isOnGroundNow = mc.player.isOnGround();
		highestY = isOnGroundNow ? mc.player.getY() : Math.max(highestY, mc.player.getY());
		double manualFallDist = Math.max(0.0, highestY - mc.player.getY());
		wasOnGround = isOnGroundNow;

		int bestMaceSlot = findBestMace();
		boolean isHoldingMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;
		boolean canUseMace = isHoldingMace || (autoSwitch.getValue() && bestMaceSlot != -1);

		if (!canUseMace) {
			stopAiming();
			return;
		}

		if (weaponOnly.getValue() && !isHoldingWeapon()) {
			stopAiming();
			return;
		}

		if (resetTimer > 0) {
			handleResetSequence();
			return;
		}

		if (maceClicksLeft > 0) {
			doMace();
			return;
		}

		if (System.currentTimeMillis() - lastComboTime < cooldown.getValue()) {
			return;
		}

		currentTarget = findTarget();
		if (currentTarget == null) {
			stopAiming();
			return;
		}

		boolean gameSaysFalling = mc.player.fallDistance >= minFallDist.getValue();
		boolean manualSaysFalling = manualFallDist >= minFallDist.getValue();
		boolean isFalling = gameSaysFalling || manualSaysFalling;

		if (!isFalling && minFallDist.getValue() > 0.1) {
			stopAiming();
			return;
		}

		boolean isBlocking = currentTarget.isBlocking();
		boolean canStunSlam = stunSlam.getValue() && isBlocking;

		if (canStunSlam) {
			stunSlam();
		} else {
			runDirectMaceLogic();
		}
	}

	private boolean isInLiquidOrWeb() {
		if (mc.player == null || mc.world == null) {
			return false;
		}
		Box box = mc.player.getBoundingBox();
		BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);
		BlockPos.Mutable mutable = new BlockPos.Mutable();

		for (int x = min.getX(); x <= max.getX(); ++x) {
			for (int y = min.getY(); y <= max.getY(); ++y) {
				for (int z = min.getZ(); z <= max.getZ(); ++z) {
					mutable.set(x, y, z);
					BlockState state = mc.world.getBlockState(mutable);
					if (state.getFluidState().isIn(FluidTags.WATER) || state.getBlock() == Blocks.COBWEB) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void stunSlam() {
		if (mc.player.distanceTo(currentTarget) > aimRange.getValue()) {
			stopAiming();
			currentTarget = null;
			maceClicksLeft = 0;
			originalSlot = -1;
			return;
		}

		Vec3d aimPos = getAimPos(currentTarget);
		if (shouldRotate()) {
			rotateThen(aimPos, () -> {
				if (!hasAttackedThisCycle && canExecuteAttack()) {
					int axeSlot = findAxe();
					int maceSlot = findBestMace();
					if (axeSlot != -1 && maceSlot != -1) {
						breakShield(axeSlot, maceSlot);
					}
				}
			});
		} else {
			if (!hasAttackedThisCycle && canExecuteAttack()) {
				int axeSlot = findAxe();
				int maceSlot = findBestMace();
				if (axeSlot != -1 && maceSlot != -1) {
					breakShield(axeSlot, maceSlot);
				}
			}
		}
	}

	private void breakShield(int axeSlot, int maceSlot) {
		if (currentTarget == null) {
			return;
		}
		originalSlot = maceSlot;
		if (autoSwitch.getValue()) {
			mc.player.getInventory().selectedSlot = axeSlot;
		}
		attackTarget();
		hasAttackedThisCycle = true;
		maceClicksLeft = 1;
		axeHitTime = System.currentTimeMillis();
	}

	private void doMace() {
		if (currentTarget == null || !currentTarget.isAlive() ||
				mc.player.distanceTo(currentTarget) > aimRange.getValue()) {
			resetSlot();
			maceClicksLeft = 0;
			originalSlot = -1;
			stopAiming();
			return;
		}

		long timeSinceAxe = System.currentTimeMillis() - axeHitTime;
		if (timeSinceAxe < 55L) {
			return;
		}
		if (timeSinceAxe > 1500L) {
			resetSlot();
			maceClicksLeft = 0;
			originalSlot = -1;
			stopAiming();
			return;
		}

		Vec3d aimPos = getAimPos(currentTarget);
		if (shouldRotate()) {
			rotateThen(aimPos, () -> {
				if (!hasAttackedThisCycle && canExecuteAttack()) {
					if (autoSwitch.getValue() && originalSlot >= 0 && originalSlot < 9) {
						mc.player.getInventory().selectedSlot = originalSlot;
					}
					attackTarget();
					hasAttackedThisCycle = true;
					maceClicksLeft = 0;
					resetTimer = 8;
					lastComboTime = System.currentTimeMillis();
				}
			});
		} else {
			if (!hasAttackedThisCycle && canExecuteAttack()) {
				if (autoSwitch.getValue() && originalSlot >= 0 && originalSlot < 9) {
					mc.player.getInventory().selectedSlot = originalSlot;
				}
				attackTarget();
				hasAttackedThisCycle = true;
				maceClicksLeft = 0;
				resetTimer = 8;
				lastComboTime = System.currentTimeMillis();
			}
		}
	}

	private void runDirectMaceLogic() {
		Vec3d aimPos = getAimPos(currentTarget);
		int maceSlot = findBestMace();

		if (shouldRotate()) {
			rotateThen(aimPos, () -> {
				if (!hasAttackedThisCycle && canExecuteAttack() && maceSlot != -1) {
					performDirectMaceSmash(maceSlot);
				}
			});
		} else {
			if (!hasAttackedThisCycle && canExecuteAttack() && maceSlot != -1) {
				performDirectMaceSmash(maceSlot);
			}
		}
	}

	private void performDirectMaceSmash(int maceSlot) {
		if (autoSwitch.getValue()) {
			mc.player.getInventory().selectedSlot = maceSlot;
		}
		attackTarget();
		hasAttackedThisCycle = true;
		lastComboTime = System.currentTimeMillis();
		resetTimer = 5;
	}

	private void resetSlot() {
		if (autoSwitch.getValue() && originalSlot >= 0 && originalSlot < 9) {
			mc.player.getInventory().selectedSlot = originalSlot;
		}
	}

	private void handleResetSequence() {
		resetTimer--;
		if (currentTarget != null && currentTarget.isAlive() &&
				mc.player.distanceTo(currentTarget) <= aimRange.getValue() && shouldRotate()) {
			Vec3d aimPos = getAimPos(currentTarget);
			rotateThen(aimPos, () -> {});
		}
		if (resetTimer <= 0) {
			stopAiming();
		}
	}

	private boolean isHoldingWeapon() {
		if (mc.player == null) {
			return false;
		}
		Item item = mc.player.getMainHandStack().getItem();
		return item instanceof MaceItem || item instanceof AxeItem || item instanceof SwordItem;
	}

	private int findBestMace() {
		int bestSlot = -1;
		int maxDensity = -1;
		for (int i = 0; i < 9; ++i) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!(stack.getItem() instanceof MaceItem)) continue;
			int densityLevel = getDensityLevel(stack);
			if (densityLevel <= maxDensity) continue;
			maxDensity = densityLevel;
			bestSlot = i;
		}
		return bestSlot;
	}

	private int getDensityLevel(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
		if (enchantments == null) {
			return 0;
		}
		for (RegistryEntry<?> entry : enchantments.getEnchantments()) {
			if (!entry.getKey().isPresent()) continue;
			String id = ((RegistryKey<?>) entry.getKey().get()).getValue().getPath();
			if (id.contains("density")) {
				@SuppressWarnings("unchecked")
				RegistryEntry<Enchantment> enchantEntry = (RegistryEntry<Enchantment>) entry;
				return enchantments.getLevel(enchantEntry);
			}
		}
		return 0;
	}

	private int findAxe() {
		for (int i = 0; i < 9; ++i) {
			if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
				return i;
			}
		}
		return -1;
	}

	private void attackTarget() {
		if (hasAttackedThisCycle) {
			return;
		}
		mc.player.swingHand(Hand.MAIN_HAND);
		mc.interactionManager.attackEntity(mc.player, currentTarget);
		hasAttackedThisCycle = true;
	}

	private void stopAiming() {
		dlindustries.vigillant.system.system.INSTANCE.rotatorManager.endPacketSpoof();
		rotating = false;
		targetRotation = null;
		rotationAction = null;
		originalSlot = -1;
	}

	private PlayerEntity findTarget() {
		if (mc.player == null || mc.world == null) {
			return null;
		}
		return mc.world.getPlayers().stream()
				.filter(p -> p != mc.player && p.isAlive() && !p.isCreative() && !p.isSpectator())
				.filter(p -> mc.player.distanceTo(p) <= aimRange.getValue())
				.filter(p -> !ignoreFriends.getValue() || !dlindustries.vigillant.system.system.INSTANCE.friendManager.isFriend(p))
				.min(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
				.orElse(null);
	}

	// Rotation methods adapted from SafeAnchor
	private void rotateThen(Vec3d pos, Runnable action) {
		Rotation base = getRotationTo(pos);
		targetRotation = base;
		rotationAction = action;

		if (!smoothRotations.getValue()) {
			rotating = true;
			finishRotation();
			return;
		}

		currentYaw = mc.player.getYaw();
		currentPitch = mc.player.getPitch();
		startYaw = currentYaw;
		startPitch = currentPitch;
		rotationProgress = 0.0f;

		float yawDiff = Math.abs(MathHelper.wrapDegrees((float) targetRotation.yaw() - startYaw));
		float pitchDiff = Math.abs((float) targetRotation.pitch() - startPitch);
		totalRotationDistance = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
		if (totalRotationDistance < 0.1f) totalRotationDistance = 0.1f;

		rotating = true;
		lastFrameNanos = System.nanoTime();
	}

	private void updateSmoothRotation() {
		if (mc.player == null || !smoothRotations.getValue()) {
			lastFrameNanos = System.nanoTime();
			return;
		}

		long now = System.nanoTime();
		float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
		lastFrameNanos = now;
		if (delta <= 0.0f || delta > 0.5f) delta = 0.05f;

		float speed = (float) rotationSpeed.getValue();
		float maxStep = speed * delta;
		float yawDiff = MathHelper.wrapDegrees((float) targetRotation.yaw() - currentYaw);
		if (Math.abs(yawDiff) <= maxStep) currentYaw = (float) targetRotation.yaw();
		else currentYaw = MathHelper.wrapDegrees(currentYaw + Math.copySign(maxStep, yawDiff));

		float pitchDiff = (float) targetRotation.pitch() - currentPitch;
		if (Math.abs(pitchDiff) <= maxStep) currentPitch = (float) targetRotation.pitch();
		else currentPitch = MathHelper.clamp(currentPitch + Math.copySign(maxStep, pitchDiff), -90.0f, 90.0f);

		if (silentRotation.getValue()) {
			dlindustries.vigillant.system.system.INSTANCE.rotatorManager
					.beginPacketSpoof(new Rotation(currentYaw, currentPitch));
		} else {
			mc.player.setYaw(currentYaw);
			mc.player.setPitch(currentPitch);
		}

		float yawError = Math.abs(MathHelper.wrapDegrees((float) targetRotation.yaw() - currentYaw));
		float pitchError = Math.abs((float) targetRotation.pitch() - currentPitch);
		if (yawError < 1.0f && pitchError < 1.0f) {
			currentYaw = (float) targetRotation.yaw();
			currentPitch = (float) targetRotation.pitch();
			finishRotation();
		}
	}

	private void finishRotation() {
		Runnable action = rotationAction;
		rotationAction = null;
		rotating = false;

		if (targetRotation == null) {
			if (action != null) action.run();
			return;
		}

		var rotator = dlindustries.vigillant.system.system.INSTANCE.rotatorManager;
		if (silentRotation.getValue()) {
			rotator.runWithSilentRotation(targetRotation, action);
		} else {
			mc.player.setYaw((float) targetRotation.yaw());
			mc.player.setPitch((float) targetRotation.pitch());
			if (action != null) action.run();
		}

		targetRotation = null;
		rotator.endPacketSpoof();
	}

	private Rotation getRotationTo(Vec3d target) {
		Vec3d eyes = mc.player.getEyePos();
		double dx = target.x - eyes.x;
		double dy = target.y - eyes.y;
		double dz = target.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		double yaw = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		double pitch = MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, horizontal)), -90.0, 90.0);
		return new Rotation(yaw, pitch);
	}
}
