package com.mystichorizons.mysticnametags.license;

import com.hypixel.hytale.logger.HytaleLogger;
import com.mystic.licensing.LicenseGate;
import com.mystic.licensing.PublicKeyRing;
import com.mystichorizons.mysticnametags.generated.LicensingEndpoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * This mod's licensing entry point, on MysticLicenses v2.
 *
 * <p>Feature code asks {@link #bannersLicensed()} and nothing else. Everything here is safe to call
 * before {@link #init}; an uninitialised gate simply reports unlicensed.</p>
 *
 * <p>The operator puts their license key in {@code license.key} next to {@code settings.json}. The
 * server activates online, then runs on a signed authorization cached beside it and renewed in the
 * background, with an offline grace period while the licensing service is unreachable.</p>
 *
 * <p><b>Failure policy.</b> A licensing problem turns off the banner feature and nothing more. Tags
 * keep working, they just render their text {@code display} instead of artwork. The mod never
 * refuses to load and never throws out of a feature check.</p>
 */
public final class MysticNameTagsLicense {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Product slug in the MysticLicenses catalog. */
    public static final String PRODUCT_ID = "mysticnametags";

    /** Feature id gating tag banners: the entitlement {@code product.mysticnametags.tags.banner}. */
    public static final String FEATURE_BANNERS = "tags.banner";

    /** The retired licensing prototype's offline file, which this version no longer reads. */
    private static final String PROTOTYPE_LICENSE_FILE = "license.mclicense";

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

    /** Why there is no gate, for the status line. */
    private static volatile String unavailable = "not initialised";

    private MysticNameTagsLicense() {
    }

    /**
     * Reads {@code license.key} and activates. On a server licensed before this returns at once from
     * the cached authorization; on a first start it waits up to 5 seconds for the licensing service.
     *
     * <p>Never throws. If anything goes wrong the gate stays closed and the mod carries on.</p>
     *
     * @param modVersion       this build's version, shown in the licensing portal; may be null
     * @param onBannersChanged runs when banners become licensed or stop being licensed while the
     *                         server is running (a key added, a subscription lapsed, the service back)
     */
    public static void init(@Nonnull Path dataDir,
                            @Nullable String modVersion,
                            @Nonnull Runnable onBannersChanged) {
        noticePrototypeLicense(dataDir);

        String url = LicensingEndpoint.url();
        if (url == null) {
            unavailable = "this build has no licensing service configured";
            LOGGER.at(Level.WARNING).log(
                    "[MysticNameTags] This build has no licensing service configured; banner tags will render as text.");
            return;
        }

        try {
            Map<String, PublicKey> keys = new LinkedHashMap<>();
            LicensingEndpoint.publicKeys().forEach((kid, raw) -> keys.put(kid, PublicKeyRing.parseRaw(raw)));

            LicenseGate built = LicenseGate.builder(PRODUCT_ID)
                    .displayName("MysticNameTags")
                    .dataDir(dataDir)
                    .serverUrl(URI.create(url))
                    .publicKeys(new PublicKeyRing(keys))
                    .modVersion(modVersion == null || modVersion.equals("unknown") ? null : modVersion)
                    .log(LOG)
                    .build();

            built.start();
            built.onFeatureChange(FEATURE_BANNERS, enabled -> {
                LOGGER.at(Level.INFO).log(enabled
                        ? "[MysticNameTags] Tag banners are now licensed; registering banner art."
                        : "[MysticNameTags] Tag banners are no longer licensed; banner tags render as text.");
                try {
                    onBannersChanged.run();
                } catch (Throwable t) {
                    LOGGER.at(Level.WARNING).withCause(t)
                            .log("[MysticNameTags] Could not apply the banner license change.");
                }
            });
            gate = built;
        } catch (Throwable t) {
            gate = null;
            unavailable = "licensing could not start";
            LOGGER.at(Level.WARNING).withCause(t).log(
                    "[MysticNameTags] Licensing could not start; banner tags will render as text.");
        }
    }

    /**
     * Re-reads {@code license.key}. Wired to {@code /tags reload}: a key pasted in, replaced or
     * removed takes effect without a restart. Returns at once; the outcome is logged and applied
     * through the {@code onBannersChanged} hook given to {@link #init}.
     */
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

    /** Stops renewing. The activation stays, so the next start picks up where this one left off. */
    public static void shutdown() {
        LicenseGate current = gate;
        gate = null;
        unavailable = "shut down";
        if (current != null) {
            try {
                current.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** One-line human summary, for logs and admin output. Never contains the key. */
    @Nonnull
    public static String summaryLine() {
        LicenseGate current = gate;
        if (current == null) {
            return "MysticNameTags license: " + unavailable + ".";
        }
        try {
            return current.summaryLine();
        } catch (Throwable t) {
            return "MysticNameTags license: unknown.";
        }
    }

    /**
     * The one check banner code should call.
     *
     * <p>A cheap read of an immutable snapshot: safe from any thread, never throws, never logs.</p>
     */
    public static boolean bannersLicensed() {
        try {
            LicenseGate current = gate;
            return current != null && current.hasFeature(FEATURE_BANNERS);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void noticePrototypeLicense(@Nonnull Path dataDir) {
        try {
            if (Files.exists(dataDir.resolve(PROTOTYPE_LICENSE_FILE))) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] " + PROTOTYPE_LICENSE_FILE
                        + " is from the retired licensing prototype and is no longer read. Put your"
                        + " MysticLicenses key in license.key instead; " + PROTOTYPE_LICENSE_FILE
                        + ", server-id.txt and license-request.json can be deleted.");
            }
        } catch (Throwable ignored) {
        }
    }
}
