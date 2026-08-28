package com.mystichorizons.mysticnametags.license;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Status plus payload plus a short human-readable detail, for the one startup log line.
 *
 * <p>{@link #payload()} is null unless the file verified and decrypted.</p>
 */
public final class LicenseCheckResult {

    private final LicenseStatus status;
    private final LicensePayload payload;
    private final String detail;

    LicenseCheckResult(@Nonnull LicenseStatus status,
                       @Nullable LicensePayload payload,
                       @Nullable String detail) {
        this.status = status;
        this.payload = payload;
        this.detail = detail;
    }

    static LicenseCheckResult failure(@Nonnull LicenseStatus status, @Nullable String detail) {
        return new LicenseCheckResult(status, null, detail);
    }

    @Nonnull
    public LicenseStatus status() {
        return status;
    }

    @Nullable
    public LicensePayload payload() {
        return payload;
    }

    @Nullable
    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return status + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")");
    }
}
