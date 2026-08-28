package com.mystichorizons.mysticnametags.license;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * This mod's licensing entry point.
 *
 * <p>Feature code asks {@link #bannersLicensed()} and nothing else. Everything here is safe to call
 * before {@link #init} — an uninitialised gate simply reports unlicensed.</p>
 *
 * <p><b>Failure policy.</b> A licensing problem turns off the banner feature and nothing more. Tags
 * keep working, they just render their text {@code display} instead of artwork. The mod never
 * refuses to load and never throws out of a feature check.</p>
 */
public final class MysticNameTagsLicense {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Product id as issued by the licensing portal. */
    public static final String PRODUCT_ID = LicenseGate.Products.NAMETAGS;

    /** Feature id gating tag banners. */
    public static final String FEATURE_BANNERS = "tags.banner";

    /** Routes the gate's log seam into the Hytale logger. */
    private static final LicenseGate.Log LOG = new LicenseGate.Log() {
        @Override
        public void info(String message) {
            LOGGER.at(Level.INFO).log("[MysticNameTags] " + message);
        }

        @Override
        public void warn(String message) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] " + message);
        }
    };

    private static volatile LicenseGate gate;

    private MysticNameTagsLicense() {
    }

    /**
     * Verifies the license once and logs a single summary line.
     *
     * <p>Never throws. If anything goes wrong the gate stays closed and the mod carries on.</p>
     *
     * @param modVersion this build's version, recorded in {@code license-request.json}
     * @param serverName cosmetic display name for the request file, may be null
     */
    public static void init(@Nonnull Path dataDir,
                            @Nullable String modVersion,
                            @Nullable String serverName) {
        try {
            LicenseGate built = LicenseGate.builder(PRODUCT_ID)
                    .dataDir(dataDir)
                    .modVersion(modVersion == null ? "unknown" : modVersion)
                    .serverName(serverName)
                    .trustSigningKey(EmbeddedKeys.SIGNING_KEY_ID, EmbeddedKeys.SIGNING_SPKI_BASE64)
                    .addContentKey(EmbeddedKeys.CONTENT_KEY_ID, EmbeddedKeys.CONTENT_KEY_B64URL)
                    .logger(LOG)
                    .build();

            built.start();
            gate = built;
        } catch (Throwable t) {
            gate = null;
            LOGGER.at(Level.WARNING).withCause(t).log(
                    "[MysticNameTags] Licensing could not start; banner tags will render as text.");
        }
    }

    /** Re-reads the license file. Wired to {@code /tags reload}. */
    public static void reload() {
        LicenseGate current = gate;
        if (current == null) {
            return;
        }
        try {
            current.reload();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Licensing reload failed; keeping the previous state.");
        }
    }

    /** Never null — falls back to a service that grants nothing. */
    @Nonnull
    public static MysticLicenseService service() {
        LicenseGate current = gate;
        return current == null ? NoopMysticLicenseService.INSTANCE : current;
    }

    /** One-line human summary, for an admin command. */
    @Nonnull
    public static String summaryLine() {
        LicenseGate current = gate;
        return current == null
                ? "[" + PRODUCT_ID + "] License: not initialised"
                : current.summaryLine();
    }

    /** This server's licensing identity, for the operator to register in the portal. */
    @Nullable
    public static String serverId() {
        LicenseGate current = gate;
        return current == null || current.serverUuid() == null ? null : current.serverUuid().toString();
    }

    /**
     * The one check banner code should call.
     *
     * <p>A cheap read of an immutable snapshot — safe from any thread, never throws, never logs.</p>
     */
    public static boolean bannersLicensed() {
        try {
            LicenseGate current = gate;
            return current != null && current.hasFeature(FEATURE_BANNERS);
        } catch (Throwable t) {
            return false;
        }
    }
}
