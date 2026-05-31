package dlindustries.vigillant.system.module.modules.render;

import dlindustries.vigillant.system.event.events.CameraUpdateListener;
import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.mixin.KeyBindingAccessor;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class Freecam extends Module implements TickListener, CameraUpdateListener {
	private final NumberSetting speed = new NumberSetting(EncryptedString.of("Speed"), 1, 10, 1, 1);
	public Vec3d oldPos = Vec3d.ZERO;
	public Vec3d pos = Vec3d.ZERO;

	public Freecam() {
		super(EncryptedString.of("Freecam"),
				EncryptedString.of("Lets you move freely around the world without actually moving"),
				-1,
				Category.RENDER);
		addSettings(speed);
	}

	@Override
	public void onEnable() {
		eventManager.add(TickListener.class, this);
		eventManager.add(CameraUpdateListener.class, this);
		if (mc.player != null) {
			this.oldPos = this.pos = mc.player.getEyePos();
		}
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(TickListener.class, this);
		eventManager.remove(CameraUpdateListener.class, this);

		if (mc.world != null && mc.player != null) {
			mc.player.setVelocity(Vec3d.ZERO);
			mc.worldRenderer.reload();
		}
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.currentScreen != null)
			return;

		mc.options.useKey.setPressed(false);
		mc.options.attackKey.setPressed(false);
		mc.options.forwardKey.setPressed(false);
		mc.options.backKey.setPressed(false);
		mc.options.leftKey.setPressed(false);
		mc.options.rightKey.setPressed(false);
		mc.options.jumpKey.setPressed(false);
		mc.options.sneakKey.setPressed(false);

		float f = (float) Math.PI / 180;
		float f2 = (float) Math.PI;
		ClientPlayerEntity clientPlayerEntity = mc.player;
		Vec3d vec3d = new Vec3d(-MathHelper.sin(-mc.player.getYaw() * f - f2), 0.0, -MathHelper.cos(-clientPlayerEntity.getYaw() * f - f2));
		Vec3d vec3d2 = new Vec3d(0.0, 1.0, 0.0);
		Vec3d vec3d3 = vec3d2.crossProduct(vec3d);
		Vec3d vec3d4 = vec3d.crossProduct(vec3d2);
		Vec3d vec3d5 = Vec3d.ZERO;

		if (isBindingPressed(mc.options.forwardKey)) {
			vec3d5 = vec3d5.add(vec3d);
		}
		if (isBindingPressed(mc.options.backKey)) {
			vec3d5 = vec3d5.subtract(vec3d);
		}
		if (isBindingPressed(mc.options.leftKey)) {
			vec3d5 = vec3d5.add(vec3d3);
		}
		if (isBindingPressed(mc.options.rightKey)) {
			vec3d5 = vec3d5.add(vec3d4);
		}
		if (isBindingPressed(mc.options.jumpKey)) {
			vec3d5 = vec3d5.add(0.0, speed.getValue(), 0.0);
		}
		if (isBindingPressed(mc.options.sneakKey)) {
			vec3d5 = vec3d5.add(0.0, -speed.getValue(), 0.0);
		}

		double moveSpeed = speed.getValue() * (isBindingPressed(mc.options.sprintKey) ? 2 : 1);
		if (vec3d5.lengthSquared() > 0) {
			vec3d5 = vec3d5.normalize().multiply(moveSpeed);
		}

		oldPos = pos;
		pos = pos.add(vec3d5);
	}

	@Override
	public void onCameraUpdate(CameraUpdateEvent event) {
		if (mc.currentScreen != null)
			return;

		float tickDelta = RenderTickCounter.ONE.getTickDelta(true);
		event.setX(MathHelper.lerp(tickDelta, oldPos.x, pos.x));
		event.setY(MathHelper.lerp(tickDelta, oldPos.y, pos.y));
		event.setZ(MathHelper.lerp(tickDelta, oldPos.z, pos.z));
	}

	private boolean isBindingPressed(KeyBinding keyBinding) {
		return GLFW.glfwGetKey(mc.getWindow().getHandle(), ((KeyBindingAccessor) keyBinding).getBoundKey().getCode()) == GLFW.GLFW_PRESS;
	}
}
