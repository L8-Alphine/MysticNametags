package com.mystichorizons.mysticnametags.license;

/**
 * Key material this build trusts.
 *
 * <h2>Regenerating</h2>
 * In the licensing portal repo:
 * <pre>
 *   npm run keys:show-public   # Ed25519 SPKI base64 for a signing key id
 *   npm run keys:content       # AES-256 content key (base64url, 32 bytes)
 * </pre>
 *
 * <h2>Key rotation</h2>
 * Several signing keys may be trusted at once — call
 * {@link LicenseGate.Builder#trustSigningKey} more than once. When the portal rotates, add the new
 * key id and <em>keep the old one</em> until every outstanding license issued under it has expired;
 * removing it early turns those licenses into {@link LicenseStatus#UNKNOWN_SIGNING_KEY}.
 *
 * <h2>What is secret and what is not</h2>
 * The public signing key is public by definition. The AES content key is <em>not</em> a security
 * boundary: it ships in every jar and must be assumed extractable. It only keeps license contents
 * opaque to casual inspection. Forging a license needs the Ed25519 <em>private</em> key, which never
 * leaves the licensing server.
 */
public final class EmbeddedKeys {

    private EmbeddedKeys() {
    }

    /** Active production signing key id. */
    public static final String SIGNING_KEY_ID = "mystic-signing-2026-01";

    /** Standard-alphabet base64 of the SPKI DER, from {@code npm run keys:show-public}. */
    public static final String SIGNING_SPKI_BASE64 =
            "MCowBQYDK2VwAyEACvpHzlBlwaA+YZw5Yv1AtxdE8gs9k63vdTqcv001Rw0=";

    /** Active production content key id. */
    public static final String CONTENT_KEY_ID = "mystic-license-content-v1";

    /** Unpadded base64url of the 32 content key bytes, from {@code npm run keys:content}. */
    public static final String CONTENT_KEY_B64URL =
            "5bciU8RIzT9otRztfZNiMpiEtpltc-uT6qQytPMSwRI";

    /** A verifier trusting the production keys. */
    public static McLicenseVerifier productionVerifier() {
        return McLicenseVerifier.builder()
                .trustSigningKey(SIGNING_KEY_ID, SIGNING_SPKI_BASE64)
                .addContentKey(CONTENT_KEY_ID, CONTENT_KEY_B64URL)
                .build();
    }
}
