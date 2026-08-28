package com.mystichorizons.mysticnametags.license;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one class a mod actually touches.
 *
 * <p>Construct it during mod initialisation, call {@link #start()} once, then ask it questions. It
 * verifies the license file exactly once, caches the immutable result, and answers every subsequent
 * query from memory — so {@link #hasFeature} is cheap enough to call from game code.</p>
 *
 * <h2>Failure policy</h2>
 * Nothing here throws. Every argument problem, IO failure, corrupt file and verification failure
 * ends as a {@link LicenseStatus} plus one log line. A licensing problem switches off a licensed
 * feature; it never stops the mod loading and never takes the server down.
 *
 * <h2>Thread safety</h2>
 * {@link #start()} and {@link #reload()} publish an immutable snapshot through a volatile field.
 * Readers on game threads always see a complete, consistent state — never a half-initialised one.
 *
 * <p>Vendored from the {@code mystic-license} reference implementation, adapted to this mod's
 * top-level {@link LicensePayload} / {@link LicenseCheckResult} types.</p>
 */
public final class LicenseGate implements MysticLicenseService {

    /** Minimal logging seam so the core has no logging framework dependency. */
    public interface Log {
        void info(String message);

        void warn(String message);

        /** Routes to {@link System.Logger}, which is in the JDK. */
        static Log system(String name) {
            System.Logger logger = System.getLogger(name);
            return new Log() {
                @Override
                public void info(String message) {
                    logger.log(System.Logger.Level.INFO, message);
                }

                @Override
                public void warn(String message) {
                    logger.log(System.Logger.Level.WARNING, message);
                }
            };
        }
    }

    /** Immutable snapshot published atomically. */
    private record State(LicenseStatus status,
                         LicensePayload payload,
                         String detail,
                         UUID serverUuid) {

        static State of(LicenseStatus status, String detail, UUID serverUuid) {
            return new State(status, null, detail, serverUuid);
        }
    }

    private final String productId;
    private final Path dataDir;
    private final String modVersion;
    private final String serverName;
    private final Log log;
    private final McLicenseVerifier verifier;
    private final boolean writeRequestFile;

    private volatile State state = State.of(LicenseStatus.MISSING, "not started", null);

    private LicenseGate(Builder builder, McLicenseVerifier verifier) {
        this.productId = builder.productId;
        this.dataDir = builder.dataDir;
        this.modVersion = builder.modVersion;
        this.serverName = builder.serverName;
        this.log = builder.log;
        this.verifier = verifier;
        this.writeRequestFile = builder.writeRequestFile;
    }

    public static Builder builder(String productId) {
        return new Builder(productId);
    }

    /**
     * Resolve the server identity, verify the license, log one summary line.
     *
     * <p>Call once from mod init. Returns the resulting status so callers that want to branch on it
     * can, without reading a field.</p>
     */
    public LicenseStatus start() {
        LicenseStatus result = load();
        logSummary();
        return result;
    }

    /**
     * Re-read and re-verify. Wired to an admin command so an operator can drop in a renewed license
     * without restarting the server.
     */
    public LicenseStatus reload() {
        LicenseStatus result = load();
        log.info(summaryLine());
        return result;
    }

    private LicenseStatus load() {
        // --- 1. server identity ---------------------------------------------
        ServerIdentity.Result identity;
        try {
            identity = ServerIdentity.resolve(dataDir);
        } catch (RuntimeException e) {
            state = State.of(LicenseStatus.MISSING, "server identity unavailable: " + e.getMessage(), null);
            return state.status();
        }

        if (identity.detail() != null) {
            log.warn("[" + productId + "] " + identity.detail());
        }
        if (identity.outcome() == ServerIdentity.Outcome.CREATED) {
            log.info("[" + productId + "] Generated this server's licensing id: " + identity.uuid()
                    + " (stored in " + dataDir.resolve(ServerIdentity.IDENTITY_FILE) + ")");
        }

        UUID serverUuid = identity.uuid();
        if (serverUuid == null) {
            state = State.of(LicenseStatus.MISSING, identity.detail(), null);
            return state.status();
        }

        // --- 2. verify -------------------------------------------------------
        Path licenseFile = dataDir.resolve(LICENSE_FILE);
        LicenseCheckResult result;
        try {
            result = verifier.verifyFile(licenseFile,
                    serverUuid.toString().toLowerCase(Locale.ROOT), Instant.now());
        } catch (RuntimeException e) {
            // Defensive: verifyFile is written not to throw, but a licensing bug must never be
            // able to take the server down.
            state = State.of(LicenseStatus.INVALID_FORMAT, "verifier error: " + e.getMessage(), serverUuid);
            return state.status();
        }

        // --- 3. help the operator get a license ------------------------------
        if (writeRequestFile && result.status() == LicenseStatus.MISSING) {
            try {
                Path request = ServerIdentity.writeLicenseRequest(
                        dataDir, serverUuid, serverName, productId, modVersion);
                log.info("[" + productId + "] No license found. Upload " + request
                        + " to the licensing portal to register this server.");
            } catch (IOException e) {
                log.warn("[" + productId + "] Could not write a license request file: " + e.getMessage());
            }
        }

        state = new State(result.status(), result.payload(), result.detail(), serverUuid);
        return result.status();
    }

    /** License file name, relative to the mod data directory. */
    public static final String LICENSE_FILE = "license.mclicense";

    @Override
    public LicenseStatus status() {
        return state.status();
    }

    @Override
    public boolean isValid() {
        return state.status().grantsAccess();
    }

    @Override
    public boolean isProductLicensed(String product) {
        State current = state;
        return current.status().grantsAccess()
                && current.payload() != null
                && current.payload().coversProduct(product);
    }

    @Override
    public boolean hasFeature(String product, String featureId) {
        State current = state;
        return current.status().grantsAccess()
                && current.payload() != null
                && current.payload().coversProduct(product)
                && current.payload().coversFeature(product, featureId);
    }

    @Override
    public Optional<Instant> expiresAt() {
        State current = state;
        return current.payload() == null ? Optional.empty()
                : Optional.ofNullable(current.payload().expiresAt());
    }

    @Override
    public Optional<String> licenseId() {
        State current = state;
        return current.payload() == null ? Optional.empty()
                : Optional.ofNullable(current.payload().licenseId());
    }

    /** This mod's own product id, so callers need not repeat it. */
    public boolean hasFeature(String featureId) {
        return hasFeature(productId, featureId);
    }

    /**
     * Run {@code enable} only when the feature is licensed, and log the decision once. Keeps module
     * registration declarative instead of scattering {@code if} statements through the codebase.
     */
    public void whenLicensed(String featureId, Runnable enable) {
        if (hasFeature(featureId)) {
            enable.run();
        } else {
            log.info("[" + productId + "] Feature '" + featureId + "' is not licensed and stays disabled.");
        }
    }

    public UUID serverUuid() {
        return state.serverUuid();
    }

    /** Features of this product that the current license actually grants. */
    public List<String> licensedFeatures(List<String> candidates) {
        return candidates.stream().filter(this::hasFeature).toList();
    }

    /** One-line human summary, suitable for an admin command. */
    public String summaryLine() {
        State current = state;
        StringBuilder out = new StringBuilder(128);
        out.append('[').append(productId).append("] License: ").append(current.status());

        if (current.payload() != null) {
            out.append(" | id=").append(current.payload().licenseId());
            out.append(" | type=").append(current.payload().licenseType());
            out.append(" | expires=")
                    .append(current.payload().expiresAt() == null ? "never" : current.payload().expiresAt());
        }
        if (current.serverUuid() != null) {
            out.append(" | server=").append(current.serverUuid());
        }
        if (current.detail() != null && !current.detail().isBlank()) {
            out.append(" | ").append(current.detail());
        }
        return out.toString();
    }

    /**
     * Exactly one startup line, with an actionable follow-up when something is wrong. Deliberately
     * not logged per feature check.
     */
    private void logSummary() {
        State current = state;
        if (current.status().grantsAccess()) {
            log.info(summaryLine());
            if (current.status() == LicenseStatus.GRACE_PERIOD) {
                log.warn("[" + productId + "] This license has expired and is running on its grace "
                        + "period. Renew it in the portal before the grace period ends.");
            }
            if (!isProductLicensed(productId)) {
                log.warn("[" + productId + "] The license is valid but does not cover this product. "
                        + "Its features stay disabled.");
            }
            return;
        }

        log.warn(summaryLine());
        log.warn("[" + productId + "] " + advice(current.status()));
    }

    private String advice(LicenseStatus status) {
        return switch (status) {
            case MISSING -> "Place license.mclicense in " + dataDir + " and restart. Everything else keeps working.";
            case WRONG_SERVER -> "This license is bound to a different server UUID. This server's id is "
                    + state.serverUuid() + ". Use the portal's server-replacement flow to move it.";
            case EXPIRED -> "The license and its grace period have both ended. Renew it in the portal.";
            case NOT_YET_VALID -> "The license is not valid yet. Check this machine's system clock.";
            case INVALID_SIGNATURE -> "The license file failed signature verification. Re-download it; do not edit it by hand.";
            case DECRYPTION_FAILED, UNKNOWN_ENCRYPTION_KEY -> "This build cannot read that license. Update the mod to a version that carries the matching key.";
            case UNKNOWN_SIGNING_KEY -> "This license was signed by a key this build does not trust. Update the mod.";
            case UNSUPPORTED_VERSION -> "This license uses a newer format than this build understands. Update the mod.";
            case WRONG_PRODUCT -> "This license does not cover this product.";
            case INVALID_FORMAT -> "The license file is not readable. Re-download it from the portal.";
            case VALID, GRACE_PERIOD -> "";
        };
    }

    public static final class Builder {
        private final String productId;
        private final McLicenseVerifier.Builder keys = McLicenseVerifier.builder();
        private Path dataDir = Path.of(".");
        private String modVersion = "unknown";
        private String serverName;
        private Log log = Log.system("mystic-license");
        private boolean writeRequestFile = true;

        private Builder(String productId) {
            this.productId = productId;
        }

        /** The mod's data directory. license.mclicense and server-id.txt live here. */
        public Builder dataDir(Path value) {
            this.dataDir = value;
            return this;
        }

        public Builder modVersion(String value) {
            this.modVersion = value;
            return this;
        }

        /** Cosmetic, written into license-request.json to help the operator. */
        public Builder serverName(String value) {
            this.serverName = value;
            return this;
        }

        public Builder logger(Log value) {
            this.log = value;
            return this;
        }

        /** Trust a signing key id. Call more than once during a key rotation. */
        public Builder trustSigningKey(String keyId, String spkiBase64) {
            keys.trustSigningKey(keyId, spkiBase64);
            return this;
        }

        public Builder addContentKey(String keyId, String keyBase64Url) {
            keys.addContentKey(keyId, keyBase64Url);
            return this;
        }

        /** Disable writing license-request.json when no license is present. */
        public Builder writeRequestFile(boolean value) {
            this.writeRequestFile = value;
            return this;
        }

        public LicenseGate build() {
            return new LicenseGate(this, keys.build());
        }
    }

    /**
     * Feature id constants, mirroring config/entitlements.json in the portal. Using these instead of
     * string literals turns a typo into a compile error rather than a feature that silently never
     * unlocks.
     */
    public static final class Products {
        public static final String BOARDS = "mysticboards";
        public static final String HOLOS = "mysticholos";
        public static final String NAMETAGS = "mysticnametags";
        public static final String ESSENTIALS = "mysticessentials";
        public static final String GUILDS = "mysticguilds";

        public static final Map<String, List<String>> FEATURES = Map.of(
                BOARDS, List.of("scoreboards.multiple", "scoreboards.conditional"),
                HOLOS, List.of("holograms.text", "holograms.items", "holograms.images", "holograms.gif"),
                NAMETAGS, List.of("tags.banner"),
                ESSENTIALS, List.of("editor.kit", "editor.shop", "mail.send.money"),
                GUILDS, List.of("module.claims", "module.civil", "module.plots", "module.wars",
                        "module.npc", "module.npc.guards"));

        private Products() {
        }
    }
}
