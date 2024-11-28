package com.brekfst.simplepunishments;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class PunishmentManager {
    private final SimplePunishments plugin;
    private final Map<UUID, List<Punishment>> punishmentCache = new HashMap<>();
    private final Map<String, Punishment> ipBanCache = new HashMap<>();

    public PunishmentManager(SimplePunishments plugin) {
        this.plugin = plugin;
        loadAllPunishments();
    }

    public void punishPlayer(UUID targetId, PunishmentType type, String reason, UUID issuerId, Long duration, String ip) {

        // Create punishment
        Punishment punishment = new Punishment(plugin, targetId, type, reason, issuerId, duration, ip, true);

        // Call event
        PunishmentEvent event = new PunishmentEvent(punishment);
        plugin.getServer().getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            // Save to database
            plugin.getDatabaseManager().savePunishment(punishment);

            // Update cache
            if (type == PunishmentType.IP_BAN && ip != null) {
                ipBanCache.put(ip, punishment);
            }
            punishmentCache.computeIfAbsent(targetId, k -> new ArrayList<>()).add(punishment);

            // Verify punishment was saved
            Optional<Punishment> saved = getActivePunishment(targetId, type);

            // Apply punishment
            applyPunishment(punishment);
        }
    }

    public void logAllPunishments(UUID targetId) {
        List<Punishment> punishments = punishmentCache.get(targetId);
    }

    private void applyPunishment(Punishment punishment) {
        Player player = plugin.getServer().getPlayer(punishment.getTargetId());
        if (player != null && player.isOnline()) {
            switch (punishment.getType()) {
                case BAN, TEMP_BAN -> {
                    Map<String, String> placeholders = createPlaceholders(punishment);
                    player.kickPlayer(plugin.getConfigManager().getMessage(
                            punishment.isPermanent() ? "ban-message" : "temp-ban-message",
                            placeholders
                    ));
                }
                case KICK -> player.kickPlayer(punishment.getReason());
            }
        }
    }

    public void removePunishment(UUID targetId, PunishmentType type) {

        Optional<Punishment> punishment = getActivePunishment(targetId, type);
        if (punishment.isPresent()) {

            Punishment p = punishment.get();
            p.deactivate();

            punishmentCache.clear();
            loadAllPunishments();

            Optional<Punishment> verifyPunishment = getActivePunishment(targetId, type);
            if (verifyPunishment.isEmpty()) {
                plugin.getDatabaseManager().updatePunishment(p);
            }
        }
    }

    public Optional<Punishment> getActivePunishment(UUID targetId, PunishmentType type) {
        List<Punishment> punishments = plugin.getDatabaseManager().loadPlayerPunishments(targetId);

        return punishments.stream()
                .filter(p -> {
                    boolean typeMatch = (type == PunishmentType.BAN) ?
                            (p.getType() == PunishmentType.BAN || p.getType() == PunishmentType.TEMP_BAN) :
                            p.getType() == type;

                    boolean active = p.isActive();

                    return typeMatch && active && !p.isExpired();
                })
                .findFirst();
    }

    public List<Punishment> getPlayerPunishments(UUID targetId) {
        return new ArrayList<>(punishmentCache.getOrDefault(targetId, new ArrayList<>()));
    }

    public void reloadPlayerPunishments(UUID targetId) {
        List<Punishment> punishments = plugin.getDatabaseManager().loadPlayerPunishments(targetId);
        punishmentCache.put(targetId, punishments);
    }

    private void loadAllPunishments() {
        punishmentCache.clear();
        ipBanCache.clear();

        // Load all punishments from database
        List<Punishment> punishments = plugin.getDatabaseManager().loadPunishments();
        for (Punishment punishment : punishments) {
            if (punishment.getType() == PunishmentType.IP_BAN && punishment.getBannedIP() != null) {
                ipBanCache.put(punishment.getBannedIP(), punishment);
            } else {
                punishmentCache.computeIfAbsent(punishment.getTargetId(), k -> new ArrayList<>())
                        .add(punishment);
            }
        }
    }

    public List<Punishment> getAllPunishments() {
        List<Punishment> allPunishments = new ArrayList<>();
        for (List<Punishment> playerPunishments : punishmentCache.values()) {
            allPunishments.addAll(playerPunishments);
        }
        return allPunishments;
    }

    private void cachePunishment(Punishment punishment) {
        punishmentCache.computeIfAbsent(punishment.getTargetId(), k -> new ArrayList<>())
                .add(punishment);
    }

    private Map<String, String> createPlaceholders(Punishment punishment) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("prefix", plugin.getConfigManager().getMessage("prefix"));
        placeholders.put("player", plugin.getServer().getOfflinePlayer(punishment.getTargetId()).getName());
        placeholders.put("reason", punishment.getReason());
        placeholders.put("duration", punishment.getFormattedDuration());
        placeholders.put("time-left", punishment.getFormattedTimeLeft());

        String issuerName = punishment.getIssuerId() != null ?
                plugin.getServer().getOfflinePlayer(punishment.getIssuerId()).getName() :
                "Console";
        placeholders.put("issuer", issuerName);

        if (!punishment.isPermanent() && punishment.getExpirationTime() != null) {
            placeholders.put("expires", formatExpiration(punishment.getExpirationTime()));
        } else {
            placeholders.put("expires", "Never");
        }

        return placeholders;
    }

    public void cleanupExpiredPunishments() {
        punishmentCache.values().forEach(punishments ->
                punishments.stream()
                        .filter(Punishment::isExpired)
                        .forEach(Punishment::deactivate)
        );
    }

    public boolean isIPBanned(String ip) {

        // Always check database first
        Punishment ipBan = plugin.getDatabaseManager().loadIPBan(ip);
        if (ipBan != null) {
            ipBanCache.put(ip, ipBan);
            return ipBan.isActive() && !ipBan.isExpired();
        }

        // Check cache if not in database
        Punishment cachedBan = ipBanCache.get(ip);
        if (cachedBan != null) {
            return cachedBan.isActive() && !cachedBan.isExpired();
        }
        return false;
    }

    public Punishment getIPBan(String ip) {

        // Force database check
        Punishment ipBan = plugin.getDatabaseManager().loadIPBan(ip);
        if (ipBan != null) {
            ipBanCache.put(ip, ipBan);
            return ipBan;
        }

        return null;
    }

    public void removeIPBan(String ip) {
        Punishment ipBan = getIPBan(ip);
        if (ipBan != null && ipBan.isActive()) {
            ipBan.deactivate();
            plugin.getDatabaseManager().updatePunishment(ipBan);
            ipBanCache.remove(ip);
        }
    }

    String formatExpiration(Instant expiration) {
        Duration timeUntil = Duration.between(Instant.now(), expiration);
        long days = timeUntil.toDays();
        long hours = timeUntil.toHoursPart();
        long minutes = timeUntil.toMinutesPart();

        StringBuilder formatted = new StringBuilder();
        if (days > 0) formatted.append(days).append(" days ");
        if (hours > 0) formatted.append(hours).append(" hours ");
        if (days == 0 && minutes > 0) formatted.append(minutes).append(" minutes");
        if (formatted.length() == 0) formatted.append("less than a minute");

        return formatted.toString().trim();
    }
}