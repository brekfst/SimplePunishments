package com.brekfst.simplepunishments.gui;

import com.brekfst.simplepunishments.punishments.Punishment;
import com.brekfst.simplepunishments.SimplePunishments;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistoryGUI {
    private final SimplePunishments plugin;
    private final FileConfiguration config;
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final int ITEMS_PER_PAGE = 45;

    public HistoryGUI(SimplePunishments plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void openGUI(Player viewer, OfflinePlayer target) {
        List<Punishment> punishments = plugin.getPunishmentManager().getPlayerPunishments(target.getUniqueId());
        int page = playerPages.getOrDefault(viewer.getUniqueId(), 0);
        int maxPages = (int) Math.ceil(punishments.size() / (double) ITEMS_PER_PAGE);

        String title = config.getString("gui.title", "Punishment History")
                .replace("%player%", target.getName())
                .replace("&", "§");

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Add punishment items
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, punishments.size());

        for (int i = startIndex; i < endIndex; i++) {
            Punishment punishment = punishments.get(i);
            ItemStack item = createPunishmentItem(punishment);
            gui.setItem(i - startIndex, item);
        }

        // Add navigation items
        if (page > 0) {
            gui.setItem(45, createNavigationItem("previous-page"));
        }
        if (page < maxPages - 1) {
            gui.setItem(53, createNavigationItem("next-page"));
        }

        // Add info items
        gui.setItem(49, createInfoItem(target, punishments.size()));

        viewer.openInventory(gui);
    }

    private ItemStack createPunishmentItem(Punishment punishment) {
        String materialName = config.getString("gui.items." + punishment.getType().toString().toLowerCase() + ".material", "PAPER");
        Material material = Material.valueOf(materialName);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String nameFormat = config.getString("gui.items." + punishment.getType().toString().toLowerCase() + ".name", "&c%type% &7- %date%");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat
                .replace("%type%", punishment.getType().toString())
                .replace("%date%", formatDate(punishment.getCreatedAt()))
        ));

        List<String> loreFormat = config.getStringList("gui.items." + punishment.getType().toString().toLowerCase() + ".lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreFormat) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%reason%", punishment.getReason())
                    .replace("%issuer%", getIssuerName(punishment))
                    .replace("%duration%", punishment.getFormattedDuration())
                    .replace("%status%", punishment.isActive() ? "Active" : "Inactive")
                    .replace("%expires%", getExpirationText(punishment))
            ));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationItem(String type) {
        String materialName = config.getString("gui.navigation." + type + ".material", "ARROW");
        Material material = Material.valueOf(materialName);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = config.getString("gui.navigation." + type + ".name", "&7" + type);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(OfflinePlayer target, int totalPunishments) {
        String materialName = config.getString("gui.info.material", "PLAYER_HEAD");
        ItemStack item = new ItemStack(Material.valueOf(materialName));
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof SkullMeta && materialName.equals("PLAYER_HEAD")) {
            ((SkullMeta) meta).setOwningPlayer(target);
        }

        String nameFormat = config.getString("gui.info.name", "&e%player%'s History");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat
                .replace("%player%", target.getName())
        ));

        List<String> loreFormat = config.getStringList("gui.info.lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreFormat) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("%total%", String.valueOf(totalPunishments))
            ));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatDate(Instant date) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(date);
    }

    private String getIssuerName(Punishment punishment) {
        return punishment.getIssuerId() != null ?
                plugin.getServer().getOfflinePlayer(punishment.getIssuerId()).getName() :
                "Console";
    }

    private String getExpirationText(Punishment punishment) {
        if (punishment.isPermanent()) return "Never";
        if (!punishment.isActive()) return "Expired";
        return plugin.getPunishmentManager().formatExpiration(punishment.getExpirationTime());
    }
}