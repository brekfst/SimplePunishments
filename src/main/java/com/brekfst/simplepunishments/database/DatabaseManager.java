package com.brekfst.simplepunishments.database;

import com.brekfst.simplepunishments.punishments.Punishment;
import com.brekfst.simplepunishments.punishments.PunishmentType;
import com.brekfst.simplepunishments.SimplePunishments;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import org.bson.Document;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class DatabaseManager {
    private Connection sqlConnection;
    private MongoClient mongoClient;
    private MongoDatabase mongoDb;
    private final SimplePunishments plugin;
    private final String dbType;

    public DatabaseManager(SimplePunishments plugin) {
        this.plugin = plugin;
        this.dbType = plugin.getConfig().getString("database.type", "SQLITE");
        setupDatabase();
    }

    private void setupDatabase() {
        try {
            switch (dbType.toUpperCase()) {
                case "MYSQL":
                    setupMySql();
                    break;
                case "MONGODB":
                    setupMongoDB();
                    break;
                default:
                    setupSQLite();
            }
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to setup database: " + e.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private void setupMySql() {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port");
        String database = plugin.getConfig().getString("database.mysql.database");
        String username = plugin.getConfig().getString("database.mysql.username");
        String password = plugin.getConfig().getString("database.mysql.password");
        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);

        try {
            sqlConnection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to MySQL", e);
        }
    }

    private void setupSQLite() {
        try {
            String path = plugin.getDataFolder() + "/database.db";
            sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + path);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite", e);
        }
    }

    private void setupMongoDB() {
        String uri = plugin.getConfig().getString("database.mongodb.uri");
        String database = plugin.getConfig().getString("database.mongodb.database");
        mongoClient = MongoClients.create(uri);
        mongoDb = mongoClient.getDatabase(database);
    }

    private void createTables() {
        if (dbType.equalsIgnoreCase("MONGODB")) return;

        try (Statement stmt = sqlConnection.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS punishments (
                id VARCHAR(36) PRIMARY KEY,
                target_id VARCHAR(36) NOT NULL,
                type VARCHAR(20) NOT NULL,
                reason TEXT,
                issuer_id VARCHAR(36),
                created_at TIMESTAMP NOT NULL,
                duration BIGINT,
                active BOOLEAN DEFAULT TRUE,
                ip VARCHAR(45)
            )
        """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create tables", e);
        }
    }

    public void savePunishment(Punishment punishment) {
        if (dbType.equalsIgnoreCase("MONGODB")) {
            saveMongoDocument(punishment);
        } else {
            saveSqlPunishment(punishment);
        }
    }

    private void saveSqlPunishment(Punishment punishment) {
        String sql = """
        INSERT INTO punishments (id, target_id, type, reason, issuer_id, created_at, duration, active, ip)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

        try (PreparedStatement pstmt = sqlConnection.prepareStatement(sql)) {
            pstmt.setString(1, punishment.getId().toString());
            pstmt.setString(2, punishment.getTargetId().toString());
            pstmt.setString(3, punishment.getType().toString());
            pstmt.setString(4, punishment.getReason());
            pstmt.setString(5, punishment.getIssuerId().toString());
            pstmt.setTimestamp(6, Timestamp.from(punishment.getCreatedAt()));
            pstmt.setLong(7, punishment.getDuration() != null ? punishment.getDuration() : -1);
            pstmt.setBoolean(8, true);
            pstmt.setString(9, punishment.getBannedIP());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save punishment: " + e.getMessage());
        }
    }

    private void saveMongoDocument(Punishment punishment) {
        Document doc = new Document()
                .append("_id", punishment.getId().toString())
                .append("targetId", punishment.getTargetId().toString())
                .append("type", punishment.getType().toString())
                .append("reason", punishment.getReason())
                .append("issuerId", punishment.getIssuerId().toString())
                .append("createdAt", Date.from(punishment.getCreatedAt()))
                .append("duration", punishment.getDuration())
                .append("active", true)
                .append("ip", punishment.getBannedIP());

        mongoDb.getCollection("punishments").insertOne(doc);
    }

    public List<Punishment> loadPunishments() {
        return dbType.equalsIgnoreCase("MONGODB") ? loadMongoPunishments() : loadSqlPunishments();
    }

    private List<Punishment> loadSqlPunishments() {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE active = TRUE";

        try (Statement stmt = sqlConnection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {

                punishments.add(new Punishment(
                        plugin,
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("target_id")),
                        PunishmentType.valueOf(rs.getString("type")),
                        rs.getString("reason"),
                        UUID.fromString(rs.getString("issuer_id")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("duration"),
                        rs.getString("ip"),
                        rs.getBoolean("active")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load SQL punishments: " + e.getMessage());
        }
        return punishments;
    }

    private List<Punishment> loadMongoPunishments() {
        List<Punishment> punishments = new ArrayList<>();
        MongoCollection<Document> collection = mongoDb.getCollection("punishments");

        for (Document doc : collection.find(new Document("active", true))) {
            try {

                punishments.add(new Punishment(
                        plugin,
                        UUID.fromString(doc.getString("_id")),
                        UUID.fromString(doc.getString("targetId")),
                        PunishmentType.valueOf(doc.getString("type")),
                        doc.getString("reason"),
                        UUID.fromString(doc.getString("issuerId")),
                        doc.getDate("createdAt").toInstant(),
                        doc.getLong("duration"),
                        doc.getString("ip"),
                        doc.getBoolean("active", true)
                ));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load MongoDB punishment: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return punishments;
    }

    public void closeConnection() {
        try {
            if (sqlConnection != null && !sqlConnection.isClosed()) {
                sqlConnection.close();
            }
            if (mongoClient != null) {
                mongoClient.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }

    public void updatePunishment(Punishment punishment) {
        if (plugin.getConfig().getString("database.type", "SQLITE").equalsIgnoreCase("MONGODB")) {
            updateMongoDocument(punishment);
        } else {
            updateSqlPunishment(punishment);
        }
    }

    public List<Punishment> loadPlayerPunishments(UUID targetId) {
        List<Punishment> punishments = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE target_id = ?";

        try (PreparedStatement pstmt = sqlConnection.prepareStatement(sql)) {
            pstmt.setString(1, targetId.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                boolean active = rs.getBoolean("active");

                Punishment punishment = new Punishment(
                        plugin,
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("target_id")),
                        PunishmentType.valueOf(rs.getString("type")),
                        rs.getString("reason"),
                        UUID.fromString(rs.getString("issuer_id")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("duration"),
                        rs.getString("ip"),
                        active  // Pass active status from database
                );
                punishments.add(punishment);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load punishments: " + e.getMessage());
        }

        return punishments;
    }

    public Punishment loadIPBan(String ip) {
        if (dbType.equalsIgnoreCase("MONGODB")) {
            return loadIPBanMongo(ip);
        }

        String sql = "SELECT * FROM punishments WHERE ip = ? AND active = TRUE";

        try (PreparedStatement pstmt = sqlConnection.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Punishment(
                        plugin,
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("target_id")),
                        PunishmentType.valueOf(rs.getString("type")),
                        rs.getString("reason"),
                        UUID.fromString(rs.getString("issuer_id")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("duration"),
                        rs.getString("ip"), true
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load IP ban: " + e.getMessage());
        }
        return null;
    }

    public Punishment loadIPBanMongo(String ip) {
        MongoCollection<Document> collection = mongoDb.getCollection("punishments");
        Document filter = new Document()
                .append("ip", ip)
                .append("active", true)
                .append("type", PunishmentType.IP_BAN.toString());

        Document doc = collection.find(filter).first();
        if (doc != null) {
            try {
                return new Punishment(
                        plugin,
                        UUID.fromString(doc.getString("_id")),
                        UUID.fromString(doc.getString("targetId")),
                        PunishmentType.valueOf(doc.getString("type")),
                        doc.getString("reason"),
                        UUID.fromString(doc.getString("issuerId")),
                        doc.getDate("createdAt").toInstant(),
                        doc.getLong("duration"),
                        doc.getString("ip"), true
                );
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load IP ban from MongoDB: " + e.getMessage());
            }
        }
        return null;
    }

    private void updateSqlPunishment(Punishment punishment) {
        String sql = """
            UPDATE punishments 
            SET active = ? 
            WHERE id = ?
        """;

        try (PreparedStatement pstmt = sqlConnection.prepareStatement(sql)) {
            pstmt.setBoolean(1, punishment.isActive());
            pstmt.setString(2, punishment.getId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update punishment: " + e.getMessage());
        }
    }

    private void updateMongoDocument(Punishment punishment) {
        MongoCollection<Document> collection = mongoDb.getCollection("punishments");

        Document filter = new Document("_id", punishment.getId().toString());
        Document update = new Document("$set", new Document()
                .append("active", punishment.isActive())
                .append("ip", punishment.getBannedIP()));

        try {
            collection.updateOne(filter, update);
        } catch (MongoException e) {
            plugin.getLogger().severe("Failed to update punishment in MongoDB: " + e.getMessage());
        }
    }
}