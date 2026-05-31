package dlindustries.vigillant.system.module.setting;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EnchantmentSetting extends Setting<EnchantmentSetting> {
	private final List<RegistryKey<Enchantment>> enchantments = new ArrayList<>();
	private final Set<String> amethystEnchants = new HashSet<>();
	private final Map<String, Object> metadata = new HashMap<>();

	public EnchantmentSetting(CharSequence name) {
		super(name);
	}

	public List<RegistryKey<Enchantment>> getEnchantments() {
		return enchantments;
	}

	public boolean isEmpty() {
		return enchantments.isEmpty() && amethystEnchants.isEmpty();
	}

	public void addEnchantment(RegistryKey<Enchantment> enchantment) {
		if (!enchantments.contains(enchantment)) {
			enchantments.add(enchantment);
		}
	}

	public void removeEnchantment(RegistryKey<Enchantment> enchantment) {
		enchantments.remove(enchantment);
	}

	public void clear() {
		enchantments.clear();
		amethystEnchants.clear();
	}

	public void addAmethystEnchant(String enchantName) {
		amethystEnchants.add(enchantName);
		saveAmethystMetadata();
	}

	public void removeAmethystEnchant(String enchantName) {
		amethystEnchants.remove(enchantName);
		saveAmethystMetadata();
	}

	public boolean hasAmethystEnchant(String enchantName) {
		return amethystEnchants.contains(enchantName);
	}

	public Set<String> getAmethystEnchants() {
		return new HashSet<>(amethystEnchants);
	}

	public boolean hasAmethystPickaxe() {
		return amethystEnchants.contains("Amethyst Pickaxe");
	}

	public boolean hasAmethystAxe() {
		return amethystEnchants.contains("Amethyst Axe");
	}

	public boolean hasAmethystSellAxe() {
		return amethystEnchants.contains("Amethyst Sell Axe");
	}

	public boolean hasAmethystShovel() {
		return amethystEnchants.contains("Amethyst Shovel");
	}

	public int getTotalCount() {
		return enchantments.size() + amethystEnchants.size();
	}

	public void setMetadata(String key, Object value) {
		metadata.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T getMetadata(String key) {
		return (T) metadata.get(key);
	}

	@SuppressWarnings("unchecked")
	public List<String> getMetadataList(String key) {
		Object obj = metadata.get(key);
		if (obj instanceof List<?> list) {
			List<String> result = new ArrayList<>();
			for (Object o : list) {
				if (o instanceof String s) {
					result.add(s);
				}
			}
			return result;
		}
		return new ArrayList<>();
	}

	public void loadAmethystFromMetadata() {
		List<String> saved = getMetadataList("selectedAmethystEnchants");
		amethystEnchants.clear();
		amethystEnchants.addAll(saved);
	}

	private void saveAmethystMetadata() {
		setMetadata("selectedAmethystEnchants", new ArrayList<>(amethystEnchants));
	}
}
