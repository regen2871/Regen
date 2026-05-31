package dlindustries.vigillant.system.module.setting;

import net.minecraft.item.Item;

public final class ItemSetting extends Setting<ItemSetting> {
	private Item value;
	private final Item originalValue;

	public ItemSetting(CharSequence name, Item defaultValue) {
		super(name);
		this.value = defaultValue;
		this.originalValue = defaultValue;
	}

	public Item getItem() {
		return value;
	}

	public Item getValue() {
		return value;
	}

	public void setValue(Item value) {
		this.value = value;
	}
}
