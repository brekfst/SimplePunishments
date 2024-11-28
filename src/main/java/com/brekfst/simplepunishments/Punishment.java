package com.brekfst.simplepunishments;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class Punishment {
    private final UUID id;
    private final UUID targetId;
    private final PunishmentType type;
    private final String reason;
    private final UUID issuerId;
    private final Instant createdAt;
    private final Long duration;
    private String bannedIP;
    private final SimplePunishments plugin;
    private boolean active;

    // Constructor for new punishments
    public Punishment(SimplePunishments plugin, UUID targetId, PunishmentType type, String reason, UUID issuerId, Long duration, String ip, boolean active) {
        this.plugin = plugin;
        this.id = UUID.randomUUID();
        this.targetId = targetId;
        this.type = type;
        this.reason = reason;
        this.issuerId = issuerId;
        this.createdAt = Instant.now();
        this.duration = duration;
        this.active = true;
        this.bannedIP = ip;
        this.active = active;
    }

    // Constructor for loading from database
    public Punishment(SimplePunishments plugin, UUID id, UUID targetId, PunishmentType type, String reason,
                      UUID issuerId, Instant createdAt, Long duration, String ip, boolean active) {
        this.plugin = plugin;
        this.id = id;
        this.targetId = targetId;
        this.type = type;
        this.reason = reason;
        this.issuerId = issuerId;
        this.createdAt = createdAt;
        this.duration = duration;
        this.bannedIP = ip;
        this.active = active;
    }
    // Basic getters
    public UUID getId() { return id; }
    public UUID getTargetId() { return targetId; }
    public PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public UUID getIssuerId() { return issuerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getDuration() { return duration; }
    public boolean isActive() {
        return active;
    }
    public String getBannedIP() {
        return bannedIP;
    }

    public boolean isPermanent() {
        return duration == null || duration <= 0;
    }

    public boolean isExpired() {
        if (!isActive()) {
            return true;
        }

        if (isPermanent()) {
            return false;
        }

        Instant expirationTime = getExpirationTime();
        if (expirationTime == null) {
            return false;
        }

        return Instant.now().isAfter(expirationTime);
    }

    public Instant getExpirationTime() {
        if (duration == null || isPermanent()) {
            return null;
        }
        return createdAt.plusSeconds(duration);
    }

    public void deactivate() {
        this.active = false;
        plugin.getDatabaseManager().updatePunishment(this);
    }

    public String getFormattedDuration() {
        if (isPermanent()) return "Permanent";
        long seconds = duration;
        StringBuilder formatted = new StringBuilder();

        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        if (days > 0) formatted.append(days).append("d ");
        if (hours > 0) formatted.append(hours).append("h ");
        if (minutes > 0) formatted.append(minutes).append("m ");
        if (seconds > 0) formatted.append(seconds).append("s");

        return formatted.toString().trim();
    }

    public String getFormattedTimeLeft() {
        if (isPermanent()) return "Permanent";
        if (isExpired()) return "Expired";

        long secondsLeft = Duration.between(Instant.now(), getExpirationTime()).getSeconds();
        StringBuilder formatted = new StringBuilder();

        long days = secondsLeft / 86400;
        secondsLeft %= 86400;
        long hours = secondsLeft / 3600;
        secondsLeft %= 3600;
        long minutes = secondsLeft / 60;
        secondsLeft %= 60;

        if (days > 0) formatted.append(days).append("d ");
        if (hours > 0) formatted.append(hours).append("h ");
        if (minutes > 0) formatted.append(minutes).append("m ");
        if (secondsLeft > 0) formatted.append(secondsLeft).append("s");

        return formatted.toString().trim();
    }
}