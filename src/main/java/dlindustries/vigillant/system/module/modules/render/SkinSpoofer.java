package dlindustries.vigillant.system.module.modules.render;

import dlindustries.vigillant.system.event.events.TickListener;
import dlindustries.vigillant.system.module.Category;
import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.module.setting.BooleanSetting;
import dlindustries.vigillant.system.module.setting.KeybindSetting;
import dlindustries.vigillant.system.module.setting.NumberSetting;
import dlindustries.vigillant.system.utils.EncryptedString;
import dlindustries.vigillant.system.utils.KeyUtils;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

public final class SkinSpoofer extends Module implements TickListener {
    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"),
            0, 250, 25, 1
    ).setDescription(EncryptedString.of("Delay between skin refresh attempts in milliseconds"));

    private final BooleanSetting autoUpdate = new BooleanSetting(EncryptedString.of("Auto Update"), true);
    private final KeybindSetting updateKey = new KeybindSetting(EncryptedString.of("Update Key"), -1, false);

    private long lastUpdateTime = 0;

    public SkinSpoofer() {
        super(
                EncryptedString.of("SkinSpoofer"),
                EncryptedString.of("Spoofs your skin via player list packet replacement"),
                -1,
                Category.RENDER
        );
        addSettings(delay, autoUpdate, updateKey);
    }
    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }
    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }
    @Override
    public void onTick() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        long currentTime = System.currentTimeMillis();
        if (autoUpdate.getValue() && currentTime - lastUpdateTime >= delay.getValueInt()) {
            updateSkin();
            lastUpdateTime = currentTime;
        }
        if (KeyUtils.isKeyPressed(updateKey.getKey())) {
            updateSkin();
            lastUpdateTime = currentTime;
        }
    }
    private void updateSkin() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        PlayerListEntry playerListEntry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
        if (playerListEntry == null) return;

        if (!playerListEntry.getProfile().getProperties().containsKey("textures")) {
            return;
        }

        var textureProperty = playerListEntry.getProfile().getProperties().get("textures").iterator().next();
        playerListEntry.getProfile().getProperties().removeAll("textures");
        playerListEntry.getProfile().getProperties().put("textures", textureProperty);
    }
}
