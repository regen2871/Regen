package dlindustries.vigillant.system.utils.rotation;

import dlindustries.vigillant.system.event.EventManager;
import dlindustries.vigillant.system.event.events.*;
import dlindustries.vigillant.system.system;
import dlindustries.vigillant.system.utils.RotationUtils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

import static dlindustries.vigillant.system.system.mc;

public final class RotatorManager implements PacketSendListener, BlockBreakingListener, ItemUseListener, AttackListener, MovementPacketListener, PacketReceiveListener {
	private boolean enabled;
	private boolean rotateBack;
	private boolean resetRotation;
	private final EventManager eventManager = system.INSTANCE.eventManager;
	private Rotation currentRotation;
	private float clientYaw, clientPitch;
	private float serverYaw, serverPitch;
	private boolean packetSpoofActive;
	private boolean packetSpoofSaved;

	public RotatorManager() {
		eventManager.add(PacketSendListener.class, this);
		eventManager.add(AttackListener.class, this);
		eventManager.add(ItemUseListener.class, this);
		eventManager.add(MovementPacketListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(BlockBreakingListener.class, this);

		enabled = false;
		rotateBack = false;
		resetRotation = false;
		packetSpoofActive = false;
		packetSpoofSaved = false;
	}

	public void shutDown() {
		eventManager.remove(PacketSendListener.class, this);
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(ItemUseListener.class, this);
		eventManager.remove(MovementPacketListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(BlockBreakingListener.class, this);
	}

	public Rotation getServerRotation() {
		return new Rotation(serverYaw, serverPitch);
	}

	public void enable() {
		enabled = true;
		rotateBack = false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void disable() {
		if (isEnabled()) {
			enabled = false;
			if (!rotateBack) rotateBack = true;
		}
		endPacketSpoof();
	}

	public void setRotation(Rotation rotation) {
		currentRotation = rotation;
	}

	public void setRotation(double yaw, double pitch) {
		setRotation(new Rotation(yaw, pitch));
	}

	/** Applies rotation only for movement packets / interactions without leaving the camera turned. */
	public void runWithSilentRotation(Rotation rotation, Runnable action) {
		if (mc.player == null) {
			if (action != null) action.run();
			return;
		}

		float previousYaw = mc.player.getYaw();
		float previousPitch = mc.player.getPitch();
		mc.player.setYaw((float) rotation.yaw());
		mc.player.setPitch((float) rotation.pitch());
		setServerRotation(rotation);
		try {
			if (action != null) action.run();
		} finally {
			mc.player.setYaw(previousYaw);
			mc.player.setPitch(previousPitch);
		}
	}

	/** Queues a silent rotation applied only while movement packets are sent. */
	public void requestSilentRotation(Rotation rotation) {
		currentRotation = rotation;
		packetSpoofActive = true;
	}

	public void clearSilentRotation() {
		packetSpoofActive = false;
		currentRotation = null;
		endPacketSpoof();
	}

	public boolean hasSilentRotation() {
		return packetSpoofActive && currentRotation != null;
	}

	public void beginPacketSpoof(Rotation rotation) {
		if (mc.player == null) return;
		if (!packetSpoofSaved) {
			clientYaw = mc.player.getYaw();
			clientPitch = mc.player.getPitch();
			packetSpoofSaved = true;
		}
		currentRotation = rotation;
		packetSpoofActive = true;
		setServerRotation(rotation);
		mc.player.setYaw((float) rotation.yaw());
		mc.player.setPitch((float) rotation.pitch());
	}

	public void endPacketSpoof() {
		if (!packetSpoofSaved || mc.player == null) {
			packetSpoofActive = false;
			packetSpoofSaved = false;
			return;
		}
		mc.player.setYaw(clientYaw);
		mc.player.setPitch(clientPitch);
		packetSpoofActive = false;
		packetSpoofSaved = false;
	}

	public boolean isPacketSpoofActive() {
		return packetSpoofActive;
	}

	private void resetClientRotation() {
		mc.player.setYaw(clientYaw);
		mc.player.setPitch(clientPitch);
		resetRotation = false;
	}

	public void setClientRotation(Rotation rotation) {
		this.clientYaw = mc.player.getYaw();
		this.clientPitch = mc.player.getPitch();
		mc.player.setYaw((float) rotation.yaw());
		mc.player.setPitch((float) rotation.pitch());
		resetRotation = true;
	}

	public void setServerRotation(Rotation rotation) {
		this.serverYaw = (float) rotation.yaw();
		this.serverPitch = (float) rotation.pitch();
	}

	private boolean wasDisabled;

	@Override
	public void onAttack(AttackEvent event) {
		if (!isEnabled() && wasDisabled) {
			enabled = true;
			wasDisabled = false;
		}
	}

	@Override
	public void onItemUse(ItemUseEvent event) {
		if (!event.isCancelled() && isEnabled()) {
			enabled = false;
			wasDisabled = true;
		}
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (event.packet instanceof PlayerMoveC2SPacket packet) {
			serverYaw = packet.getYaw(serverYaw);
			serverPitch = packet.getPitch(serverPitch);
		}
	}

	@Override
	public void onBlockBreaking(BlockBreakingEvent event) {
		if (!event.isCancelled() && isEnabled()) {
			enabled = false;
			wasDisabled = true;
		}
	}

	@Override
	public void onSendMovementPackets() {
		if (packetSpoofActive && currentRotation != null) {
			beginPacketSpoof(currentRotation);
			return;
		}

		if (isEnabled() && currentRotation != null) {
			setClientRotation(currentRotation);
			setServerRotation(currentRotation);
			return;
		}

		if (rotateBack) {
			Rotation serverRot = new Rotation(serverYaw, serverPitch);
			Rotation clientRot = new Rotation(mc.player.getYaw(), mc.player.getPitch());

			if (RotationUtils.getTotalDiff(serverRot, clientRot) > 1) {
				Rotation smoothRotation = RotationUtils.getSmoothRotation(serverRot, clientRot, 0.2);
				setClientRotation(smoothRotation);
				setServerRotation(smoothRotation);
			} else {
				rotateBack = false;
			}
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
			serverYaw = packet.getYaw();
			serverPitch = packet.getPitch();
		}
	}
}
