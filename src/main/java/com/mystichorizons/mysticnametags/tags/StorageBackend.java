package com.mystichorizons.mysticnametags.tags;

public enum StorageBackend {
    FILE,
    SQLITE,
    MYSQL;

    public static StorageBackend fromString(String raw) {
        if (raw == null) return FILE;
        try {
            return StorageBackend.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FILE;
        }
    }
}
