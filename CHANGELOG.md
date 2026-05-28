# MysticNameTags Changelog

All notable changes to this project will be documented in this file.

This project follows **Semantic Versioning** where possible.

---

## [1.2.5] - 2026-05-27

### Hytale Update 5 Compatibility

This update migrates MysticNameTags to **Hytale Update 5 / 0.5.x**.

> **Important:** This version only works on Hytale `0.5.x+`.
> It is not compatible with older dated Hytale server builds such as `2026.03.26-*`.

### Changed

- Updated the project to compile against Hytale Server `0.5.0`.
- Updated the plugin manifest server version range to `>=0.5.0 <0.6.0`.
- Updated RPGLeveling support to compile against `RPGLeveling-0.3.6.jar`.
- Replaced deprecated command permission APIs with the new permission group API.
- Updated command sender display name usage for Update 5 compatibility.
- Updated inventory integration code for the newer Update 5 inventory component access patterns.
- Updated vector and rotation usage for the new Update 5 math APIs.

### RPGLeveling Compatibility

- Added an RPGLeveling compatibility bridge for Update 5 / RPGLeveling `0.3.6`.
- Added RPGLeveling-backed tag requirement keys:
    - `rpgleveling.lvl`
    - `rpgleveling.skills.<stat>`
    - `rpgleveling.skills.available`
    - `rpgleveling.skills.total`
    - `rpgleveling.classes`
    - `rpgleveling.classes.<classId>`
    - `rpgleveling.classes.tier`
    - `rpgleveling.classes.tier.<classId>`
    - `rpgleveling.progression`
    - `rpgleveling.progression.xp`
    - `rpgleveling.progression.required_xp`
    - `rpgleveling.progression.class_kills`
- Preserved earlier aliases such as `rpgleveling.level`, `rpgleveling.class.*`, and `rpgleveling.class_tier.*`.
- RPGLeveling nameplate refresh now routes through the compatibility helper instead of calling the API directly from the scheduler.

### Packet Glyph Nameplate Improvements

- Reworked packet glyph rotation handling for Hytale Update 5.
- Updated glyph yaw math to use Update 5's radians-based rotation system.
- Improved mounted packet glyph behavior so nameplates stay attached to players more smoothly.
- Reduced packet correction behavior that could cause visible snapping or unstable nameplate rotation.
- Improved self-view packet glyph handling for third-person comfort.

### Fixed

- Fixed compile errors caused by removed or changed Hytale Update 5 APIs.
- Fixed command permission API compatibility issues.
- Fixed console/player command sender compatibility problems.
- Fixed inventory requirement checks after Update 5 inventory API changes.
- Fixed packet glyph rotation math using the wrong rotation unit.
- Fixed several nameplate rendering issues caused by Update 5 transform and mount behavior changes.

### Upgrade Notes

Before updating, back up:

```text
settings.json
tags.json
playerdata
```

After installing the new jar:

1. Make sure your server is running Hytale `0.5.x+`.
2. Restart the server.
3. Confirm MysticNameTags loads without compatibility errors.
4. Test `/mnametags nameplate status`.
5. Equip a packet glyph tag and verify self-view and other-player nameplates render correctly.
6. If you use RPGLeveling requirements, verify tags using `rpgleveling.lvl`, `rpgleveling.skills.*`, `rpgleveling.classes.*`, and `rpgleveling.progression.*`.

---

## [1.0.1] - 2026-01-28

### Added
- `/tags reload` command to reload `settings.json` and `tags.json` without restarting the server
    - Protected by the `mysticnametags.reload` permission
    - Uses LuckPerms when available, with Hytale permissions as a fallback
- Optional **EliteEssentials EconomyAPI** support
    - Economy priority order:
        1. VaultUnlocked (preferred)
        2. EliteEssentials EconomyAPI
- Optional **WiFlowPlaceholderAPI** integration
    - Used for UI messages and notifications
    - Designed to expand placeholder usage as API coverage improves
- Improved nameplate refresh handling on:
    - Tag equip / unequip
    - LuckPerms rank or metadata changes
    - Player join / leave
- Permission-aware handling for free tags (permissions are always required)

### Changed
- Unified permission handling through `IntegrationManager`
- Improved economy abstraction with soft-dependency detection
- Nameplate formatting pipeline now clearly separates:
    - Colored output for chat / UI
    - Plain output for nameplate components (API limitation)
- Tag UI now correctly displays `Equip`, `Unequip`, or `Purchase` based on state
- All plugin messages now consistently use the internal color formatting utility

### Fixed
- Fixed free tags being incorrectly blocked despite valid permissions
- Fixed nameplates not updating after LuckPerms group or prefix changes
- Fixed unnecessary world-thread nameplate rebuilds
- Fixed UI loading overlay persisting after tag interactions
- Fixed placeholder registration failures when PlaceholderAPI is not installed

### Notes
- Reloading does not yet force a global nameplate refresh; players may need to re-equip a tag or rejoin
- Nameplate components still do not support colors due to current Hytale API limitations
- Support for BetterScoreboard and player list integration is in progress

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
