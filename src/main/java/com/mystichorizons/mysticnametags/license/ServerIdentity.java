package com.mystichorizons.mysticnametags.license;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Establishes and persists the server UUID that licenses are bound to.
 *
 * <p>This closes the loop with the licensing portal: the operator has to give the portal a server
 * UUID, and that UUID has to be the same one the mod checks on every subsequent boot. So the mod
 * owns it.</p>
 *
 * <p>On first run a random UUID is generated and written to {@code server-id.txt} in the mod data
 * directory. From then on that file <em>is</em> the server's identity. It is deliberately plain
 * text: an operator can read it, copy it into the portal, and back it up.</p>
 *
 * <h2>Why generated rather than derived</h2>
 * Deriving an id from hardware, the file path or a hostname sounds tamper resistant but is not —
 * all of those are trivially spoofed — and it breaks legitimately: a host migration, a container
 * rebuild or a renamed folder would silently invalidate a paid license. A persisted random UUID is
 * honest about what it is (a stable label, not a secret).
 *
 * <h2>Corruption is never silently repaired</h2>
 * If {@code server-id.txt} exists but does not parse, this does <strong>not</strong> overwrite it.
 * Regenerating would orphan the operator's existing license against a UUID they can no longer
 * produce. It reports the problem instead and lets the licensed feature stay off.
 *
 * <p>Vendored from the {@code mystic-license} reference implementation — keep behaviour identical,
 * because the portal's server-replacement flow depends on it.</p>
 */
public final class ServerIdentity {

    /** File holding the persisted server UUID, relative to the mod data dir. */
    public static final String IDENTITY_FILE = "server-id.txt";

    /** File the operator uploads to the portal instead of typing the UUID. */
    public static final String REQUEST_FILE = "license-request.json";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 24;

    private ServerIdentity() {
    }

    /**
     * Read the persisted server UUID, if there is a readable, well-formed one.
     *
     * @return the UUID in canonical lowercase form, or empty when the file is absent, unreadable
     *         or malformed
     */
    public static Optional<UUID> load(Path dataDir) {
        Path file = dataDir.resolve(IDENTITY_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            // Tolerate a trailing comment line so operators can annotate the file.
            int newline = text.indexOf('\n');
            if (newline >= 0) {
                text = text.substring(0, newline).trim();
            }
            return Optional.of(UUID.fromString(text));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Return the persisted UUID, creating and storing one on first run.
     *
     * <p>Never overwrites an existing file. If a file is present but corrupt the result reports
     * {@link Outcome#CORRUPT} and carries no UUID, so the caller can log something actionable
     * rather than quietly re-binding the server to a new identity.</p>
     */
    public static Result resolve(Path dataDir) {
        Path file = dataDir.resolve(IDENTITY_FILE);

        if (Files.exists(file)) {
            Optional<UUID> existing = load(dataDir);
            return existing
                    .map(uuid -> new Result(uuid, Outcome.LOADED, null))
                    .orElseGet(() -> new Result(null, Outcome.CORRUPT,
                            file + " exists but does not contain a valid UUID. It has been left "
                                    + "untouched so an existing license is not orphaned - fix or "
                                    + "delete it, then re-register the server in the portal."));
        }

        UUID created = UUID.randomUUID();
        try {
            Files.createDirectories(dataDir);
            writeAtomically(file, created + System.lineSeparator());
            return new Result(created, Outcome.CREATED, null);
        } catch (IOException e) {
            // A read-only data directory must not stop the server booting.
            return new Result(created, Outcome.EPHEMERAL,
                    "Could not persist " + file + " (" + e.getMessage() + "). Using a temporary "
                            + "identity that will change on restart; licensing will not stick "
                            + "until the directory is writable.");
        }
    }

    /**
     * Write a {@code license-request.json} the operator can upload to the portal instead of typing
     * the UUID by hand.
     *
     * <p>Shape and field names must match the portal's importer; it rejects unknown fields.</p>
     *
     * @param serverName optional friendly name, may be null
     */
    public static Path writeLicenseRequest(Path dataDir,
                                           UUID serverUuid,
                                           String serverName,
                                           String productId,
                                           String modVersion) throws IOException {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);

        StringBuilder json = new StringBuilder(256);
        json.append("{\n");
        json.append("  \"format_version\": 1,\n");
        appendField(json, "server_uuid", serverUuid.toString().toLowerCase(Locale.ROOT), true);
        if (serverName != null && !serverName.isBlank()) {
            appendField(json, "server_name", trimTo(serverName, 64), true);
        }
        appendField(json, "product_id", productId, true);
        if (modVersion != null && !modVersion.isBlank()) {
            appendField(json, "mod_version", trimTo(modVersion, 32), true);
        }
        appendField(json, "request_nonce",
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce), true);
        appendField(json, "created_at", Instant.now().toString(), false);
        json.append("}\n");

        Path file = dataDir.resolve(REQUEST_FILE);
        Files.createDirectories(dataDir);
        writeAtomically(file, json.toString());
        return file;
    }

    /** What {@link #resolve} did. */
    public enum Outcome {
        /** An existing identity was read. The normal case. */
        LOADED,
        /** First run: a new identity was generated and persisted. */
        CREATED,
        /** The identity file is unreadable or malformed and was left alone. */
        CORRUPT,
        /** An identity was generated but could not be saved. */
        EPHEMERAL
    }

    /** Result of {@link #resolve}. {@code uuid} is null only for {@link Outcome#CORRUPT}. */
    public record Result(UUID uuid, Outcome outcome, String detail) {

        public Optional<UUID> optional() {
            return Optional.ofNullable(uuid);
        }

        /** Canonical lowercase form, which is what the portal binds licenses to. */
        public Optional<String> canonical() {
            return optional().map(value -> value.toString().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Write via a temporary file plus a move, so a crash mid-write cannot leave a half-written
     * identity file behind.
     */
    private static void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void appendField(StringBuilder out, String key, String value, boolean comma) {
        out.append("  \"").append(key).append("\": ");
        escape(out, value);
        out.append(comma ? ",\n" : "\n");
    }

    private static String trimTo(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static void escape(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
