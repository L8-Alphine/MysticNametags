package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Simple JDBC-based storage for player tags.
 *
 * Uses a single table:
 *   mystic_tags_players(
 *       uuid      VARCHAR(36) PRIMARY KEY,
 *       data_json TEXT NOT NULL
 *   )
 *
 * The data_json column is just the standard PlayerTagData JSON blob.
 */
public final class SqlPlayerTagStore implements PlayerTagStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final Gson gson;

    public SqlPlayerTagStore(@Nonnull String jdbcUrl,
                             @Nonnull String user,
                             @Nonnull String password,
                             @Nonnull Gson gson) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.gson = gson;

        initSchema();
    }

    private Connection getConnection() throws SQLException {
        if (user.isEmpty() && password.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    private void initSchema() {
        boolean isSqlite = jdbcUrl.startsWith("jdbc:sqlite:");

        String sql;
        if (isSqlite) {
            // SQLite: type names are mostly affinity-based, TEXT is totally fine
            sql = "CREATE TABLE IF NOT EXISTS mystic_tags_players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "data_json TEXT NOT NULL" +
                    ")";
        } else {
            // MySQL / MariaDB
            sql = "CREATE TABLE IF NOT EXISTS mystic_tags_players (" +
                    "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "data_json LONGTEXT NOT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).withCause(e)
                    .log("[MysticNameTags] Failed to initialize SQL schema for player tags");
        }
    }

    @Nonnull
    @Override
    public PlayerTagData load(@Nonnull UUID uuid) {
        String sql = "SELECT data_json FROM mystic_tags_players WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerTagData();
                }
                String json = rs.getString(1);
                PlayerTagData data = gson.fromJson(json, PlayerTagData.class);
                return data != null ? data : new PlayerTagData();
            }
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load SQL tag data for " + uuid);
            return new PlayerTagData();
        }
    }

    @Override
    public void save(@Nonnull UUID uuid, @Nonnull PlayerTagData data) {
        String sql = "INSERT INTO mystic_tags_players(uuid, data_json) " +
                "VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE data_json = VALUES(data_json)";

        // For SQLite, we need a different upsert syntax, so we branch by driver.
        boolean isSqlite = jdbcUrl.startsWith("jdbc:sqlite:");

        if (isSqlite) {
            sql = "INSERT INTO mystic_tags_players(uuid, data_json) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET data_json = excluded.data_json";
        }

        String json = gson.toJson(data);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save SQL tag data for " + uuid);
        }
    }

    @Override
    public void delete(@Nonnull UUID uuid) {
        String sql = "DELETE FROM mystic_tags_players WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to delete SQL tag data for " + uuid);
        }
    }

    @Override
    public void migrateFromFolder(@Nonnull File playerDataFolder,
                                  @Nonnull Gson gson) {
        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            return;
        }

        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Migrating " + files.length + " playerdata JSON files into SQL backend...");

        int migrated = 0;
        for (File file : files) {
            try {
                String filename = file.getName();
                String uuidPart = filename.substring(0, filename.length() - ".json".length());
                UUID uuid = UUID.fromString(uuidPart);

                try (java.io.InputStreamReader reader =
                             new java.io.InputStreamReader(
                                     new java.io.FileInputStream(file),
                                     java.nio.charset.StandardCharsets.UTF_8)) {

                    PlayerTagData data = gson.fromJson(reader, PlayerTagData.class);
                    if (data == null) data = new PlayerTagData();
                    save(uuid, data);
                    migrated++;
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to migrate " + file.getName() + " to SQL backend.");
            }
        }

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Migration complete. Migrated " + migrated + " players.");

        // Optional: rename original folder so we don't re-migrate next boot
        File renamed = new File(playerDataFolder.getParentFile(), "playerdata_legacy");
        if (!playerDataFolder.renameTo(renamed)) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Could not rename playerdata folder after migration; it may attempt again on next boot.");
        }
    }
}
