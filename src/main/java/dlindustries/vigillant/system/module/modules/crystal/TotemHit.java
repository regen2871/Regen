package dlindustries.vigillant.system.module.modules.crystal;

import dlindustries.vigillant.system.event.events.PacketSendListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.InventoryUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

public final class TotemHit extends Module implements PacketSendListener {

	public TotemHit() {
		super(EncryptedString.of("Totem Hit"),
				EncryptedString.of("More knockback when hitting players with totems"),
				-1,
				Category.CRYSTAL);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketSendListener.class, this);
		super.onDisable();
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet))
			return;

		packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
			@Override
			public void interact(Hand hand) {
			}

			@Override
			public void interactAt(Hand hand, net.minecraft.util.math.Vec3d pos) {
			}

			@Override
			public void attack() {
				Entity entity = getTargetEntity(packet);
				if (entity == null) {
					return;
				}
				if (entity instanceof PlayerEntity && mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
					int swordSlot = InventoryUtils.getSwordSlot();
					if (swordSlot != -1) {
						int originalSlot = mc.player.getInventory().selectedSlot;
						InventoryUtils.setInvSlot(swordSlot);
						// Swap back after a short delay
						mc.execute(() -> {
							InventoryUtils.setInvSlot(originalSlot);
						});
					}
				}
			}
		});
	}

	private Entity getTargetEntity(PlayerInteractEntityC2SPacket packet) {
		// Use reflection or mixin to get the entity from the packet
		// For now, we'll use a workaround by checking crosshair target
		if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
			return ((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity();
		}
		return null;
	}
}
