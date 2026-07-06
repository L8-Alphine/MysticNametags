# MysticNameTags Changelog

All notable changes to this project will be documented in this file.

This project follows **Semantic Versioning** where possible.

---

## [1.2.7] - 2026-07-06

### Added

- Added player favorite tag support:
    - `/mnametags favorite add <tagId>`
    - `/mnametags favorite remove <tagId>`
    - `/mnametags favorite toggle <tagId>`
    - `/mnametags favorite list`
    - Aliases: `/mnametags fav`, `/mnametags favorites`
- Added random tag equip support:
    - `/mnametags randomtag owned`
    - `/mnametags randomtag favorites`
    - Aliases: `/mnametags random`, `/mnametags tagrandom`
- Added player tag loadouts:
    - `/mnametags loadout save <name>`
    - `/mnametags loadout equip <name>`
    - `/mnametags loadout delete <name>`
    - `/mnametags loadout list`
    - Aliases: `/mnametags tagloadout`, `/mnametags loadouts`
- Added a quick loadout flow in the tag selector UI for saving, equipping, and deleting a quick tag loadout.
- Added favorite, random owned, and random favorite actions to the tag selector UI.
- Added an in-game tag editor for creating, updating, previewing, and deleting simple tag definitions.
- Added an editor tab to the dashboard UI.
- Added a standalone tag editor UI with searchable tag list, live draft capture, preview, save, delete, and new-tag actions.
- Added tag pack import/export support:
    - `/tagsadmin importpack <file.json> [append|upsert|replace]`
    - `/tagsadmin exportpack <file.json> [category]`
    - Tag packs are read from and written to `plugins/MysticNameTags/tagpacks`.
    - Import modes support append-only, upsert, and full replace behavior.
    - Imports create backups of the current `tags.json` before writing.
- Added an in-game tag pack manager UI for listing packs, importing selected packs, choosing import mode, exporting loaded tags, and filtering exports by category.
- Added admin audit logging for tag actions, including favorite changes, loadouts, player grants/removals/resets, tag editor changes, and tag pack import/export.
- Added `/tagsadmin audit [lines]` to show recent audit entries.
- Added an audit-tail action to the dashboard Debug tab.
- Added debug snapshot file output from the dashboard, with the saved file name shown back in the UI.
- Added a public MysticNameTags event API:
    - `MysticNameTagsAPI.listen(...)`
    - `MysticNameTagsAPI.unlisten(...)`
    - `MysticNameTagsAPI.getEventListenerCount()`
    - Events for equip, unequip, unlock, purchase, favorite add/remove, loadout save/equip/delete, pack import/export, tag create/update/delete, admin grant/remove/reset.
- Added public API helpers for tag definition writes:
    - `MysticNameTagsAPI.createOrUpdateSimpleTag(...)`
    - `MysticNameTagsAPI.deleteTag(...)`
- Added optional MMOSkillTree support.
- Added MMOSkillTree-backed requirement/stat keys:
    - `mmoskilltree.total_level`
    - `mmoskilltree.total_xp`
    - `mmoskilltree.level.<skillId>`
    - `mmoskilltree.skill.<skillId>`
    - `mmoskilltree.xp.<skillId>`
    - `mmoskilltree.progress.<skillId>`
    - `mmoskilltree.level_progress.<skillId>`
    - `mmoskilltree.achievement.<id>`
    - `mmoskilltree.achievement.unlocked.<id>`
    - `mmoskilltree.achievement_progress.<id>`
    - `mmoskilltree.achievement.progress.<id>`
    - `mmoskilltree.achievement_points`
    - `mmoskilltree.stat.<canonicalKey>`
    - `mmoskilltree.statistics.<canonicalKey>`
- Added optional MysticVanish support for packet glyph nameplates, so vanished players' glyph nameplates follow MysticVanish visibility rules.
- Added dashboard and doctor diagnostics for MMOSkillTree and MysticVanish.
- Added `autoUnlockPermissionTags` to `settings.json`.
    - When enabled, free tags with permission nodes are treated as unlocked while the player holds the permission.
    - Removing the permission revokes access again because the unlock is not persisted as owned data.
- Added persisted player data fields for favorites and loadouts.
- Added shared UI templates for common MysticNameTags controls and rows:
    - `MysticCommon.ui`
    - `TagRow.ui`
    - `EditorTagRow.ui`
    - `PackRow.ui`
    - `OwnedTagRowMain.ui`
    - `OwnedTagRowEquip.ui`
    - `OwnedTagRowFav.ui`

### Changed

- Bumped plugin version from `1.2.6` to `1.2.7`.
- Updated Hytale Server compile target from `0.5.0` to `0.5.6`.
- Updated VaultUnlocked compile target from `2.18.3` to `2.20.0` and disabled transitive dependency resolution for it.
- Added `Ziggfreed:MMOSkillTree` as an optional dependency in `manifest.json`.
- Reworked the economy backend chain so backends are checked live instead of being permanently pruned during startup.
- Moved VaultUnlocked to the front of the ledger economy chain.
- Added a startup retry probe for VaultUnlocked providers that register after MysticNameTags starts.
- Updated economy status output to report the active backend more accurately.
- Updated `/mnametags info` economy display so VaultUnlocked appears as the preferred backend when available.
- Updated `/tagsadmin doctor` diagnostics with MMOSkillTree and MysticVanish status lines.
- Updated admin give/remove/reset operations to record the acting command sender in audit logs and public events.
- Updated admin remove/reset logic to also clean favorites and loadouts that reference removed tags.
- Updated the tag selector UI to use dynamic row templates instead of a fixed set of manually addressed row elements.
- Updated the tag selector and tag editor search handling to capture live text values without fighting the player's typing.
- Updated owned-tags UI rows to use separate reusable templates for main rows, equip actions, and favorite indicators.
- Updated dashboard styling and layout with new actions for tag editing, pack import/export, and audit viewing.
- Updated tag rendering logic so permission-auto-unlocked tags are treated as effectively owned in player-facing UI and equip flows.
- Updated glyph nameplate rendering to use glyph-specific colored text rather than chat-formatted text.
- Updated placeholder output to preserve compact hex colors for placeholder consumers.
- Expanded MiniMessage color parsing support for glyphs, UI, placeholders, and formatting utilities.
- Expanded MiniMessage aliases for colors and decorations, including short hex colors, `color:`/`colour:`/`c:` tags, gray/grey aliases, purple/magenta/pink aliases, underline, strikethrough, and obfuscated decorations.
- Changed chat/placeholder color handling so expanded hex is only used where the consumer can safely parse it.
- Updated tag config writes to rebuild tag indexes, refresh caches, and refresh online nameplates after tag editor and pack operations.

### Fixed

- Fixed VaultUnlocked providers that register late being missed permanently after startup.
- Fixed VaultUnlocked providers that work but report `isEnabled()` unexpectedly being treated as unavailable.
- Fixed balance, purchase, and backend availability checks using stale economy detection.
- Fixed debug snapshot actions only writing to logs by also saving a snapshot file when possible.
- Fixed dashboard, pack manager, tag selector, and tag editor text fields losing or overwriting live typed values during refreshes.
- Fixed tag selector search so filtering applies live and clearing the search box intentionally resets the visible field.
- Fixed tag editor draft fields so preview updates do not interrupt typing.
- Fixed pack manager export name capture so exporting uses the current typed filename.
- Fixed fixed-row UI behavior that could leave stale tag rows visible or make dynamic row event bindings unreliable.
- Fixed owned/favorite/loadout cleanup when tags are removed by admins or deleted from the tag editor.
- Fixed player reset not clearing favorites and loadouts.
- Fixed permission-auto-unlocked tags showing as unavailable even when the player currently has the required permission.
- Fixed compact hex colors such as `&#4f5c63` being partially interpreted by legacy parsers as `&4` plus literal text.
- Fixed placeholder color output leaking expanded `&x&...` sequences into consumers that expect compact hex.
- Fixed glyph nameplate color parsing for MiniMessage named colors, reset tags, short hex tags, and `color:#hex` style tags.
- Fixed vanished players' packet glyph nameplates remaining visible to viewers who should not see them.
- Fixed economy labels that still described EconomySystem as "primary" even when another backend was active.

### Developer API

- Added `MysticNameTagsEvent`, `MysticNameTagsEventBus`, `MysticNameTagsEventListener`, and `MysticNameTagsEventType`.
- Added event publishing for user, admin, editor, and pack operations.
- Added `TagEditResult` / `TagEditStatus` for create, update, and delete workflows.
- Added `FavoriteResult`, `LoadoutResult`, and `LoadoutEquipResult`.
- Added `TagPackImportResult`, `TagPackExportResult`, and `TagPackImportMode`.
- Added public MMOSkillTree helpers:
    - `MysticNameTagsAPI.isMMOSkillTreeAvailable()`
    - `MysticNameTagsAPI.getMMOSkillTreeStat(...)`
- Added `IntegrationManager.getActiveEconomyBackendName()`.
- Added `IntegrationManager.refreshEconomyBackends(...)`.
- Added `IntegrationManager.isVaultApiAvailable()`.
- Added `IntegrationManager.isMMOSkillTreeAvailable()`.
- Added `IntegrationManager.isMysticVanishAvailable()`.

### Upgrade Notes

Before updating, back up:

```text
settings.json
tags.json
playerdata
tagpacks
```

After installing the new jar:

1. Make sure your server is running Hytale `0.5.6+`.
2. Restart the server.
3. Run `/tagsadmin doctor` and confirm economy, MMOSkillTree, and MysticVanish integrations show the expected status.
4. If you use permission-only free tags, decide whether to enable `autoUnlockPermissionTags` in `settings.json`.
5. If you use tag packs, create or copy JSON packs into `plugins/MysticNameTags/tagpacks`.
6. Test `/mnametags favorite list`, `/mnametags randomtag owned`, and `/mnametags loadout list` with a normal player account.
7. Open `/tags ui`, verify the dashboard Tag Editor and Pack Manager actions, then test `/tagsadmin audit`.

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
