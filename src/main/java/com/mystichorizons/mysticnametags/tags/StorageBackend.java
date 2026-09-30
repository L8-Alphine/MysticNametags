package com.mystichorizons.mysticnametags.tags;

import com.mystichorizons.mysticnametags.util.JdbcDrivers;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum StorageBackend {
    FILE,
    SQLITE,
    MYSQL,
    /**
     * MySQL-compatible server reached with the MariaDB Connector/J driver.
     * Same tables and SQL as {@link #MYSQL}; only the driver and the JDBC URL
     * differ, which matters because MySQL Connector/J and MariaDB servers do
     * not always agree on the handshake.
     */
    MARIADB,
    /** Shared Redis datastore; intended for networks running several servers. */
    REDIS;

    public static StorageBackend fromString(String raw) {
        if (raw == null) return FILE;

        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");

        // "MARIA", "MARIA_DB", "maria-db" all mean the same thing to a user.
        if (normalized.startsWith("MARIA")) {
            return MARIADB;
        }

        try {
            return StorageBackend.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return FILE;
        }
    }

    /** True when this backend talks to a MySQL-compatible server over JDBC. */
    public boolean isMySqlCompatible() {
        return this == MYSQL || this == MARIADB;
    }

    /**
     * JDBC URL for a MySQL-compatible server. MARIADB uses the MariaDB
     * driver's scheme and option names; every other backend that reaches this
     * gets Connector/J's. Only meaningful for {@link #isMySqlCompatible()}.
     */
    @Nonnull
    public String mySqlJdbcUrl(@Nonnull String host, int port, @Nonnull String database) {
        return this == MARIADB
                ? JdbcDrivers.mariadbUrl(host, port, database)
                : JdbcDrivers.mysqlUrl(host, port, database);
    }
}
