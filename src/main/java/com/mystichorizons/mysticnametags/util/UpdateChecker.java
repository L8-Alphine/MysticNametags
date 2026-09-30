package com.mystichorizons.mysticnametags.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very small update checker for MysticNameTags.
 *
 * <p>Asks CFWidget's keyless JSON API for this project's newest release file on
 * CurseForge, as Mystic Essentials does, and reads the version from its
 * "mysticnametags-&lt;version&gt;.jar" name. It does not load curseforge.com
 * itself: the Overwolf platform terms forbid automated access to the site.
 * Sends nothing but this mod's version in the User-Agent. Switchable with
 * {@code updateCheckEnabled} in settings.json.</p>
 */
public final class UpdateChecker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** CFWidget's view of the MysticNameTags CurseForge project (id 1446990). */
    private static final String UPDATE_API_URL = "https://api.cfwidget.com/1446990";

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    // mysticnametags-1.0.2.jar -> capture "1.0.2"
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("mysticnametags-([0-9A-Za-z_.\\-]+)\\.jar");

    private final String currentVersion;
    private volatile String latestVersion;
    private volatile boolean checked;

    @Nonnull
    public String getCurrentVersion() {
        return currentVersion != null ? currentVersion : "";
    }

    public UpdateChecker(@Nonnull String currentVersion) {
        this.currentVersion = currentVersion;
    }

    /** Runs {@link #checkForUpdates()} on its own daemon thread, so startup never waits on the network. */
    public void checkForUpdatesAsync() {
        Thread thread = new Thread(this::checkForUpdates, "MysticNameTags-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * One check. Network errors are swallowed and will just log a debug message.
     */
    public void checkForUpdates() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(UPDATE_API_URL).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "MysticNameTags/" + getCurrentVersion() + " (update check)");

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.at(Level.FINE)
                        .log("[MysticNameTags] Update check HTTP " + code);
                return;
            }

            String body;
            try (InputStream in = conn.getInputStream()) {
                body = new String(in.readNBytes(MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
            }

            String newestFile = newestFileName(body);
            Matcher matcher = VERSION_PATTERN.matcher(newestFile == null ? "" : newestFile);
            if (matcher.find()) {
                String latest = matcher.group(1).trim();
                if (!latest.isEmpty()) {
                    this.latestVersion = latest;
                    this.checked = true;

                    LOGGER.at(Level.INFO)
                            .log("[MysticNameTags] Latest CurseForge version: " + this.latestVersion +
                                    " (current: " + currentVersion + ")");

                    if (isCurrentAheadOfLatest()) {
                        LOGGER.at(Level.INFO)
                                .log("[MysticNameTags] Current version appears to be ahead of the latest CurseForge release.");
                    } else if (isUpdateAvailable()) {
                        LOGGER.at(Level.INFO)
                                .log("[MysticNameTags] A newer version is available on CurseForge.");
                    }
                }
            } else {
                LOGGER.at(Level.FINE)
                        .log("[MysticNameTags] Could not find mysticnametags-*.jar in the CurseForge metadata.");
            }
        } catch (Exception ex) {
            LOGGER.at(Level.FINE).withCause(ex)
                    .log("[MysticNameTags] Failed to check for updates.");
        }
    }

    /**
     * CFWidget puts the newest release file (beta/alpha only when no release
     * exists) in {@code download}; its name carries the version.
     */
    @Nullable
    static String newestFileName(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement download = root.getAsJsonObject().get("download");
            if (download == null || !download.isJsonObject()) {
                return null;
            }
            JsonObject file = download.getAsJsonObject();
            for (String field : new String[] {"name", "display"}) {
                JsonElement value = file.get(field);
                if (value != null && value.isJsonPrimitive()) {
                    return value.getAsString();
                }
            }
            return null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** @return latest known version string, or null if not yet checked/failed. */
    @Nullable
    public String getLatestVersion() {
        return latestVersion;
    }

    /** @return true if a check has completed and we parsed some version info. */
    public boolean hasVersionInfo() {
        return checked && latestVersion != null;
    }

    public boolean isChecked() {
        return checked;
    }

    /** @return true if we know of a newer version than the one we're running. */
    public boolean isUpdateAvailable() {
        if (!hasVersionInfo() || currentVersion == null) {
            return false;
        }
        return compareVersions(normalize(currentVersion), normalize(latestVersion)) < 0;
    }

    /** @return true if the currently running version is ahead of the latest CurseForge release. */
    public boolean isCurrentAheadOfLatest() {
        if (!hasVersionInfo() || currentVersion == null) {
            return false;
        }
        return compareVersions(normalize(currentVersion), normalize(latestVersion)) > 0;
    }

    private static String normalize(String ver) {
        if (ver == null) return "";
        ver = ver.trim();
        if (ver.startsWith("v") || ver.startsWith("V")) {
            ver = ver.substring(1);
        }
        return ver;
    }

    private static int compareVersions(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.max(aParts.length, bParts.length);

        for (int i = 0; i < len; i++) {
            int ai = i < aParts.length ? safeParseInt(aParts[i]) : 0;
            int bi = i < bParts.length ? safeParseInt(bParts[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
