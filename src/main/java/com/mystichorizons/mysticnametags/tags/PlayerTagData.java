package com.mystichorizons.mysticnametags.tags;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PlayerTagData {

    private Set<String> owned = new HashSet<>();
    private Set<String> favorites = new HashSet<>();
    private Map<String, String> loadouts = new LinkedHashMap<>();
    private String equipped;
    private boolean ownNameplateVisible = true;

    public Set<String> getOwned() {
        if (owned == null) {
            owned = new HashSet<>();
        }
        return owned;
    }

    public Set<String> getFavorites() {
        if (favorites == null) {
            favorites = new HashSet<>();
        }
        return favorites;
    }

    public Map<String, String> getLoadouts() {
        if (loadouts == null) {
            loadouts = new LinkedHashMap<>();
        }
        return loadouts;
    }

    public String getEquipped() {
        return equipped;
    }

    public void setEquipped(String equipped) {
        this.equipped = equipped;
    }

    public boolean owns(String id) {
        return id != null && getOwned().contains(id);
    }

    public void addOwned(String id) {
        if (id != null) {
            getOwned().add(id);
        }
    }

    public boolean isFavorite(String id) {
        return id != null && getFavorites().contains(id);
    }

    public boolean addFavorite(String id) {
        return id != null && getFavorites().add(id);
    }

    public boolean removeFavorite(String id) {
        return id != null && getFavorites().remove(id);
    }

    public void clearUnavailableFavorites() {
        getFavorites().removeIf(id -> id == null || !getOwned().contains(id));
    }

    public void putLoadout(String name, String tagId) {
        if (name == null || tagId == null) {
            return;
        }
        getLoadouts().put(name, tagId);
    }

    public boolean removeLoadout(String name) {
        return name != null && getLoadouts().remove(name) != null;
    }

    public boolean isOwnNameplateVisible() {
        return ownNameplateVisible;
    }

    public void setOwnNameplateVisible(boolean ownNameplateVisible) {
        this.ownNameplateVisible = ownNameplateVisible;
    }
}
