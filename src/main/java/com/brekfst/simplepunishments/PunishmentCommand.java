package com.brekfst.simplepunishments;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

class PunishmentCommand implements CommandExecutor, TabCompleter {
    private final SimplePunishments plugin;
    private final ConfigManager config;

    public PunishmentCommand(SimplePunishments plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!hasPermission(sender, cmd.getName())) {
            sender.sendMessage(config.getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(config.getMessage("command." + cmd.getName() + ".usage"));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(config.getMessage("player-not-found"));
            return true;
        }

        UUID issuerId = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : config.getMessage("default-reason");

        switch (cmd.getName().toLowerCase()) {
            case "ban" -> handleBan(sender, target, reason, issuerId, null, null);
            case "tempban" -> handleTempBan(sender, target, args, reason, issuerId);
            case "unban" -> handleUnban(sender, target);
            case "mute" -> handleMute(sender, target, reason, issuerId, null);
            case "tempmute" -> handleTempMute(sender, target, args, reason, issuerId);
            case "unmute" -> handleUnmute(sender, target);
            case "kick" -> handleKick(sender, target, reason, issuerId);
            case "history" -> handleHistory(sender, target);
            case "ipban" -> handleIPBan(sender, target, reason, issuerId);
        }
        return true;
    }

    private void handleBan(CommandSender sender, OfflinePlayer target, String reason, UUID issuerId, Long duration, String formattedDuration) {

        if (plugin.getPunishmentManager().getActivePunishment(target.getUniqueId(), PunishmentType.BAN).isPresent()) {
            sender.sendMessage(config.getMessage("already-banned"));
            return;
        }

        PunishmentType type = (duration == null) ? PunishmentType.BAN : PunishmentType.TEMP_BAN;

        try {
            plugin.getPunishmentManager().punishPlayer(target.getUniqueId(), type, reason, issuerId, duration, null);

            // Send success message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("reason", reason);
            placeholders.put("duration", formattedDuration); // Use provided formatted duration

            String messageKey = (duration == null) ? "command.ban.success" : "command.tempban.success";
            sender.sendMessage(config.getMessage(messageKey, placeholders));

            // Kick if online
            if (target.isOnline() && target.getPlayer() != null) {
                Player player = target.getPlayer();
                Map<String, String> placeholders1 = new HashMap<>();
                placeholders1.put("player", target.getName());
                placeholders1.put("reason", reason);
                placeholders1.put("duration", duration == null ? "permanent" : formatDuration(duration)); // Static duration
                placeholders1.put("expires", duration == null ? "never" :
                        plugin.getPunishmentManager().formatExpiration(Instant.now().plusSeconds(duration))); // Countdown
                placeholders1.put("issuer", issuerId != null ?
                        plugin.getServer().getOfflinePlayer(issuerId).getName() : "Console");

                String banMessage = config.getMessage(
                        (duration == null) ? "ban-message" : "temp-ban-message",
                        placeholders1
                );
                player.kickPlayer(banMessage);
            }
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("command.error"));
        }
    }

    private void handleIPBan(CommandSender sender, OfflinePlayer target, String reason, UUID issuerId) {
        if (!target.isOnline()) {
            sender.sendMessage(config.getMessage("player-not-online"));
            return;
        }

        Player player = target.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();

        if (plugin.getPunishmentManager().isIPBanned(ip)) {
            sender.sendMessage(config.getMessage("ip-already-banned"));
            return;
        }

        plugin.getPunishmentManager().punishPlayer(target.getUniqueId(), PunishmentType.IP_BAN, reason, issuerId, null, ip);

        // Kick all players with the same IP
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            String playerIP = onlinePlayer.getAddress().getAddress().getHostAddress();
            if (playerIP.equals(ip)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reason", reason);
                placeholders.put("duration", "Permanent");
                placeholders.put("issuer", issuerId != null ?
                        plugin.getServer().getOfflinePlayer(issuerId).getName() : "Console");
                onlinePlayer.kickPlayer(config.getMessage("ipban-message", placeholders));
            }
        }

        // Send success message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("ip", ip);
        placeholders.put("reason", reason);
        placeholders.put("duration", "Permanent");
        sender.sendMessage(config.getMessage("command.ipban.success", placeholders));
    }

    private void handleTempBan(CommandSender sender, OfflinePlayer target, String[] args, String reason, UUID issuerId) {

        if (args.length < 2) {
            sender.sendMessage(config.getMessage("command.tempban.usage"));
            return;
        }

        long duration = parseDuration(args[1]);
        String formattedDuration = formatDuration(duration);

        if (duration <= 0) {
            sender.sendMessage(config.getMessage("invalid-duration"));
            return;
        }

        // Extract reason without the duration argument
        String actualReason = args.length > 2 ?
                String.join(" ", Arrays.copyOfRange(args, 2, args.length)) :
                config.getMessage("default-reason");

        // Double check if already banned
        Optional<Punishment> existingBan = plugin.getPunishmentManager()
                .getActivePunishment(target.getUniqueId(), PunishmentType.BAN);

        if (existingBan.isPresent()) {
            sender.sendMessage(config.getMessage("already-banned"));
            return;
        }

        handleBan(sender, target, actualReason, issuerId, duration, formattedDuration);
    }


    private void handleUnban(CommandSender sender, OfflinePlayer target) {
        // Debug current punishments
        plugin.getPunishmentManager().logAllPunishments(target.getUniqueId());

        // Check for regular ban with retry
        Optional<Punishment> regularBan = plugin.getPunishmentManager()
                .getActivePunishment(target.getUniqueId(), PunishmentType.BAN);

        boolean wasRegularBanned = regularBan.isPresent();

        // Check for IP ban
        boolean wasIPBanned = false;
        String bannedIP = null;
        List<Punishment> allPunishments = plugin.getPunishmentManager().getAllPunishments();

        for (Punishment punishment : allPunishments) {
            if (punishment.getType() == PunishmentType.IP_BAN
                    && punishment.getTargetId().equals(target.getUniqueId())
                    && punishment.isActive()) {
                wasIPBanned = true;
                bannedIP = punishment.getBannedIP();
                break;
            }
        }

        if (!wasRegularBanned && !wasIPBanned) {
            sender.sendMessage(config.getMessage("not-banned"));
            return;
        }

        // Remove the bans
        if (wasRegularBanned) {
            plugin.getPunishmentManager().removePunishment(target.getUniqueId(), PunishmentType.BAN);
        }

        if (wasIPBanned && bannedIP != null) {
            plugin.getPunishmentManager().removeIPBan(bannedIP);
        }

        // Send success message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());

        String messageKey;

        if (wasRegularBanned && wasIPBanned) {
            messageKey = "command.unban.success-both";
        } else if (wasIPBanned) {
            messageKey = "command.unban.success-ip";
        } else {
            messageKey = "command.unban.success";
        }

        String messageText = config.getMessage(messageKey, placeholders);
        sender.sendMessage(messageText);
    }


    private void handleMute(CommandSender sender, OfflinePlayer target, String reason, UUID issuerId, Long duration) {
        if (plugin.getPunishmentManager().getActivePunishment(target.getUniqueId(), PunishmentType.MUTE).isPresent()) {
            sender.sendMessage(config.getMessage("already-muted"));
            return;
        }

        plugin.getPunishmentManager().punishPlayer(target.getUniqueId(), PunishmentType.MUTE, reason, issuerId, duration, null);
        sendSuccessMessage(sender, "mute", target, reason, duration);
    }

    private void handleTempMute(CommandSender sender, OfflinePlayer target, String[] args, String reason, UUID issuerId) {
        if (args.length < 2) {
            sender.sendMessage(config.getMessage("command.tempmute.usage"));
            return;
        }

        long duration = parseDuration(args[1]);
        if (duration <= 0) {
            sender.sendMessage(config.getMessage("invalid-duration"));
            return;
        }

        String actualReason = args.length > 2 ?
                String.join(" ", Arrays.copyOfRange(args, 2, args.length)) :
                config.getMessage("default-reason");

        handleMute(sender, target, actualReason, issuerId, duration);
    }

    private void handleUnmute(CommandSender sender, OfflinePlayer target) {
        if (plugin.getPunishmentManager().getActivePunishment(target.getUniqueId(), PunishmentType.MUTE).isEmpty()) {
            sender.sendMessage(config.getMessage("not-muted"));
            return;
        }

        plugin.getPunishmentManager().removePunishment(target.getUniqueId(), PunishmentType.MUTE);
        sendSuccessMessage(sender, "unmute", target, null, null);
    }

    private void handleKick(CommandSender sender, OfflinePlayer target, String reason, UUID issuerId) {
        if (!target.isOnline()) {
            sender.sendMessage(config.getMessage("player-not-online"));
            return;
        }

        plugin.getPunishmentManager().punishPlayer(target.getUniqueId(), PunishmentType.KICK, reason, issuerId, null, null);
        sendSuccessMessage(sender, "kick", target, reason, null);
    }

    private void handleHistory(CommandSender sender, OfflinePlayer target) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessage("player-only-command"));
            return;
        }

        new HistoryGUI(plugin).openGUI((Player) sender, target);
    }

    private void sendSuccessMessage(CommandSender sender, String command, OfflinePlayer target, String reason, Long duration) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        if (reason != null) placeholders.put("reason", reason);
        if (duration != null) placeholders.put("duration", formatDuration(duration));
        sender.sendMessage(config.getMessage("command." + command + ".success", placeholders));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        if (seconds < 604800) return (seconds / 86400) + "d";
        return (seconds / 604800) + "w";
    }

    private long parseDuration(String input) {
        try {
            long amount = Long.parseLong(input.substring(0, input.length() - 1));
            char unit = input.charAt(input.length() - 1);
            return switch (Character.toLowerCase(unit)) {
                case 's' -> amount;
                case 'm' -> amount * 60;
                case 'h' -> amount * 3600;
                case 'd' -> amount * 86400;
                case 'w' -> amount * 604800;
                default -> -1;
            };
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean hasPermission(CommandSender sender, String command) {
        return sender.hasPermission("simplepunishments." + command);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("simplepunishments." + cmd.getName())) {
            return new ArrayList<>();
        }

        switch (cmd.getName().toLowerCase()) {
            case "ban", "ipban", "kick", "mute" -> {
                if (args.length == 1) {
                    return getOnlinePlayerNames(args[0]);
                } else if (args.length == 2) {
                    return Arrays.asList("[reason]");
                }
            }
            case "tempban", "tempmute" -> {
                if (args.length == 1) {
                    return getOnlinePlayerNames(args[0]);
                } else if (args.length == 2) {
                    String partial = args[1].toLowerCase();
                    List<String> durations = Arrays.asList(
                            "1m", "5m", "10m", "30m",
                            "1h", "6h", "12h",
                            "1d", "3d", "7d", "14d", "30d",
                            "1w", "2w", "1y"
                    );
                    return durations.stream()
                            .filter(dur -> dur.startsWith(partial))
                            .collect(Collectors.toList());
                } else if (args.length == 3) {
                    return Arrays.asList("[reason]");
                }
            }
            case "unban", "unmute" -> {
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    Set<String> bannedPlayers = new HashSet<>();
                    PunishmentType type = cmd.getName().equals("unban") ? PunishmentType.BAN : PunishmentType.MUTE;

                    // Get all active punishments of the specific type
                    plugin.getPunishmentManager().getAllPunishments().stream()
                            .filter(p -> p.getType() == type && p.isActive() && !p.isExpired())
                            .map(p -> plugin.getServer().getOfflinePlayer(p.getTargetId()).getName())
                            .filter(name -> name != null && name.toLowerCase().startsWith(partial))
                            .forEach(bannedPlayers::add);

                    return new ArrayList<>(bannedPlayers);
                }
            }
            case "history" -> {
                if (args.length == 1) {
                    return getOnlinePlayerNames(args[0]);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<String> getOnlinePlayerNames(String partial) {
        String partialLower = partial.toLowerCase();
        return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partialLower))
                .collect(Collectors.toList());
    }
}