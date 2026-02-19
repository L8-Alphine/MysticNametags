package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very small update checker for MysticNameTags.
 *
 * This implementation scrapes the CurseForge files listing for:
 *   https://www.curseforge.com/hytale/mods/mysticnametags
 *
 * It looks for the first "mysticnametags-<version>.jar" occurrence and
 * treats that as the latest public release.
 */
public final class UpdateChecker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // CurseForge files page – no API key required, just HTML.
    private static final String FILES_URL =
            "https://www.curseforge.com/hytale/mods/mysticnametags/files/all";

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

    /**
     * Call this once during plugin startup.
     * Network errors are swallowed and will just log a debug message.
     */
    public void checkForUpdates() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(FILES_URL).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "text/html");
            conn.setRequestProperty("User-Agent", "MysticNameTags-UpdateChecker");

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.at(Level.FINE)
                        .log("[MysticNameTags] Update check HTTP " + code);
                return;
            }

            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    html.append(line).append('\n');
                }
            }

            Matcher matcher = VERSION_PATTERN.matcher(html);
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
                        .log("[MysticNameTags] Could not find mysticnametags-*.jar on CurseForge files page.");
            }
        } catch (Exception ex) {
            LOGGER.at(Level.FINE).withCause(ex)
                    .log("[MysticNameTags] Failed to check for updates.");
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
