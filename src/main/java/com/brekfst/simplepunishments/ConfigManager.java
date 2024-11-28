package com.brekfst.simplepunishments;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final SimplePunishments plugin;
    private FileConfiguration config;
    private final String cachedPrefix;

    public ConfigManager(SimplePunishments plugin) {
        this.plugin = plugin;
        loadConfig();
        this.cachedPrefix = config.getString("messages.prefix", "&7[&bSimplePunishments&7]&r ");
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public String getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String fullPath = "messages." + path;
        String message = config.getString(fullPath);

        // If message is null, return a default error message
        if (message == null) {
            return "Missing configuration for: " + path;
        }

        // Replace prefix first
        String replaced = message.replace("%prefix%", cachedPrefix);

        // Then replace all other placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                replaced = replaced.replace("%" + entry.getKey() + "%", value);
            }
        }

        // Convert color codes
        return ChatColor.translateAlternateColorCodes('&', replaced);
    }
}