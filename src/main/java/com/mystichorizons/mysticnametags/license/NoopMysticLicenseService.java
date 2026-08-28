package com.mystichorizons.mysticnametags.license;

import java.time.Instant;
import java.util.Optional;

/** Always reports {@link LicenseStatus#MISSING} and grants nothing. For tests and unlicensed builds. */
public final class NoopMysticLicenseService implements MysticLicenseService {

    public static final NoopMysticLicenseService INSTANCE = new NoopMysticLicenseService();

    @Override public LicenseStatus status() { return LicenseStatus.MISSING; }
    @Override public boolean isValid() { return false; }
    @Override public boolean isProductLicensed(String productId) { return false; }
    @Override public boolean hasFeature(String productId, String featureId) { return false; }
    @Override public Optional<Instant> expiresAt() { return Optional.empty(); }
    @Override public Optional<String> licenseId() { return Optional.empty(); }
}
