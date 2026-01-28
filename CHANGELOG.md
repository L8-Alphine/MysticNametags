# MysticNameTags Changelog

All notable changes to this project will be documented in this file.

This project follows **Semantic Versioning** where possible.

---

## [1.0.0] – Initial Public Release

### ✨ Features
- Added fully permission-driven **player tag system**
- Tag selection UI accessible via:

```
/tags
```

- Custom tags defined in `tags.json`
- Each tag supports:
- Custom display name
- `&` color codes
- Hex color codes
- Per-tag permission nodes
- LuckPerms integration for:
- Group detection
- Prefix fallback
- Permission checks
- VaultUnlocked integration for future economy support
- Optional PlaceholderAPI support:
- `%mystictags_tag%`
- `%mystictags_tag_plain%`
- `%mystictags_full%`

### 🎨 UI & Display
- Color support in UI previews using:
- `&` formatting
- Hex colors
- Nameplates currently **do not support colors** due to Hytale API limitations
- Nameplates fall back to LuckPerms prefixes when no tag is selected

### ⚙️ Performance
- No background ticking tasks
- No polling loops
- Nameplates rebuild only when:
- Player joins
- Tag changes
- LuckPerms user data is recalculated
- World-thread safe updates

### 🔐 Permissions
- All tags (including free tags) require permissions
- Permissions are defined per-tag in `tags.json`
- Permissions are handled exclusively via LuckPerms

### 🧪 Compatibility
- Tested with:
- LuckPerms
- VaultUnlocked
- EliteEssentials (teleports unaffected)
- Safe to use alongside other chat and utility plugins

### ⚠️ Known Limitations
- No `/reload` command yet
- Server restart required after config changes
- Nameplate coloring not supported by current Hytale API
- PlaceholderAPI support is limited to supported scopes

### 🚧 In Progress / Planned
- Reload command for live config updates
- BetterScoreboard integration
- Player list tag support
- Expanded PlaceholderAPI support
- Public Developer API for external plugins

---
