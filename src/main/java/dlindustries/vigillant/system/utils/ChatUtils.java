package dlindustries.vigillant.system.utils;

import dlindustries.vigillant.system.module.Module;
import dlindustries.vigillant.system.system;
import net.minecraft.text.Text;

import static dlindustries.vigillant.system.system.mc;

public final class ChatUtils {

	private ChatUtils() {
	}

	public static void m(String message) {
		send(buildPrefix() + "§f" + message);
	}

	public static void w(String message) {
		send(buildPrefix() + "§e" + message);
	}

	public static void e(String message) {
		send(buildPrefix() + "§c" + message);
	}

	private static void send(String fullMessage) {
		if (mc != null && mc.player != null) {
			mc.player.sendMessage(Text.literal(fullMessage), false);
		}
	}

	private static String buildPrefix() {
		String moduleName = inferCallerModuleName();
		if (moduleName == null || moduleName.isEmpty()) moduleName = "General";
		return "§c[Regen] §b" + moduleName + " §f| ";
	}

	private static String inferCallerModuleName() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		for (StackTraceElement element : stack) {
			String className = element.getClassName();
			if (className == null) continue;
			if (!className.startsWith("dlindustries.vigillant.system.module.modules.")) continue;
			try {
				Class<?> clazz = Class.forName(className);
				if (Module.class.isAssignableFrom(clazz)) {
					String simple = clazz.getSimpleName();

					if (system.INSTANCE != null && system.INSTANCE.getModuleManager() != null) {
						for (Module mod : system.INSTANCE.getModuleManager().getModules()) {
							if (mod != null && mod.getClass() == clazz) {
								return mod.getName().toString();
							}
						}
					}
					return simple;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}
}
