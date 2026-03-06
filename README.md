# MysticNameTags
[![CodeFactor](https://www.codefactor.io/repository/github/l8-alphine/mysticnametags/badge)](https://www.codefactor.io/repository/github/l8-alphine/mysticnametags)

**MysticNameTags** is a modern, permission-driven, and performance-focused
**tag and nameplate system for Hytale servers**.

From **1.0.0 → 1.1.2**, MysticNameTags has evolved into a **stable, integration-aware system** designed for servers that want:

* Clean, scalable UI
* Strict permission control
* Optional economy support
* Optional integration systems
* Zero unnecessary background overhead

Built for large servers. No constant polling. No unsafe component writes.

---

## ✨ Features

---

## 🏷 Custom Player Tags

* Unlimited tags via `tags.json`
* Each tag supports:

  * Custom display text
  * `&` and hex (`&#RRGGBB`) color formatting
  * Optional economy pricing
  * Per-tag permission nodes
  * Category grouping
* Tags may be:

  * Free
  * Purchasable
  * Rank-locked
  * Admin-only

---

## 🎨 Coloring Support

Supported formats:

* Legacy `&` color codes
* Hex colors (`&#RRGGBB`)

⚠ **Nameplate coloring is NOT currently possible** due to Hytale API limitations.
Colors apply to:

* Chat
* UI previews
* Placeholders

Nameplates are automatically stripped to plain text.

---

## 🖥 Clean & Scalable UI

Open the tag UI with:

```
/tags
```

UI features:

* Paginated tag lists
* Category filtering
* Clear cost & permission visibility
* Optimized layout for large tag sets
* Cached permission checks for performance

Includes an **Admin Dashboard** for:

* Viewing tag definitions
* Debugging permission visibility
* Monitoring update notifications
* Integration status overview

---

## 🔐 Permission-First Design

* Tags respect permission nodes defined in `tags.json`
* Supports:

  * Hytale native permissions
  * LuckPerms
  * PermissionsPlus
* Permission checks are cached per-player for UI performance
* No hardcoded permission nodes

Example tag:

```json
{
  "id": "mystic",
  "display": "&#8A2BE2&l[Mystic]",
  "permission": "mysticnametags.tag.mystic",
  "price": 0,
  "purchasable": false,
  "category": "Special"
}
```

---

## ⚙ Config Toggles (1.1.2+)

MysticNameTags now includes full config control via `settings.json`.

### Master Nameplate Toggle

```
nameplatesEnabled = true
```

* When `false`, MysticNameTags restores vanilla nameplates.
* Safe to toggle and reload.

---

### Default Tag System

```
defaultTagEnabled = false
defaultTagId = "mystic"
```

When enabled:

* If a player has no equipped tag,
* MysticNameTags will display the configured default tag automatically.

---

### Endless Leveling Integration Toggle

```
endlessLevelingNameplatesEnabled = true
```

* Allows MysticNameTags to override Endless Leveling’s player nameplate system.
* Prevents double-writing or flickering.
* Safe fallback if Endless Leveling is not installed.

---

## 🔄 Multi-Permission Backend Support (1.0.6+)

Permissions are handled by a unified **IntegrationManager**, automatically selecting the best available backend:

* LuckPerms
* PermissionsPlus
* Native Hytale permissions

Switching permission systems requires no configuration changes.

---

## 💰 Optional Economy Support

Supported systems:

* TheEconomy (recommended)
* EcoTale
* VaultUnlocked
* EliteEssentials

Economy priority:

```
EconomySystem → EcoTale → VaultUnlocked → EliteEssentials
```

Economy checks occur only when:

* Opening the tag UI
* Purchasing a tag

No background loops. No polling.

---

## 🧩 Placeholder Support

### HelpChat PlaceholderAPI

| Placeholder              | Description                             |
| ------------------------ | --------------------------------------- |
| `%mystictags_tag%`       | Colored active tag                      |
| `%mystictags_tag_plain%` | Plain active tag                        |
| `%mystictags_full%`      | Full formatted name (rank + name + tag) |

---

### WiFlow PlaceholderAPI

| Placeholder               | Description          |
| ------------------------- | -------------------- |
| `{mystictags_tag}`        | Colored active tag   |
| `{mystictags_tag_plain}`  | Plain active tag     |
| `{mystictags_full}`       | Full formatted name  |
| `{mystictags_full_plain}` | Plain formatted name |

---

## 🎮 RPG Leveling Integration (Soft Dependency)

To use MysticNameTags with RPG Leveling:

In RPG Leveling config:

```
EnablePlayerLevelNameplate = false
```

In MysticNameTags settings:

```
rpgLevelingNameplatesEnabled = true
```

MysticNameTags will handle the level display in a safe, controlled way.

---

## 🎮 Endless Leveling Integration (1.1.2)

MysticNameTags can now safely override Endless Leveling’s `PlayerNameplateSystem`.

* No double-ticking
* No conflicts
* No flicker
* Fully config-controlled

If enabled:

* MysticNameTags becomes the sole nameplate writer
* Endless Leveling level data is preserved in the display

---

## ⚡ Performance-Focused by Design

* ❌ No constant ticking systems
* ❌ No permission polling
* ❌ No unsafe cross-thread writes
* ✅ World-thread safe updates
* ✅ Only updates when necessary:

  * Player join
  * Tag change
  * Permission changes
  * Reload
  * Explicit integration refresh

Designed for large servers.

---

## ⚠ MiniMessage (Not Supported)

MysticNameTags does NOT support MiniMessage.

Supported:

* `&` colors
* `&#RRGGBB` hex

Unsupported:

* `<gradient>`
* `<rainbow>`
* `<color>`
* `<bold>`
* Any MiniMessage syntax

MiniMessage is intentionally excluded to ensure:

* Predictable formatting
* Zero parsing overhead
* Hytale UI compatibility

---

## 🔑 Core Permission Nodes

| Permission                    | Description                   |
| ----------------------------- | ----------------------------- |
| `mysticnametags.ui.open`      | Opens admin UI                |
| `mysticnametags.reload`       | Reloads config & integrations |
| `mysticnametags.admin.update` | Update notifications          |

Tag permissions are defined per-tag in `tags.json`.

---

## 🚧 Actively Developed

Upcoming:

* Public Developer API
* Extended integration hooks
* Player list / tab integration (pending Hytale API support)
* Expanded UI customization

---

## ❤️ Credits

Developed by **MysticHorizons**
Built for the Hytale modding community
