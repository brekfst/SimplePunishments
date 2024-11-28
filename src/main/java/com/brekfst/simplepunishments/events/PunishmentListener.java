package com.brekfst.simplepunishments.events;

import com.brekfst.simplepunishments.punishments.Punishment;
import com.brekfst.simplepunishments.punishments.PunishmentType;
import com.brekfst.simplepunishments.SimplePunishments;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PunishmentListener implements Listener {
    private final SimplePunishments plugin;

    public PunishmentListener(SimplePunishments plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String ip = event.getAddress().getHostAddress();

        plugin.getPunishmentManager().reloadPlayerPunishments(player.getUniqueId());

        if (plugin.getPunishmentManager().isIPBanned(ip)) {
            Punishment ipBan = plugin.getPunishmentManager().getIPBan(ip);
            if (ipBan != null && ipBan.isActive() && !ipBan.isExpired()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reason", ipBan.getReason());
                placeholders.put("duration", "Permanent");
                placeholders.put("issuer", ipBan.getIssuerId() != null ?
                        plugin.getServer().getOfflinePlayer(ipBan.getIssuerId()).getName() : "Console");

                event.disallow(PlayerLoginEvent.Result.KICK_BANNED,
                        plugin.getConfigManager().getMessage("ipban-message", placeholders));
                return;
            }
        }

        Optional<Punishment> ban = plugin.getPunishmentManager()
                .getActivePunishment(player.getUniqueId(), PunishmentType.BAN);

        if (ban.isPresent()) {
            Punishment punishment = ban.get();
            if (punishment.isActive() && !punishment.isExpired()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reason", punishment.getReason());
                placeholders.put("duration", punishment.getDuration() == null ? "Permanent" :
                        formatDuration(punishment.getDuration()));
                placeholders.put("issuer", punishment.getIssuerId() != null ?
                        plugin.getServer().getOfflinePlayer(punishment.getIssuerId()).getName() : "Console");
                placeholders.put("expires", punishment.isPermanent() ? "Never" :
                        plugin.getPunishmentManager().formatExpiration(punishment.getExpirationTime())); // Time remaining

                String messageKey = punishment.isPermanent() ? "ban-message" : "temp-ban-message";
                String kickMessage = plugin.getConfigManager().getMessage(messageKey, placeholders);

                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID playerId = event.getUniqueId();
        plugin.getPunishmentManager().reloadPlayerPunishments(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Optional<Punishment> activeMute = plugin.getPunishmentManager()
                .getActivePunishment(event.getPlayer().getUniqueId(), PunishmentType.MUTE);

        if (activeMute.isPresent()) {
            Punishment mute = activeMute.get();
            if (mute.isActive() && !mute.isExpired()) {
                event.setCancelled(true);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reason", mute.getReason());
                placeholders.put("duration", mute.getFormattedDuration());
                placeholders.put("time-left", mute.getFormattedTimeLeft());
                placeholders.put("expires", mute.getFormattedTimeLeft());
                placeholders.put("issuer", mute.getIssuerId() != null ?
                        plugin.getServer().getOfflinePlayer(mute.getIssuerId()).getName() : "Console");

                String messageKey = mute.isPermanent() ? "mute-message" : "temp-mute-message";
                event.getPlayer().sendMessage(plugin.getConfigManager().getMessage(messageKey, placeholders));
            }
        }
    }

    @EventHandler
    public void onPunishment(PunishmentEvent event) {
        Punishment punishment = event.getPunishment();
        String targetName = plugin.getServer().getOfflinePlayer(punishment.getTargetId()).getName();
        String issuerName = punishment.getIssuerId() != null ?
                plugin.getServer().getOfflinePlayer(punishment.getIssuerId()).getName() : "Console";

        plugin.getLogger().info(String.format(
                "Player %s was %s by %s for: %s",
                targetName,
                punishment.getType().toString().toLowerCase(),
                issuerName,
                punishment.getReason()
        ));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        if (seconds < 604800) return (seconds / 86400) + "d";
        return (seconds / 604800) + "w";
    }
}