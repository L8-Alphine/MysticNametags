package com.mystichorizons.mysticnametags.tags;

import java.util.HashSet;
import java.util.Set;

public class PlayerTagData {

    private Set<String> owned = new HashSet<>();
    private String equipped;

    public Set<String> getOwned() {
        return owned;
    }

    public String getEquipped() {
        return equipped;
    }

    public void setEquipped(String equipped) {
        this.equipped = equipped;
    }

    public boolean owns(String id) {
        return owned.contains(id);
    }

    public void addOwned(String id) {
        owned.add(id);
    }
}
