package com.mystichorizons.mysticnametags.license;

/**
 * Outcome of validating a {@code license.mclicense} file.
 *
 * <p>Every non-{@link #VALID}/{@link #GRACE_PERIOD} value means "the licensed feature stays off" —
 * never "shut the whole mod down". A missing or broken license file is a licensing problem, not a
 * reason to break somebody's server.</p>
 *
 * <p>Vendored from the {@code mystic-license} spec. Values must not be added or renamed: the
 * licensing portal and the other Mystic mods share this vocabulary.</p>
 */
public enum LicenseStatus {

    /** Signature verified, decrypted, in date, bound to this server. */
    VALID,

    /** Expired, but still inside the grace period recorded in the payload. */
    GRACE_PERIOD,

    /** No license file was found in the configured data directory. */
    MISSING,

    /** The file is not a well-formed MCL1 envelope. */
    INVALID_FORMAT,

    /** Ed25519 verification failed: the file was tampered with or forged. */
    INVALID_SIGNATURE,

    /** AES-256-GCM authenticated decryption failed. */
    DECRYPTION_FAILED,

    /** The license is bound to a different Hytale server UUID. */
    WRONG_SERVER,

    /** The license does not cover the product that asked. */
    WRONG_PRODUCT,

    /** The current time is before the payload's {@code not_before}. */
    NOT_YET_VALID,

    /** Past expiry and past the grace period. */
    EXPIRED,

    /** Envelope or payload format version this build does not understand. */
    UNSUPPORTED_VERSION,

    /** The envelope names a signing key id this build does not trust. */
    UNKNOWN_SIGNING_KEY,

    /** The envelope names a content key id this build does not carry. */
    UNKNOWN_ENCRYPTION_KEY;

    /** True when licensed features should be switched on. */
    public boolean grantsAccess() {
        return this == VALID || this == GRACE_PERIOD;
    }

    /** Plain-English line for the single startup log message. */
    public String operatorSummary() {
        return switch (this) {
            case VALID -> "licensed";
            case GRACE_PERIOD -> "licensed (grace period - renew soon)";
            case MISSING -> "no license file found";
            case INVALID_FORMAT -> "license file is not readable";
            case INVALID_SIGNATURE -> "license signature did not verify";
            case DECRYPTION_FAILED -> "license contents could not be decrypted";
            case WRONG_SERVER -> "license is registered to a different server";
            case WRONG_PRODUCT -> "license does not cover this product";
            case NOT_YET_VALID -> "license is not active yet";
            case EXPIRED -> "license has expired";
            case UNSUPPORTED_VERSION -> "license was issued for a newer version of this mod";
            case UNKNOWN_SIGNING_KEY -> "license was signed by an unknown key";
            case UNKNOWN_ENCRYPTION_KEY -> "license uses a content key this build does not carry";
        };
    }
}
