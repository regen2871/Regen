package dlindustries.vigillant.system.module.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dlindustries.vigillant.system.event.events.GameRenderListener;
import dlindustries.vigillant.system.event.events.PacketReceiveListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class StorageEsp extends Module implements GameRenderListener, PacketReceiveListener {
	private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 16, 128, 64, 4)
			.setDescription(EncryptedString.of("Max distance to render storage blocks"));
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws lines from your view to storage blocks"));
	private final BooleanSetting donutBypass = new BooleanSetting(EncryptedString.of("Possible Bypass"), false)
			.setDescription(EncryptedString.of("Blocks chunk delta updates used by some anticheats"));
	private final NumberSetting transparency = new NumberSetting(EncryptedString.of("Transparency"), 1, 255, 80, 1)
			.setDescription(EncryptedString.of("Opacity of filled boxes"));
	private final NumberSetting outlineAlpha = new NumberSetting(EncryptedString.of("Outline Alpha"), 0, 255, 180, 5);
	private final BooleanSetting fill = new BooleanSetting(EncryptedString.of("Fill"), true);
	private final BooleanSetting renderOutline = new BooleanSetting(EncryptedString.of("Outline"), true);

	private final StorageEspGroup chests = new StorageEspGroup("Chests", new Color(156, 91, 0));
	private final StorageEspGroup trappedChests = new StorageEspGroup("Trapped Chests", new Color(200, 91, 0));
	private final StorageEspGroup enderChests = new StorageEspGroup("Ender Chests", new Color(131, 44, 236));
	private final StorageEspGroup shulkers = new StorageEspGroup("Shulkers", new Color(0, 153, 158));
	private final StorageEspGroup furnaces = new StorageEspGroup("Furnaces", new Color(125, 125, 125));
	private final StorageEspGroup barrels = new StorageEspGroup("Barrels", new Color(180, 100, 40));
	private final StorageEspGroup dispensers = new StorageEspGroup("Dispensers", new Color(110, 110, 110));
	private final StorageEspGroup hoppers = new StorageEspGroup("Hoppers", new Color(64, 64, 64));
	private final StorageEspGroup enchantTables = new StorageEspGroup("Enchant Tables", new Color(80, 80, 255));
	private final StorageEspGroup spawners = new StorageEspGroup("Spawners", new Color(27, 207, 0));
	private final StorageEspGroup anvils = new StorageEspGroup("Anvils (LAGGY)", new Color(48, 48, 48));

	private final List<StorageEspGroup> groups = List.of(
			chests, trappedChests, enderChests, shulkers, furnaces, barrels,
			dispensers, hoppers, enchantTables, spawners, anvils
	);

	private int tickCounter;
	private static final int ANVIL_UPDATE_INTERVAL = 200;
	private final List<BlockPos> anvilCache = new ArrayList<>();

	public StorageEsp() {
		super(EncryptedString.of("Storage ESP"),
				EncryptedString.of("Highlights storage blocks through walls"),
				-1,
				Category.RENDER);
		addSettings(range, donutBypass, transparency, outlineAlpha, fill, renderOutline, tracers);
		groups.forEach(group -> addSetting(group.enabled));
	}

	@Override
	public void onEnable() {
		eventManager.add(GameRenderListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		anvilCache.clear();
		tickCounter = 0;
	}

	@Override
	public void onDisable() {
		eventManager.remove(GameRenderListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		anvilCache.clear();
	}

	@Override
	public void onGameRender(GameRenderListener.GameRenderEvent event) {
		if (mc.world == null || mc.player == null) return;

		groups.forEach(StorageEspGroup::clear);

		if (anvils.isEnabled()) {
			tickCounter++;
			if (tickCounter >= ANVIL_UPDATE_INTERVAL) {
				updateAnvilCache();
				tickCounter = 0;
			}
			addWithinRange(anvils, anvilCache);
		}

		double maxRangeSq = range.getValue() * range.getValue();
		Vec3d playerPos = mc.player.getPos();

		int viewDist = mc.options.getClampedViewDistance();
		int playerChunkX = mc.player.getChunkPos().x;
		int playerChunkZ = mc.player.getChunkPos().z;

		for (int cx = playerChunkX - viewDist; cx <= playerChunkX + viewDist; cx++) {
			for (int cz = playerChunkZ - viewDist; cz <= playerChunkZ + viewDist; cz++) {
				WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz, false);
				if (chunk != null) processChunk(chunk, playerPos, maxRangeSq);
			}
		}

		renderStorages(event);
	}

	private void addWithinRange(StorageEspGroup group, List<BlockPos> positions) {
		double maxRangeSq = range.getValue() * range.getValue();
		Vec3d playerPos = mc.player.getPos();
		for (BlockPos pos : positions) {
			if (playerPos.squaredDistanceTo(Vec3d.ofCenter(pos)) <= maxRangeSq) {
				group.add(pos);
			}
		}
	}

	private void updateAnvilCache() {
		anvilCache.clear();
		if (!anvils.isEnabled()) return;

		int viewDist = mc.options.getClampedViewDistance();
		int playerChunkX = mc.player.getChunkPos().x;
		int playerChunkZ = mc.player.getChunkPos().z;

		for (int cx = playerChunkX - viewDist; cx <= playerChunkX + viewDist; cx++) {
			for (int cz = playerChunkZ - viewDist; cz <= playerChunkZ + viewDist; cz++) {
				WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz, false);
				if (chunk != null) scanChunkForAnvils(chunk);
			}
		}
	}

	private void scanChunkForAnvils(WorldChunk chunk) {
		int startX = chunk.getPos().x * 16;
		int startZ = chunk.getPos().z * 16;

		for (int x = startX; x < startX + 16; x++) {
			for (int z = startZ; z < startZ + 16; z++) {
				for (int y = mc.world.getTopY(); y >= mc.world.getBottomY() + 10; y--) {
					BlockPos pos = new BlockPos(x, y, z);
					Block block = mc.world.getBlockState(pos).getBlock();
					if (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) {
						anvilCache.add(pos);
						break;
					}
				}
			}
		}
	}

	private void processChunk(WorldChunk chunk, Vec3d playerPos, double maxRangeSq) {
		for (BlockPos pos : chunk.getBlockEntityPositions()) {
			if (playerPos.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxRangeSq) continue;

			BlockEntity blockEntity = mc.world.getBlockEntity(pos);
			if (blockEntity == null) continue;

			if (blockEntity instanceof TrappedChestBlockEntity && trappedChests.isEnabled()) {
				trappedChests.add(pos);
			} else if (blockEntity instanceof ChestBlockEntity && chests.isEnabled()) {
				chests.add(pos);
			} else if (blockEntity instanceof EnderChestBlockEntity && enderChests.isEnabled()) {
				enderChests.add(pos);
			} else if (blockEntity instanceof ShulkerBoxBlockEntity && shulkers.isEnabled()) {
				shulkers.add(pos);
			} else if (blockEntity instanceof AbstractFurnaceBlockEntity && furnaces.isEnabled()) {
				furnaces.add(pos);
			} else if (blockEntity instanceof BarrelBlockEntity && barrels.isEnabled()) {
				barrels.add(pos);
			} else if (blockEntity instanceof DispenserBlockEntity && dispensers.isEnabled()) {
				dispensers.add(pos);
			} else if (blockEntity instanceof HopperBlockEntity && hoppers.isEnabled()) {
				hoppers.add(pos);
			} else if (blockEntity instanceof EnchantingTableBlockEntity && enchantTables.isEnabled()) {
				enchantTables.add(pos);
			} else if (blockEntity instanceof MobSpawnerBlockEntity && spawners.isEnabled()) {
				spawners.add(pos);
			}
		}
	}

	private void renderStorages(GameRenderListener.GameRenderEvent event) {
		MatrixStack matrices = event.matrices;
		Camera camera = mc.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();

		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthFunc(GL11.GL_ALWAYS);

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180));
		matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		for (StorageEspGroup group : groups) {
			if (!group.isEnabled() || group.positions.isEmpty()) continue;

			Color baseColor = group.color;
			Color fillColor = new Color(
					baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
					transparency.getValueInt()
			);
			Color outlineColor = new Color(
					baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
					outlineAlpha.getValueInt()
			);

			for (BlockPos pos : group.positions) {
				Box box = new Box(pos);
				if (fill.getValue()) {
					RenderUtils.renderFilledBox(matrices, box, fillColor);
				}
				if (renderOutline.getValue()) {
					RenderUtils.renderOutlinedBox(matrices, box, outlineColor);
				}
			}

			if (tracers.getValue()) {
				Vec3d start = mc.player.getEyePos().subtract(cameraPos);
				for (BlockPos pos : group.positions) {
					Vec3d end = Vec3d.ofCenter(pos).subtract(cameraPos);
					RenderUtils.renderLine(matrices, start, end, baseColor);
				}
			}
		}

		matrices.pop();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	@Override
	public void onPacketReceive(PacketReceiveListener.PacketReceiveEvent event) {
		if (donutBypass.getValue() && event.packet instanceof ChunkDeltaUpdateS2CPacket) {
			event.cancel();
		}
	}

	private static class StorageEspGroup {
		private final BooleanSetting enabled;
		private final Color color;
		private final List<BlockPos> positions = new ArrayList<>();

		StorageEspGroup(String name, Color defaultColor) {
			this.enabled = new BooleanSetting(name, true);
			this.color = defaultColor;
		}

		void add(BlockPos pos) {
			positions.add(pos);
		}

		void clear() {
			positions.clear();
		}

		boolean isEnabled() {
			return enabled.getValue();
		}
	}
}
