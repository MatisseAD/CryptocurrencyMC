package fr.jachou.cryptocurrency.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class Messages {
    private static FileConfiguration cfg;

    private Messages() {}

    public static void init(JavaPlugin plugin) {
        cfg = plugin.getConfig();
    }

    public static String t(String key, String def) {
        if (cfg == null) return color(def);
        String val = cfg.getString("messages." + key, def);
        return color(val);
    }

    public static String f(String key, String def, Map<String, String> placeholders) {
        String base = t(key, def);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                base = base.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return base;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
