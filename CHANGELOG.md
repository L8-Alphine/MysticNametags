# MysticNameTags Changelog

All notable changes to this project will be documented in this file.

This project follows **Semantic Versioning** where possible.

---

## [Unreleased] - Licensing on MysticLicenses v2

### Changed

- Tag banners are licensed through MysticLicenses v2 instead of offline
  `license.mclicense` files. Put the key from the portal in `license.key` in
  the plugin's data directory. The server activates online once, then runs on
  a signed authorization it caches and renews in the background, with an
  offline grace period while the licensing service is unreachable. A server
  licensed before starts at once; a first start waits at most 5 seconds, and
  banners switch on by themselves if the answer comes later.
- `/tags reload` re-reads `license.key`: a new key activates and frees the
  previous license's slot, the same key is re-checked at once, and a removed
  key releases the server. Banner art is registered or dropped, and every
  nameplate redrawn, whenever the license changes while the server runs.
- The server's licensing identity lives in `.mystic/` at the server root,
  shared with other Mystic mods, so a server counts once in the portal.
- The licensing service's address and public keys are set at build time
  (`mystic.licensing.url` / `mystic.licensing.keys`, shared by every Mystic
  mod); see `docs/INSTALL.md`. A build without them runs with banners off.

### Network use

- The update check no longer downloads curseforge.com's file listing (the
  Overwolf platform terms forbid automated access to the site). It asks
  CFWidget's public JSON API instead, as Mystic Essentials does, and runs in
  the background rather than holding up startup for up to 10 seconds.
- `settings.json` has a `__network` block: `updateCheckEnabled` and
  `metricsEnabled` switch off the update check and HStats statistics. HStats
  also starts in the background now.
- The README's "Network use" section lists everything the mod sends, where,
  when, and how to turn each part off.

### Removed

- The prototype's offline verifier (`McLicenseVerifier`, `ServerIdentity`
  and friends). `license.mclicense`, `server-id.txt` and
  `license-request.json` are no longer read or written; a leftover
  `license.mclicense` gets a one-line notice at startup.

## [1.3.0] - Storage Fixes

### Fixed

- SQL storage never connected. `DriverManager` finds drivers with a one-off
  `ServiceLoader` scan that runs the first time it is touched, using whichever
  class loader is current then - on a Hytale server that is the launcher's,
  long before the plugin loader that owns our shaded MySQL/SQLite classes
  exists. It therefore never saw them, and `storageBackend: MYSQL` died at boot
  with `No suitable driver found for jdbc:mysql://...`, taking tag ownership and
  stats with it. Connections now go through `JdbcDrivers`, which instantiates
  the bundled drivers from the plugin's own class loader and calls
  `Driver.connect` directly, so nothing depends on DriverManager's registry.
- `shadowJar` merges service files. Without it the MySQL, MariaDB and SQLite
  `META-INF/services/java.sql.Driver` entries overwrite each other and only the
  last one shaded survives.
- The stats table is created as `LONGTEXT` on MySQL/MariaDB instead of `TEXT`.
  A long-lived player's per-block counters can pass the 64KB `TEXT` limit, and
  the row would have been silently truncated. Existing tables are left alone.
- The Redis connection failure is logged with the username, whether a password
  was sent and whether TLS is on, plus what a connection that dies mid-AUTH
  usually means. It previously reported only the address.

### Added

- `storageBackend: MARIADB`, using MariaDB Connector/J. It shares every `mysql*`
  setting, the same tables and the same SQL as `MYSQL` - only the driver and the
  JDBC URL differ - so switching between the two needs no migration. MySQL
  Connector/J and a MariaDB server do not always agree on the handshake, so a
  MariaDB server should use the driver MariaDB ships. `MARIA`, `MARIA_DB` and
  `maria-db` are accepted spellings.
- `/tagsadmin storage`, `/tagsadmin debugstorage` and `/tagsadmin doctor` report
  which JDBC driver serves the configured backend, and `doctor` now fails the
  storage check when no bundled driver accepts the URL rather than always
  passing. `/tagsadmin storage` also shows the Redis username and TLS state.
- `redisUsername` is accepted as an alias for `redisUser`, and the generated
  `settings.json` documents `redisUser`, `redisPassword` and `redisSsl` in the
  `__network` block.

---

## [1.2.9] - 2026-09-02 - Glyph Rendering Fix

### Changed

- Glyph textures moved out of the asset pack. All eight font families used to ship under
  `Common/NPC/MysticNameTags/`, and every texture there is delivered to and atlased by every
  client whether or not the font is ever drawn: 768 entity atlas entries for one nameplate
  font. The PNGs now live in the jar as plain resources and only the configured family is
  registered, at boot and on `/tags reload`, through the same common-asset path banner art
  already uses. Switching the font on a live server now also asks connected clients to
  rebuild their atlases, so the new family shows up without a reconnect.
- Every glyph PNG is a 96x96 canvas with the ink cell at (32,32) and a 32px transparent
  gutter around it; the slot models put their UV box on that cell. The gutter is deliberate:
  the quad renderer samples a little outside the UV box at grazing angles and coarse mips,
  and ink on the texture edge picks up whatever the atlas packed next to it as a thin
  flickering line. Pixel data is unchanged, so the fonts look exactly as before.
- Slot models are generated per ink cell size: `GlyphSlot_*` (16px) for the default family
  and `GlyphSlot32_*` for the seven 32px families.
- `verifyGlyphAssets` now checks the canvas size, that ink stays inside the family's cell,
  and that the quad models' UV box sits on the cell.
- Updated the LuckPerms API compile dependency to 5.5.
- Removed the obsolete local `HytaleServer.jar` copy task; Update 6's server API is
  resolved from Hytale's official Maven repository as version 0.6.0.
- Reworked the glyph nameplate renderer to call the Update 6 server API directly instead
  of reflecting for it. Update 6 promoted the pieces the renderer had been probing for to
  stable public API, so the compatibility shims that guessed at signatures are gone:
    - Glyph, anchor and banner transforms are now built with the public `Position` and
      `ModelTransform` constructors through a single `billboardTransform` helper, replacing
      five copies of the same field-poking block.
    - Glyph entity removal calls `Store#removeEntity(Ref, RemoveReason)` directly instead of
      probing five candidate method signatures in turn.
    - Native nameplate writes call `Store#putComponent` directly instead of searching the
      store class for `putComponent`/`setComponent`/`updateComponent` by parameter count.
- Removed the unused `MountCompat` helper. It reflected for the Update 5
  `MountedComponent(Ref, Rotation3f, MountController)` constructor, which Update 6 changed
  to take a `Vector3f` attachment offset, so the lookup could no longer have succeeded.
  Glyph mounting is handled by the packet path, which already sends the correct offset.
- A glyph line is now one entity per character rather than one carrier entity holding a
  `ModelAttachment` per glyph. Attachment batching does not draw on Update 6: the carrier
  spawns, mounts, positions and even accepts its tint, but its attachments are never
  rendered. Each glyph is now shaped exactly like a banner - a model path plus a texture -
  which is the only packet shape proven to render. The per-character column still rides in
  the `GlyphSlot_*` geometry, so layout is unchanged.
    - Because each glyph carries its own model, the packaged mod no longer ships the 768
      legacy per-character and per-family server `ModelAsset` descriptors.
    - Slot models are generated on the renderer's real four-unit grid across +/-256, which
      is 129 models per ink cell size (258 in total for the 16px and 32px sets) - down from
      the original 257-model single set while covering twice the layouts.
    - The build verifies all eight 96-character font families: image dimensions, canvas and
      gutter, transparency, tint-safe pixels, matching filenames, and the Update 6 quad
      schema.

### Fixed

- Glyphs drew as solid tinted blocks on Update 6. The client's entity texture atlas refuses
  any texture smaller than 32x32 (`Texture width/height must be a multiple of 32 and at
  least 32x32` in the client log, once per glyph), so the 16x16 default set never reached
  the atlas and every quad fell back to an untextured fill that the tint effect then
  coloured. See the texture layout change above.
- The 32px families rendered only the top-left quarter of each glyph: the rewritten renderer
  used the 16x16 slot quads for every family. They now get their own 32x32 slot set.
- **Glyph nameplate text renders again on Update 6.** Every character was being dropped
  before it could be drawn: `populateTextLine` resolved a per-character `ModelAsset`
  (`mysticnametags:Glyph_lo_a` and friends) and skipped the glyph when the lookup missed.
  Those descriptors are not shipped -- only the carrier `GlyphLineBase` is -- so the lookup
  missed for every character, every line came out empty, and no glyph entity was ever
  spawned. Banner tags were unaffected because they bypass this path, which is why a banner
  could render above a player whose name line stayed invisible. The lookup is gone; each glyph
  entity now names its PNG directly, which is all the packet ever needed.
- Long glyph lines no longer collapse at the edges. Slot offsets are clamped to the
  generated grid, and the old +/-128 range only spanned 33 characters per line, so anything
  longer piled its outermost glyphs onto the clamp. The grid now spans +/-256 (65 characters
  per line) at the same four-unit step, which is still far fewer models than the original
  257-model set.
- Native nameplates now flatten configured line breaks into spaces. Formats such as
  `{rank} {name}/n{tag}` keep every resolved placeholder visible on Update 6, while
  experimental glyph nameplates continue to preserve multiline layouts.
- Glyph nameplates no longer fail silently when the server API changes shape. The removed
  shims each ended in a fallback that swallowed the failure: an unresolved transform left
  glyphs at the world origin, an unmatched removal signature leaked anchor entities, and an
  unmatched nameplate setter mutated the component in place without replicating it, so
  nameplates froze at their last value. These paths now use the real API and log failures.

---

## [1.2.8] - 2026-08-28 - Update 6

### Added

- Added **Redis support for networks**, so tag state follows a player between servers.
    - New `REDIS` value for `storageBackend`, storing tag data and tracked stats in
      Redis (`<prefix>tags:<uuid>` / `<prefix>stats:<uuid>`) instead of a local file
      or SQL.
    - New `redisSyncEnabled` setting: cross-server sync over Redis pub/sub, usable
      alongside `MYSQL` storage so durability stays in SQL while Redis carries the
      messages. Changes made on one server reach the others immediately instead of
      only on next join.
    - New settings: `redisHost`, `redisPort`, `redisUser`, `redisPassword`,
      `redisDatabase`, `redisSsl`, `redisKeyPrefix`, `redisTimeoutMs`,
      `redisPoolSize`, `networkServerId`.
    - A player joining any server now re-reads their record from storage, so a tag
      unlocked and equipped on one server is already unlocked and equipped on the
      next one they join.
    - `/tagsadmin storage`, `/tagsadmin debugstorage` and `/tagsadmin doctor` report
      the Redis connection, key prefix, and cross-server sync status.

### Fixed

- Cached player tag data is now dropped on disconnect and re-read on connect.
  Previously it was kept for the lifetime of the server, so a returning player
  could be served a stale copy (and the cache grew without bound).
- A player record that could not be read is now marked unknown instead of being
  treated as empty, and is never written back over stored data. A database or
  Redis outage can no longer wipe a player's tags.
- Migrating `playerdata/*.json` into a shared backend now skips players that are
  already present, instead of overwriting them. On a network, the second server to
  boot no longer clobbers what the first one imported.

- Added **tag banners**: a tag can now render a PNG above the player instead of its
  text display.
    - New `banner` and `bannerScale` fields on tag definitions in `tags.json`.
    - Drop art in the plugin's `images/` folder; `banner` accepts `legend`,
      `legend.png` or `images/legend.png`.
    - Images are registered as client assets and pushed to already-connected players,
      so `/tags reload` picks up new or changed art without a restart.
    - Each banner renders as a single entity at its true aspect ratio, making banner
      tags much cheaper than the same text rendered as glyphs.
    - New settings: `bannersEnabled`, `bannerMaxWidthBlocks`, `bannerMaxHeightBlocks`,
      `bannerMaxFileBytes`, `bannerKeepNameLine`.
    - Requires `experimentalGlyphNameplatesEnabled`; banner tags fall back to their text
      display when banners are unavailable or the PNG is missing.
    - `/tagsadmin doctor` now reports missing banner files and settings that would
      prevent a configured banner from rendering.
    - The `/tags` detail panel shows a preview of the selected tag's banner.
    - Banners are a licensed feature (`mysticnametags` / `tags.banner`), verified offline from
      `license.mclicense` in the plugin data directory. Without a license, banner tags render
      their text display; nothing else in the mod is affected and the server never fails to
      start. A `server-id.txt` is written on first run for binding a license to this server.
    - With `bannerKeepNameLine`, the banner renders in the `{tag}` slot of `nameplateFormat`
      and every other line of the format still renders as glyph text.
- Added MMOSkillTree nameplate format tokens using the same
  `mmoskilltree.*` keys supported by tag stat requirements.
- Added event-driven nameplate refreshes for MMOSkillTree XP and achievement
  changes.
- Added explicit WiFlow and HelpChat PlaceholderAPI expansion support in
  `nameplateFormat`.

### Changed

- Updated the Hytale Server compile target from `0.5.6` to `0.6.0` and the
  manifest compatibility range to `>=0.6.0 <0.7.0` for Hytale Update 6.
- Migrated public commands from the removed `canGeneratePermission()` override
  to Update 6's explicit `requireNoPermission()` declaration.
- Tag displays in the `/tags`, owned-tags, editor, pack manager and dashboard UIs now
  render as text spans instead of flat single-color labels, so multi-color and gradient
  displays show every color they declare.
- Tag **descriptions** now support color codes in the UIs. They previously had their
  formatting stripped before display, in the detail panel, the owned-tags rows and the
  "How it works" popup — even though `/tagsadmin doctor` already validated description
  color syntax.
- The "How it works" and requirements blocks render as spans, so color codes in their
  language strings display as colors instead of as literal `&#RRGGBB` text.

### Fixed

- Fixed stale label colors throughout the UIs. Colors were applied via a label's
  `Style.TextColor`, which only takes effect on a page's first build batch and silently
  does nothing on later updates — so paging through the tag list or changing selection
  could leave a row showing a previous tag's color.

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
