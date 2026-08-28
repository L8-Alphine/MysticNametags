package com.mystichorizons.mysticnametags.license;

import java.time.Instant;
import java.util.Optional;

/**
 * The interface licensed feature code depends on.
 *
 * <p><b>Failure policy.</b> No method here throws, ever. When there is no valid license the mod
 * disables only the licensed feature — it must not refuse to load, crash the server, or disable
 * unlicensed functionality. Turning a billing question into an outage is the worst possible
 * outcome.</p>
 */
public interface MysticLicenseService {

    /** Current status. Never null; {@link LicenseStatus#MISSING} when absent. */
    LicenseStatus status();

    /** Convenience for {@code status().grantsAccess()}. */
    boolean isValid();

    /** True when this product is covered, honouring the {@code *} wildcard. */
    boolean isProductLicensed(String productId);

    /** True when the product is covered and the feature is listed (or {@code *}). */
    boolean hasFeature(String productId, String featureId);

    /** Empty for a non-expiring license, or when there is no valid license. */
    Optional<Instant> expiresAt();

    /** The {@code license_id} of the loaded license, for support and logging. */
    Optional<String> licenseId();
}
