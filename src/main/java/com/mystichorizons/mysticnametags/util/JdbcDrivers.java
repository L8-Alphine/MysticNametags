package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Resolves the JDBC drivers shaded into the plugin jar.
 *
 * <p>{@link java.sql.DriverManager} discovers drivers with a one-off
 * {@code ServiceLoader} scan that runs when it is first touched, using the
 * class loader current at that moment. On a Hytale server that is the
 * launcher's loader, long before the plugin loader that owns the shaded
 * MySQL/MariaDB/SQLite classes exists, so DriverManager never sees them and
 * every connection attempt died with
 * {@code No suitable driver found for jdbc:mysql://...}.
 *
 * <p>This class instantiates the bundled drivers from the plugin's own class
 * loader and connects through the {@link Driver} instance directly, so a
 * connection never depends on DriverManager's registry or on which loader is
 * asking. Loading each class also lets it self-register with DriverManager,
 * which keeps third-party code that still goes through DriverManager working
 * once the plugin has connected at least once.
 */
public final class JdbcDrivers {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    public static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    /** Probed in this order; the first one that accepts a URL wins. */
    private static final String[] BUNDLED = {MYSQL_DRIVER, MARIADB_DRIVER, SQLITE_DRIVER};

    private static volatile Map<String, Driver> drivers;

    private JdbcDrivers() {
    }

    /** JDBC URL for MySQL Connector/J. */
    @Nonnull
    public static String mysqlUrl(@Nonnull String host, int port, @Nonnull String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8";
    }

    /**
     * JDBC URL for MariaDB Connector/J. Its option names differ from
     * Connector/J's: TLS is {@code sslMode}, and the connection is always
     * UTF-8 so there is no {@code characterEncoding} to set.
     */
    @Nonnull
    public static String mariadbUrl(@Nonnull String host, int port, @Nonnull String database) {
        return "jdbc:mariadb://" + host + ":" + port + "/" + database
                + "?sslMode=disable&autoReconnect=true";
    }

    /**
     * Opens a connection with the bundled driver that accepts the URL.
     *
     * @param user     blank to connect without credentials (SQLite)
     * @param password blank to send no password
     */
    @Nonnull
    public static Connection open(@Nonnull String jdbcUrl,
                                  @Nonnull String user,
                                  @Nonnull String password) throws SQLException {
        Driver driver = resolve(jdbcUrl);
        if (driver == null) {
            throw new SQLException("No JDBC driver bundled with MysticNameTags accepts "
                    + scheme(jdbcUrl) + " (available: " + describeAvailable() + ")");
        }

        Properties props = new Properties();
        if (!user.isEmpty()) {
            props.setProperty("user", user);
        }
        if (!password.isEmpty()) {
            props.setProperty("password", password);
        }

        Connection connection = driver.connect(jdbcUrl, props);
        if (connection == null) {
            // acceptsURL said yes and connect() still declined it.
            throw new SQLException("JDBC driver " + label(driver) + " refused " + scheme(jdbcUrl));
        }
        return connection;
    }

    /** True when a bundled driver can handle this URL. */
    public static boolean isAvailable(@Nonnull String jdbcUrl) {
        return resolve(jdbcUrl) != null;
    }

    /** Driver that would serve this URL, e.g. {@code MariaDB Connector/J 3.5}. */
    @Nonnull
    public static String describeDriver(@Nonnull String jdbcUrl) {
        Driver driver = resolve(jdbcUrl);
        return driver == null ? "none" : label(driver);
    }

    /** Every driver that loaded, for diagnostics. */
    @Nonnull
    public static String describeAvailable() {
        Map<String, Driver> available = loaded();
        if (available.isEmpty()) {
            return "none";
        }
        List<String> labels = new ArrayList<>(available.size());
        for (Driver driver : available.values()) {
            labels.add(label(driver));
        }
        return String.join(", ", labels);
    }

    @Nullable
    private static Driver resolve(@Nonnull String jdbcUrl) {
        Map<String, Driver> available = loaded();

        for (String preferred : preferenceFor(jdbcUrl)) {
            Driver driver = available.get(preferred);
            if (driver != null && accepts(driver, jdbcUrl)) {
                return driver;
            }
        }

        for (Driver driver : available.values()) {
            if (accepts(driver, jdbcUrl)) {
                return driver;
            }
        }
        return null;
    }

    /**
     * MariaDB's driver also accepts {@code jdbc:mysql://}, so a MYSQL backend
     * still connects when only the MariaDB driver is on the class path.
     */
    @Nonnull
    private static String[] preferenceFor(@Nonnull String jdbcUrl) {
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mariadb:")) {
            return new String[]{MARIADB_DRIVER};
        }
        if (lower.startsWith("jdbc:mysql:")) {
            return new String[]{MYSQL_DRIVER, MARIADB_DRIVER};
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return new String[]{SQLITE_DRIVER};
        }
        return new String[0];
    }

    private static boolean accepts(@Nonnull Driver driver, @Nonnull String jdbcUrl) {
        try {
            return driver.acceptsURL(jdbcUrl);
        } catch (SQLException e) {
            return false;
        }
    }

    @Nonnull
    private static Map<String, Driver> loaded() {
        Map<String, Driver> local = drivers;
        if (local != null) {
            return local;
        }

        synchronized (JdbcDrivers.class) {
            if (drivers != null) {
                return drivers;
            }

            Map<String, Driver> found = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();

            for (String className : BUNDLED) {
                try {
                    // initialize = true so the driver also registers itself
                    // with DriverManager on the way past.
                    Class<?> type = Class.forName(className, true, JdbcDrivers.class.getClassLoader());
                    found.put(className, (Driver) type.getDeclaredConstructor().newInstance());
                } catch (Throwable t) {
                    missing.add(className);
                }
            }

            drivers = found;

            if (found.isEmpty()) {
                LOGGER.at(Level.SEVERE).log("[MysticNameTags] No JDBC drivers could be loaded; "
                        + "SQL storage backends will not work. Missing: " + String.join(", ", missing));
            } else {
                LOGGER.at(Level.INFO).log("[MysticNameTags] JDBC drivers available: " + describeAvailable());
                if (!missing.isEmpty()) {
                    LOGGER.at(Level.FINE).log("[MysticNameTags] JDBC drivers absent from this build: "
                            + String.join(", ", missing));
                }
            }
            return found;
        }
    }

    @Nonnull
    private static String label(@Nonnull Driver driver) {
        String name = switch (driver.getClass().getName()) {
            case MYSQL_DRIVER -> "MySQL Connector/J";
            case MARIADB_DRIVER -> "MariaDB Connector/J";
            case SQLITE_DRIVER -> "SQLite JDBC";
            default -> driver.getClass().getName();
        };
        return name + " " + driver.getMajorVersion() + "." + driver.getMinorVersion();
    }

    /** Scheme only; the rest of a JDBC URL can carry credentials. */
    @Nonnull
    private static String scheme(@Nonnull String jdbcUrl) {
        int marker = jdbcUrl.indexOf("://");
        if (marker > 0) {
            return jdbcUrl.substring(0, marker + 3);
        }
        int first = jdbcUrl.indexOf(':');
        int second = first < 0 ? -1 : jdbcUrl.indexOf(':', first + 1);
        return second > 0 ? jdbcUrl.substring(0, second + 1) : jdbcUrl;
    }
}
