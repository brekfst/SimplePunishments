package com.brekfst.simplepunishments;

import org.bukkit.plugin.java.JavaPlugin;

public class SimplePunishments extends JavaPlugin {
    private PunishmentManager punishmentManager;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        punishmentManager = new PunishmentManager(this);
        registerCommands();
        registerListeners();
        startCleanupTask();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
    }

    private void registerCommands() {
        PunishmentCommand punishmentCommand = new PunishmentCommand(this);
        getCommand("ban").setExecutor(punishmentCommand);
        getCommand("ipban").setExecutor(punishmentCommand);
        getCommand("tempban").setExecutor(punishmentCommand);
        getCommand("unban").setExecutor(punishmentCommand);
        getCommand("mute").setExecutor(punishmentCommand);
        getCommand("tempmute").setExecutor(punishmentCommand);
        getCommand("unmute").setExecutor(punishmentCommand);
        getCommand("kick").setExecutor(punishmentCommand);
        getCommand("history").setExecutor(punishmentCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PunishmentListener(this), this);
    }

    private void startCleanupTask() {
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> punishmentManager.cleanupExpiredPunishments(),
                20L * 60,
                20L * 60 * 30
        );
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}