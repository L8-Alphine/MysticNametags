package com.mystichorizons.mysticnametags.license;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline verifier for {@code license.mclicense} (MCL1 envelope).
 *
 * <p>Vendored from the {@code mystic-license} reference implementation. The wire format is fixed by
 * the licensing portal — do not change a byte of it here without changing the issuer.</p>
 *
 * <p><b>Never contacts the network.</b> Everything needed to validate a license ships in the jar.</p>
 *
 * <p><b>Security boundary.</b> The Ed25519 signature is the boundary. The AES content key ships
 * inside the mod and must be assumed extractable; it only makes license contents opaque to casual
 * inspection. Extracting it does not allow forging a license, because forgery needs the private
 * key, which only the licensing server holds. Java bytecode can also be patched — this is a
 * licensing control for honest operators, not DRM.</p>
 */
public final class McLicenseVerifier {

    /** Must match the portal's envelope signing context. */
    private static final String ENVELOPE_SIGNING_CONTEXT = "mystic-license-envelope-v1";

    private static final String MAGIC = "MCL1";
    private static final int ENVELOPE_VERSION = 1;
    private static final int PAYLOAD_FORMAT_VERSION = 1;
    private static final String PAYLOAD_FORMAT = "mystic-license";
    private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    private static final int IV_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int TAG_BITS = TAG_BYTES * 8;
    private static final int SIGNED_FIELD_COUNT = 9;

    /** Refuse absurdly large files before allocating anything. */
    private static final long MAX_FILE_BYTES = 256 * 1024;

    private final Map<String, PublicKey> trustedSigningKeys;
    private final Map<String, byte[]> contentKeys;

    private McLicenseVerifier(Map<String, PublicKey> signingKeys, Map<String, byte[]> contentKeys) {
        this.trustedSigningKeys = Map.copyOf(signingKeys);
        this.contentKeys = new LinkedHashMap<>(contentKeys);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Collects the keys this build embeds. */
    public static final class Builder {
        private final Map<String, PublicKey> signingKeys = new LinkedHashMap<>();
        private final Map<String, byte[]> contentKeys = new LinkedHashMap<>();

        /**
         * Trust a signing key. Several may be trusted at once so a key rotation does not
         * invalidate licenses issued by the previous key.
         *
         * @param spkiBase64 standard-alphabet base64 of the SPKI DER
         */
        public Builder trustSigningKey(String keyId, String spkiBase64) {
            try {
                byte[] der = Base64.getDecoder().decode(spkiBase64);
                PublicKey key = KeyFactory.getInstance("Ed25519")
                        .generatePublic(new X509EncodedKeySpec(der));
                signingKeys.put(keyId, key);
                return this;
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid Ed25519 public key for " + keyId, e);
            }
        }

        /** @param keyBase64Url unpadded base64url of the 32 key bytes */
        public Builder addContentKey(String keyId, String keyBase64Url) {
            byte[] key = Base64.getUrlDecoder().decode(keyBase64Url);
            if (key.length != 32) {
                throw new IllegalArgumentException("Content key " + keyId + " must be 32 bytes");
            }
            contentKeys.put(keyId, key);
            return this;
        }

        public McLicenseVerifier build() {
            if (signingKeys.isEmpty()) {
                throw new IllegalStateException("At least one trusted signing key is required");
            }
            return new McLicenseVerifier(signingKeys, contentKeys);
        }
    }

    /** Read and verify a license file. A missing file is not an error. */
    @Nonnull
    public LicenseCheckResult verifyFile(@Nullable Path file,
                                         @Nullable String serverUuid,
                                         @Nullable Instant now) {
        if (file == null || !Files.isRegularFile(file)) {
            return LicenseCheckResult.failure(LicenseStatus.MISSING, "no license file at " + file);
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT,
                        "license file is implausibly large");
            }
            return verify(Files.readAllBytes(file), serverUuid, now);
        } catch (IOException e) {
            return LicenseCheckResult.failure(LicenseStatus.MISSING,
                    "cannot read license file: " + e.getMessage());
        }
    }

    /**
     * Verify raw license bytes.
     *
     * @param serverUuid this server's identity, or null to skip the binding check
     */
    @Nonnull
    public LicenseCheckResult verify(@Nullable byte[] fileBytes,
                                     @Nullable String serverUuid,
                                     @Nullable Instant now) {
        if (fileBytes == null || fileBytes.length == 0) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "license file is empty");
        }

        // 1 + 2. Parse and check the envelope.
        JsonObject envelope;
        try {
            JsonElement parsed = JsonParser.parseString(new String(fileBytes, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "envelope is not an object");
            }
            envelope = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "envelope is not valid JSON");
        }

        if (!MAGIC.equals(str(envelope, "magic"))) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "bad magic");
        }

        Long version = integer(envelope, "version");
        if (version == null || version != ENVELOPE_VERSION) {
            return LicenseCheckResult.failure(LicenseStatus.UNSUPPORTED_VERSION, "envelope version " + version);
        }

        JsonObject algorithm = object(envelope, "algorithm");
        if (algorithm == null
                || !ENCRYPTION_ALGORITHM.equals(str(algorithm, "encryption"))
                || !SIGNATURE_ALGORITHM.equals(str(algorithm, "signature"))) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "unsupported algorithms");
        }

        String signingKeyId = str(envelope, "signing_key_id");
        String encryptionKeyId = str(envelope, "encryption_key_id");
        String ivB64 = str(envelope, "iv");
        String ciphertextB64 = str(envelope, "ciphertext");
        String tagB64 = str(envelope, "authentication_tag");
        String signatureB64 = str(envelope, "signature");

        if (signingKeyId == null || encryptionKeyId == null || ivB64 == null
                || ciphertextB64 == null || tagB64 == null || signatureB64 == null) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT,
                    "envelope is missing required fields");
        }

        // 3. Resolve the signing key.
        PublicKey publicKey = trustedSigningKeys.get(signingKeyId);
        if (publicKey == null) {
            return LicenseCheckResult.failure(LicenseStatus.UNKNOWN_SIGNING_KEY, signingKeyId);
        }

        // 4. Verify the signature BEFORE touching the ciphertext. Unauthenticated bytes must
        //    never reach the JSON parser, and the content key is not even looked up until this
        //    passes.
        byte[] signingInput = buildSigningInput(
                MAGIC, version, signingKeyId, encryptionKeyId,
                ENCRYPTION_ALGORITHM, SIGNATURE_ALGORITHM, ivB64, ciphertextB64, tagB64);

        try {
            byte[] signature = Base64.getUrlDecoder().decode(signatureB64);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            if (!verifier.verify(signature)) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_SIGNATURE, "signature does not match");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_SIGNATURE, e.getMessage());
        }

        // 5. Only now resolve the content key.
        byte[] contentKey = contentKeys.get(encryptionKeyId);
        if (contentKey == null) {
            return LicenseCheckResult.failure(LicenseStatus.UNKNOWN_ENCRYPTION_KEY, encryptionKeyId);
        }

        // 6. Authenticated decryption. Java's Cipher wants ciphertext || tag concatenated.
        byte[] plaintext;
        try {
            byte[] iv = Base64.getUrlDecoder().decode(ivB64);
            byte[] ciphertext = Base64.getUrlDecoder().decode(ciphertextB64);
            byte[] tag = Base64.getUrlDecoder().decode(tagB64);
            if (iv.length != IV_BYTES || tag.length != TAG_BYTES) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "bad iv or tag length");
            }

            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            plaintext = cipher.doFinal(combined);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return LicenseCheckResult.failure(LicenseStatus.DECRYPTION_FAILED, e.getMessage());
        }

        // 7. Parse the payload and check its version.
        LicensePayload payload;
        try {
            JsonElement parsed = JsonParser.parseString(new String(plaintext, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "payload is not an object");
            }
            JsonObject json = parsed.getAsJsonObject();
            if (!PAYLOAD_FORMAT.equals(str(json, "format"))) {
                return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "payload is not a mystic-license");
            }
            Long formatVersion = integer(json, "format_version");
            if (formatVersion == null || formatVersion != PAYLOAD_FORMAT_VERSION) {
                return LicenseCheckResult.failure(LicenseStatus.UNSUPPORTED_VERSION,
                        "payload version " + formatVersion);
            }
            payload = readPayload(json);
        } catch (RuntimeException e) {
            return LicenseCheckResult.failure(LicenseStatus.INVALID_FORMAT, "payload could not be read");
        }

        Instant checkTime = now == null ? Instant.now() : now;

        // 8. not_before.
        if (payload.notBefore() != null && checkTime.isBefore(payload.notBefore())) {
            return new LicenseCheckResult(LicenseStatus.NOT_YET_VALID, payload,
                    "valid from " + payload.notBefore());
        }

        // 9. Expiry and grace period.
        LicenseStatus timeStatus = LicenseStatus.VALID;
        if (payload.expiresAt() != null && checkTime.isAfter(payload.expiresAt())) {
            Instant graceEnd = payload.expiresAt().plusSeconds(payload.gracePeriodSeconds());
            if (checkTime.isAfter(graceEnd)) {
                return new LicenseCheckResult(LicenseStatus.EXPIRED, payload,
                        "expired at " + payload.expiresAt());
            }
            timeStatus = LicenseStatus.GRACE_PERIOD;
        }

        // 10. Server binding.
        if (serverUuid != null && "server_uuid".equals(payload.bindingMode())) {
            String normalised = serverUuid.toLowerCase(Locale.ROOT);
            if (!payload.serverUuids().contains(normalised)) {
                return new LicenseCheckResult(LicenseStatus.WRONG_SERVER, payload,
                        "license is registered to " + payload.serverUuids());
            }
        }

        return new LicenseCheckResult(timeStatus, payload, null);
    }

    /** Steps 11 and 12: the product/feature decision, as one call. */
    @Nonnull
    public LicenseStatus checkFeature(@Nonnull LicenseCheckResult result,
                                      @Nonnull String productId,
                                      @Nullable String featureId) {
        if (result.payload() == null || !result.status().grantsAccess()) {
            return result.status();
        }
        if (!result.payload().coversProduct(productId)) {
            return LicenseStatus.WRONG_PRODUCT;
        }
        if (featureId != null && !result.payload().coversFeature(productId, featureId)) {
            return LicenseStatus.WRONG_PRODUCT;
        }
        return result.status();
    }

    /**
     * Rebuild the exact bytes the licensing server signed.
     *
     * <pre>
     *   UTF-8("mystic-license-envelope-v1")
     *   uint32be(9)
     *   for each field, in this fixed order:
     *       uint32be(byteLength)  UTF-8(field)
     * </pre>
     *
     * <p>The base64url <em>text</em> is hashed, not the decoded bytes, so both sides agree without
     * needing a canonical binary encoding.</p>
     */
    @Nonnull
    static byte[] buildSigningInput(String magic, long version, String signingKeyId,
                                    String encryptionKeyId, String encryptionAlgorithm,
                                    String signatureAlgorithm, String iv, String ciphertext,
                                    String authenticationTag) {
        String[] fields = {
                magic,
                Long.toString(version),
                signingKeyId,
                encryptionKeyId,
                encryptionAlgorithm,
                signatureAlgorithm,
                iv,
                ciphertext,
                authenticationTag
        };

        byte[] context = ENVELOPE_SIGNING_CONTEXT.getBytes(StandardCharsets.UTF_8);
        int total = context.length + 4;
        byte[][] encoded = new byte[fields.length][];
        for (int i = 0; i < fields.length; i++) {
            encoded[i] = fields[i].getBytes(StandardCharsets.UTF_8);
            total += 4 + encoded[i].length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(total);
        buffer.put(context);
        buffer.putInt(SIGNED_FIELD_COUNT);
        for (byte[] field : encoded) {
            buffer.putInt(field.length);
            buffer.put(field);
        }
        return buffer.array();
    }

    @Nonnull
    private static LicensePayload readPayload(@Nonnull JsonObject json) {
        JsonObject binding = object(json, "binding");
        List<String> serverUuids = new ArrayList<>();
        String bindingMode = "unbound";
        String boundDiscordUserId = null;

        if (binding != null) {
            String mode = str(binding, "mode");
            if (mode != null) {
                bindingMode = mode;
            }
            if (binding.has("server_uuids") && binding.get("server_uuids").isJsonArray()) {
                for (JsonElement item : binding.getAsJsonArray("server_uuids")) {
                    if (item != null && item.isJsonPrimitive()) {
                        serverUuids.add(item.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            }
            boundDiscordUserId = str(binding, "discord_user_id");
        }

        Map<String, List<String>> products = new LinkedHashMap<>();
        JsonObject productsJson = object(json, "products");
        if (productsJson != null) {
            for (String productId : productsJson.keySet()) {
                List<String> features = new ArrayList<>();
                JsonElement value = productsJson.get(productId);
                if (value != null && value.isJsonArray()) {
                    for (JsonElement item : value.getAsJsonArray()) {
                        if (item != null && item.isJsonPrimitive()) {
                            features.add(item.getAsString());
                        }
                    }
                }
                products.put(productId, features);
            }
        }

        Long grace = integer(json, "grace_period_seconds");
        Long generation = integer(json, "generation");

        return new LicensePayload(
                str(json, "license_id"),
                str(json, "license_type"),
                str(json, "issuer"),
                bindingMode,
                serverUuids,
                boundDiscordUserId,
                products,
                instant(str(json, "issued_at")),
                instant(str(json, "not_before")),
                instant(str(json, "expires_at")),
                grace == null ? 0L : grace,
                generation == null ? 1 : generation.intValue());
    }

    @Nullable
    private static Instant instant(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Nullable
    private static String str(@Nullable JsonObject object, @Nonnull String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    @Nullable
    private static Long integer(@Nullable JsonObject object, @Nonnull String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return null;
        }
    }

    @Nullable
    private static JsonObject object(@Nullable JsonObject parent, @Nonnull String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
