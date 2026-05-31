package dlindustries.vigillant.system.module.setting;

import java.util.Arrays;
import java.util.List;

public final class ModeSetting<T extends Enum<T>> extends Setting<ModeSetting<T>> {
	public int index;
	private final List<T> possibleValues;
	private final int originalValue;

	public ModeSetting(CharSequence name, T defaultValue, Class<T> type) {
		super(name);
		T[] values = type.getEnumConstants();
		this.possibleValues = Arrays.asList(values);
		this.index = this.possibleValues.indexOf(defaultValue);
		this.originalValue = this.index;
	}

	public T getMode() {
		if (index < 0 || index >= possibleValues.size()) {
			index = 0;
		}
		return possibleValues.get(index);
	}

	public T getValue() {
		return getMode();
	}

	public void setMode(T mode) {
		int newIndex = possibleValues.indexOf(mode);
		if (newIndex >= 0) {
			index = newIndex;
		}
	}

	public void setModeIndex(int mode) {
		if (mode >= 0 && mode < possibleValues.size()) {
			index = mode;
		} else {
			index = 0;
		}
	}

	public int getModeIndex() {
		return index;
	}

	public int getOriginalValue() {
		return originalValue;
	}

	public void cycle() {
		if (index < possibleValues.size() - 1)
			index++;
		else index = 0;
	}

	public boolean isMode(T mode) {
		return index == possibleValues.indexOf(mode);
	}
}