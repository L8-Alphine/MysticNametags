package com.mystichorizons.mysticnametags.license;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The decrypted license contents, in the shape a mod actually needs. Immutable. */
public final class LicensePayload {

    private final String licenseId;
    private final String licenseType;
    private final String issuer;
    private final String bindingMode;
    private final List<String> serverUuids;
    private final String boundDiscordUserId;
    private final Map<String, List<String>> products;
    private final Instant issuedAt;
    private final Instant notBefore;
    private final Instant expiresAt;
    private final long gracePeriodSeconds;
    private final int generation;

    LicensePayload(@Nullable String licenseId,
                   @Nullable String licenseType,
                   @Nullable String issuer,
                   String bindingMode,
                   List<String> serverUuids,
                   @Nullable String boundDiscordUserId,
                   Map<String, List<String>> products,
                   @Nullable Instant issuedAt,
                   @Nullable Instant notBefore,
                   @Nullable Instant expiresAt,
                   long gracePeriodSeconds,
                   int generation) {
        this.licenseId = licenseId;
        this.licenseType = licenseType;
        this.issuer = issuer;
        this.bindingMode = bindingMode;
        this.serverUuids = List.copyOf(serverUuids);
        this.boundDiscordUserId = boundDiscordUserId;

        Map<String, List<String>> copy = new LinkedHashMap<>();
        products.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.products = Collections.unmodifiableMap(copy);

        this.issuedAt = issuedAt;
        this.notBefore = notBefore;
        this.expiresAt = expiresAt;
        this.gracePeriodSeconds = gracePeriodSeconds;
        this.generation = generation;
    }

    @Nullable public String licenseId() { return licenseId; }
    @Nullable public String licenseType() { return licenseType; }
    @Nullable public String issuer() { return issuer; }
    public String bindingMode() { return bindingMode; }
    public List<String> serverUuids() { return serverUuids; }
    @Nullable public String boundDiscordUserId() { return boundDiscordUserId; }
    public Map<String, List<String>> products() { return products; }
    @Nullable public Instant issuedAt() { return issuedAt; }
    @Nullable public Instant notBefore() { return notBefore; }
    /** Null means non-expiring. */
    @Nullable public Instant expiresAt() { return expiresAt; }
    public long gracePeriodSeconds() { return gracePeriodSeconds; }
    public int generation() { return generation; }

    /** Wildcard-aware product check. */
    public boolean coversProduct(String productId) {
        return products.containsKey("*") || products.containsKey(productId);
    }

    /** Wildcard-aware feature check, at both the product and the feature level. */
    public boolean coversFeature(String productId, String featureId) {
        List<String> wildcardProduct = products.get("*");
        if (wildcardProduct != null
                && (wildcardProduct.contains("*") || wildcardProduct.contains(featureId))) {
            return true;
        }
        List<String> features = products.get(productId);
        return features != null && (features.contains("*") || features.contains(featureId));
    }
}
