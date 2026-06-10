package dlindustries.vigillant.system.utils.rotation;

import dlindustries.vigillant.system.event.EventManager;
import dlindustries.vigillant.system.event.events.GameRenderListener;
import dlindustries.vigillant.system.system;
import dlindustries.vigillant.system.utils.RotationUtils;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static dlindustries.vigillant.system.system.mc;

public final class RotationManager implements GameRenderListener {
	public enum Priority {
		HIGH(1),
		HIGHEST(2);

		private final int weight;

		Priority(int weight) {
			this.weight = weight;
		}
	}

	private static RotationManager instance;

	private final Map<Object, RotationEntry> entries = new ConcurrentHashMap<>();
	private long lastFrameNanos = System.nanoTime();

	private RotationManager() {
	}

	public static void init(EventManager eventManager) {
		if (instance == null) {
			instance = new RotationManager();
			eventManager.add(GameRenderListener.class, instance);
		}
	}

	public static void setRotationSupplier(Object owner, Priority priority, Supplier<Vec3d> supplier, double speed, boolean silent) {
		if (instance == null) {
			return;
		}

		RotationEntry entry = instance.entries.computeIfAbsent(owner, ignored -> new RotationEntry());
		if (!entry.active && mc.player != null) {
			entry.currentYaw = mc.player.getYaw();
			entry.currentPitch = mc.player.getPitch();
		}
		entry.priority = priority;
		entry.supplier = supplier;
		entry.speed = speed;
		entry.silent = silent;
		entry.active = true;
	}

	public static void clearTarget(Object owner) {
		if (instance == null) {
			return;
		}

		RotationEntry entry = instance.entries.get(owner);
		if (entry != null) {
			entry.active = false;
		}
	}

	public static void stop(Object owner) {
		if (instance == null) {
			return;
		}
		clearTarget(owner);
		instance.entries.remove(owner);
		var rotator = system.INSTANCE.rotatorManager;
		rotator.endPacketSpoof();
		rotator.clearSilentRotation();
		rotator.disable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		if (mc.player == null || entries.isEmpty()) {
			lastFrameNanos = System.nanoTime();
			return;
		}

		RotationEntry entry = entries.values().stream()
				.filter(e -> e.active && e.supplier != null)
				.max(Comparator.comparingInt(e -> e.priority.weight))
				.orElse(null);

		if (entry == null) {
			lastFrameNanos = System.nanoTime();
			return;
		}

		Vec3d aimPos = entry.supplier.get();
		if (aimPos == null || aimPos == Vec3d.ZERO) {
			lastFrameNanos = System.nanoTime();
			return;
		}

		long now = System.nanoTime();
		float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
		lastFrameNanos = now;
		if (delta <= 0.0f || delta > 0.5f) {
			delta = 0.05f;
		}

		Rotation target = RotationUtils.getDirection(mc.player, aimPos);
		float maxStep = (float) entry.speed * delta;

		float yawDiff = MathHelper.wrapDegrees((float) target.yaw() - entry.currentYaw);
		if (Math.abs(yawDiff) <= maxStep) {
			entry.currentYaw = (float) target.yaw();
		} else {
			entry.currentYaw = MathHelper.wrapDegrees(entry.currentYaw + Math.copySign(maxStep, yawDiff));
		}

		float pitchDiff = (float) target.pitch() - entry.currentPitch;
		if (Math.abs(pitchDiff) <= maxStep) {
			entry.currentPitch = (float) target.pitch();
		} else {
			entry.currentPitch = MathHelper.clamp(entry.currentPitch + Math.copySign(maxStep, pitchDiff), -90.0f, 90.0f);
		}

		Rotation smoothRotation = new Rotation(entry.currentYaw, entry.currentPitch);
		var rotator = system.INSTANCE.rotatorManager;

		if (entry.silent) {
			rotator.beginPacketSpoof(smoothRotation);
		} else {
			rotator.endPacketSpoof();
			mc.player.setYaw(entry.currentYaw);
			mc.player.setPitch(entry.currentPitch);
		}
	}

	private static final class RotationEntry {
		private Priority priority = Priority.HIGH;
		private Supplier<Vec3d> supplier;
		private double speed = 24.0;
		private boolean silent;
		private boolean active;
		private float currentYaw;
		private float currentPitch;

		private RotationEntry() {
		}
	}
}
