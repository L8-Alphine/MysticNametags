# MysticNameTags Installation Guide

A comprehensive guide to installing and configuring MysticNameTags on your Hytale server.

## Prerequisites

Before installing MysticNameTags, ensure your server meets the following requirements:

### Server Requirements

- **Hytale Server** (Java-based, 2026.03.26 or later)
- **Java 25** or higher (required for compilation and runtime)
- **Gradle** (for building from source) or **Maven-compatible package manager**
- Minimum **2GB RAM** recommended (more for large servers with 100+ players)

### Required Dependencies

MysticNameTags requires at least one permission system installed on your server:

- **LuckPerms** (recommended - fully supported)
- **HyperPerms** (supported)
- **PermissionsPlus** (supported)
- **Hytale Native Permissions** (supported as fallback)

### Optional Dependencies

Enhance your installation with these optional plugins:

| Plugin | Feature | Status |
|--------|---------|--------|
| **TheEconomy** | Economy support for purchasable tags | Recommended |
| **EcoTale** | Alternative economy system | Supported |
| **VaultUnlocked** | Economy abstraction layer | Supported |
| **EliteEssentials** | Economy and utility features | Supported |
| **HelpChat PlaceholderAPI** | Chat placeholder support | Optional |
| **WiFlow PlaceholderAPI** | Alternative placeholder system | Optional |
| **RPG Leveling** | Level display integration | Optional |
| **Endless Leveling** | Advanced leveling integration (v1.1.2+) | Optional |
| **HyEssentialsX** | Utility features | Optional |
| **CoinsAndMarkets** | Coin economy system | Optional |
| **PrefixesPlus** | Prefix integration | Optional |
| **EcoTaleQuests** | Quest integration | Optional |
| **Playtime** | Playtime tracking | Optional |

---

## Installation Methods

### Method 1: Pre-Built JAR (Recommended for Users)

This is the fastest way to get started without compiling code.

#### Step 1: Download the Plugin

1. Navigate to the [MysticNameTags GitHub Releases](https://github.com/L8-Alphine/MysticNametags/releases)
2. Download the latest `MysticNameTags-x.x.x.jar` file
3. Verify the version matches your server requirements (v1.0.1 or later)

Current version in repository: **v1.2.3**

#### Step 2: Install to Server

1. Locate your Hytale server's plugins directory:
   ```
   <server-directory>/mods/
   ```
   or
   ```
   <server-directory>/plugins/
   ```

2. Copy the downloaded JAR file into this directory:
   ```bash
   cp MysticNameTags-1.2.3.jar /path/to/server/mods/
   ```

3. Ensure file permissions allow the server to read the JAR:
   ```bash
   chmod 644 /path/to/server/mods/MysticNameTags-1.2.3.jar
   ```

#### Step 3: Restart Your Server

```bash
# Stop the server gracefully
stop

# Start the server
./start.sh  # or appropriate startup script
```

Monitor the startup logs for initialization messages:
```
[MysticNameTags] Initializing MysticNameTags v1.2.3...
[MysticNameTags] Loaded configuration from settings.json
[MysticNameTags] Loaded X custom tags from tags.json
[MysticNameTags] Permission backend initialized: [LuckPerms|HyperPerms|PermissionsPlus|Native]
[MysticNameTags] WiFlowPlaceholderAPI present=true|false | enabled=true|false
[MysticNameTags] at.helpch PlaceholderAPI present=true|false | enabled=true|false
[MysticNameTags] MysticNameTags enabled successfully!
```

---

### Method 2: Build from Source (For Developers)

#### Step 1: Clone the Repository

```bash
git clone https://github.com/L8-Alphine/MysticNametags.git
cd MysticNametags
```

#### Step 2: Verify Java Version

MysticNameTags requires Java 25:

```bash
java -version
# Output should show: openjdk version "25" or similar
```

If you have multiple Java versions installed, specify the correct one:

```bash
JAVA_HOME=/path/to/java25 gradle build
```

#### Step 3: Build the Plugin

Using Gradle (included via Gradle Wrapper):

```bash
# Linux/macOS
./gradlew build

# Windows
gradlew.bat build
```

The build process will:
1. Download dependencies from Maven repositories (Hytale official, CodeMC)
2. Compile Java source code
3. Process resources and generate glyph slot models
4. Create a shadow JAR with all dependencies bundled (excludes Hytale Server classes)

**Build output:** `build/libs/MysticNameTags.jar`

#### Step 4: Deploy to Server

**Option A: Manual Deployment**

```bash
cp build/libs/MysticNameTags.jar /path/to/server/mods/
```

**Option B: Gradle Deploy Task**

```bash
# Automatically copies the built JAR to server/mods/
./gradlew deployToServer
```

Ensure your `server/` directory structure exists:
```
<project-root>/
├── server/
│   ├── mods/
│   ├── config/
│   └── world/
```

#### Step 5: Restart Server

Same as Method 1, Step 3.

---

## Configuration Setup

After installation, MysticNameTags creates configuration files on first startup.

### Config File Location

```
<server-directory>/
├── config/
│   ├── MysticNameTags/
│   │   ├── settings.json       (Master settings)
│   │   ├── tags.json           (Custom tag definitions)
│   │   └── lang/               (Localization files)
│   │       └── en_US/
│   │           ├── messages.json
│   │           └── howitworkspanel.json
```

### 1. `settings.json` - Master Configuration

**First startup (auto-generated):**

```json
{
  "_": "MysticNameTags settings.json – edit & reload/restart to apply changes.",
  "__core": [
    "Core nameplate settings.",
    "nameplateFormat = tokens: {rank}, {name}, {tag}, {endless_level}, {endless_prestige}, {endless_race}, {endless_primary_class}, {endless_secondary_class}, {rpg_level}, {ecoques_rank}",
    "nameplateFormat supports /n for a new line",
    "stripExtraSpaces = condense multiple spaces",
    "language = locale bundle (e.g. en_US)",
    "tagDelaysecs = cooldown (seconds) before equipping a DIFFERENT tag again (0 = off)"
  ],
  "nameplateFormat": "{rank} {name} {tag}",
  "stripExtraSpaces": true,
  "language": "en_US",
  "tagDelaysecs": 20,
  "__storage": [
    "Storage backend for tag ownership data.",
    "storageBackend = FILE / SQLITE / MYSQL"
  ],
  "storageBackend": "FILE",
  "sqliteFile": "playerdata.db",
  "mysqlHost": "localhost",
  "mysqlPort": 3306,
  "mysqlDatabase": "mysticnametags",
  "mysqlUser": "root",
  "mysqlPassword": "password",
  "__nameplates": [
    "Nameplate behavior.",
    "nameplatesEnabled = master toggle",
    "defaultTagEnabled = use defaultTagId when no tag equipped",
    "defaultTagId must match tags.json id"
  ],
  "nameplatesEnabled": true,
  "defaultTagEnabled": false,
  "defaultTagId": "mystic",
  "__endless": [
    "EndlessLeveling integration toggles."
  ],
  "endlessLevelingNameplatesEnabled": false,
  "endlessRaceDisplay": false,
  "endlessPrestigeDisplay": false,
  "endlessPrimaryClassDisplay": false,
  "endlessSecondaryClassDisplay": false,
  "endlessPrestigePrefix": "P",
  "__placeholders": [
    "Placeholder APIs.",
    "Auto-detect flags control whether detection can override the enabled flags."
  ],
  "wiFlowPlaceholdersAutoDetect": true,
  "wiFlowPlaceholdersEnabled": false,
  "helpchPlaceholderApiAutoDetect": true,
  "helpchPlaceholderApiEnabled": false,
  "__economy": [
    "Tag purchasing & permission gating.",
    "fullPermissionGate = permission node fully gates tags (can hide/block access).",
    "permissionGate = tag remains visible, but permission node is required to unlock/equip."
  ],
  "economySystemEnabled": true,
  "useCoinSystem": false,
  "usePhysicalCoinEconomy": false,
  "fullPermissionGate": false,
  "permissionGate": false,
  "__rpg": [
    "RPGLeveling integration."
  ],
  "rpgLevelingNameplatesEnabled": false,
  "rpgLevelingRefreshSeconds": 30,
  "__playtime": [
    "Playtime provider + extra commands.",
    "playtimeProvider = AUTO / INTERNAL / ZIB_PLAYTIME / NONE"
  ],
  "playtimeProvider": "AUTO",
  "ownedTagsCommandEnabled": true,
  "__experimental_glyph_nameplates": [
    "⚠ EXPERIMENTAL ⚠",
    "Glyph nameplates packet-spawn models and mount them to the player.",
    "Keep disabled unless testing with low player counts."
  ],
  "experimentalGlyphNameplatesEnabled": false,
  "experimentalGlyphMaxChars": 32,
  "experimentalGlyphUpdateTicks": 1,
  "experimentalGlyphMaxEntitiesPerPlayer": 40,
  "experimentalGlyphViewerActivationDistance": 12.0,
  "experimentalGlyphViewerDropDistance": 14.0,
  "experimentalGlyphViewerRefreshActiveMs": 25,
  "experimentalGlyphViewerRefreshIdleMs": 500,
  "experimentalGlyphIdleFollowIntervalMs": 500,
  "experimentalGlyphRotationSyncIntervalMs": 25,
  "experimentalGlyphMaxLines": 2,
  "experimentalGlyphMaxCharsPerLine": 32,
  "experimentalGlyphLineSpacing": 0.30,
  "experimentalGlyphTintStrength": 0.65
}
```

**Core Settings Reference:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `nameplateFormat` | String | `"{rank} {name} {tag}"` | Format with tokens: `{rank}`, `{name}`, `{tag}`, `{endless_level}`, `{endless_prestige}`, `{endless_race}`, `{endless_primary_class}`, `{endless_secondary_class}`, `{rpg_level}`, `{ecoques_rank}`. Use `/n` for new lines. |
| `stripExtraSpaces` | Boolean | `true` | Remove multiple consecutive spaces from formatted output |
| `language` | String | `"en_US"` | Locale bundle (e.g., `en_US`, `fr_FR`, etc.) |
| `tagDelaysecs` | Number | `20` | Cooldown (seconds) before equipping a DIFFERENT tag again (0 = disabled) |

**Storage Backend Reference:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `storageBackend` | String | `"FILE"` | Storage system: `FILE` (JSON files), `SQLITE`, or `MYSQL` |
| `sqliteFile` | String | `"playerdata.db"` | SQLite file name (relative to config folder) |
| `mysqlHost` | String | `"localhost"` | MySQL server hostname |
| `mysqlPort` | Number | `3306` | MySQL server port |
| `mysqlDatabase` | String | `"mysticnametags"` | MySQL database name |
| `mysqlUser` | String | `"root"` | MySQL username |
| `mysqlPassword` | String | `"password"` | MySQL password |

**Nameplate Settings Reference:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `nameplatesEnabled` | Boolean | `true` | Master toggle for nameplate system |
| `defaultTagEnabled` | Boolean | `false` | Auto-apply default tag to players without selection |
| `defaultTagId` | String | `"mystic"` | Tag ID to use as default (must exist in tags.json) |

**EndlessLeveling Integration Settings:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `endlessLevelingNameplatesEnabled` | Boolean | `false` | Enable Endless Leveling nameplate override (v1.1.2+) |
| `endlessRaceDisplay` | Boolean | `false` | Display player race in nameplate |
| `endlessPrestigeDisplay` | Boolean | `false` | Display prestige tier in nameplate |
| `endlessPrimaryClassDisplay` | Boolean | `false` | Display primary class in nameplate |
| `endlessSecondaryClassDisplay` | Boolean | `false` | Display secondary class in nameplate |
| `endlessPrestigePrefix` | String | `"P"` | Prefix before prestige number (e.g., "P" → "P3") |

**PlaceholderAPI Settings:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `wiFlowPlaceholdersAutoDetect` | Boolean | `true` | Auto-detect WiFlow PlaceholderAPI presence |
| `wiFlowPlaceholdersEnabled` | Boolean | `false` | Enable WiFlow PlaceholderAPI integration |
| `helpchPlaceholderApiAutoDetect` | Boolean | `true` | Auto-detect HelpChat PlaceholderAPI presence |
| `helpchPlaceholderApiEnabled` | Boolean | `false` | Enable HelpChat PlaceholderAPI integration |

**Economy & Permission Settings:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `economySystemEnabled` | Boolean | `true` | Enable economy/purchasable tags |
| `useCoinSystem` | Boolean | `false` | Use coin-based economy instead of cash |
| `usePhysicalCoinEconomy` | Boolean | `false` | Use physical coins (items) for economy |
| `fullPermissionGate` | Boolean | `false` | Permission node fully blocks access to tag |
| `permissionGate` | Boolean | `false` | Permission node required to unlock/equip (tag remains visible) |

**RPG Leveling Settings:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `rpgLevelingNameplatesEnabled` | Boolean | `false` | Enable RPG Leveling nameplate integration |
| `rpgLevelingRefreshSeconds` | Number | `30` | Refresh interval for RPG level data (minimum: 5) |

**Playtime & Features:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `playtimeProvider` | String | `"AUTO"` | Playtime source: `AUTO` (detect), `INTERNAL`, `ZIB_PLAYTIME`, `NONE` |
| `ownedTagsCommandEnabled` | Boolean | `true` | Enable `/tags owned` command for viewing owned tags |

**How to Edit:**

1. Stop the server
2. Edit `config/MysticNameTags/settings.json`
3. **Option A:** Restart the server
4. **Option B:** Use `/tags reload` (requires `mysticnametags.reload` permission)

---

### 2. `tags.json` - Tag Definitions

**First startup (auto-generated with examples):**

```json
{
  "tags": [
    {
      "id": "mystic",
      "display": "&#8A2BE2&l[Mystic]",
      "description": "A mystical tag for special players",
      "permission": "mysticnametags.tag.mystic",
      "price": 0,
      "purchasable": false,
      "category": "Special"
    },
    {
      "id": "legendary",
      "display": "&#FFD700&l[Legendary]",
      "description": "The legendary tag",
      "permission": "mysticnametags.tag.legendary",
      "price": 50000,
      "purchasable": true,
      "category": "Premium"
    },
    {
      "id": "vip",
      "display": "&#00FF00&l[VIP]",
      "description": "VIP member tag",
      "permission": "mysticnametags.tag.vip",
      "price": 0,
      "purchasable": false,
      "category": "Ranks",
      "requiredPlaytimeMinutes": 1440
    }
  ]
}
```

**Tag Definition Reference:**

| Field | Type | Required | Example | Description |
|-------|------|----------|---------|-------------|
| `id` | String | Yes | `"mystic"` | Unique identifier (alphanumeric, no spaces) |
| `display` | String | Yes | `"&#8A2BE2[Mystic]"` | Display text with color codes |
| `description` | String | No | `"A mystical tag"` | UI description shown to players |
| `permission` | String | Yes | `"mysticnametags.tag.mystic"` | Permission node required to access tag |
| `price` | Number | Yes | `50000` | Cost in economy currency (0 = free) |
| `purchasable` | Boolean | Yes | `true` | Whether tag can be bought with economy |
| `category` | String | Yes | `"Premium"` | UI category grouping |
| `requiredPlaytimeMinutes` | Number | No | `1440` | Playtime requirement in minutes (optional) |
| `requiredOwnedTags` | Array | No | `["vip", "member"]` | List of tag IDs player must own first |
| `requiredItems` | Array | No | `[{"itemId": "diamond", "amount": 5}]` | Required items to unlock |
| `requiredStats` | Array | No | `[{"key": "kills", "min": 100}]` | Stat requirements |
| `placeholderRequirements` | Array | No | `[{"placeholder": "level", "operator": ">=", "value": "10"}]` | PlaceholderAPI requirements |
| `onUnlockCommands` | Array | No | `["say {player} unlocked a tag!"]` | Commands to run on unlock |

**Color Format Support:**

- **Legacy Codes:** `&0` - `&f` for standard Minecraft colors
  - `&l` = Bold, `&o` = Italic, `&n` = Underline, `&m` = Strikethrough
  - Example: `&4&l[ADMIN]` = Bold red text
  
- **Hex Colors:** `&#RRGGBB` for custom colors
  - Example: `&#FF5733[Custom]` = Orange text
  - Uses 6-digit hex color codes

**Permission Gating Modes:**

```json
{
  "id": "restricted_tag",
  "permission": "mysticnametags.tag.restricted",
  "fullPermissionGate": true
}
```

- **Full Gate** (`fullPermissionGate: true`): Tag is completely hidden from players without permission
- **Soft Gate** (`permissionGate: true`): Tag is visible but can't be unlocked/equipped without permission

**Example: Multi-Requirement Tag**

```json
{
  "id": "elite_master",
  "display": "&#FF1493&l&o[Elite Master]",
  "description": "Requires playtime, coins, and owned tags",
  "permission": "mysticnametags.tag.elite_master",
  "price": 500000,
  "purchasable": true,
  "category": "Elite",
  "requiredPlaytimeMinutes": 10080,
  "requiredOwnedTags": ["vip", "premium"],
  "requiredItems": [
    {
      "itemId": "emerald",
      "amount": 50
    }
  ],
  "requiredStats": [
    {
      "key": "kills",
      "min": 1000
    },
    {
      "key": "blocks_broken",
      "min": 50000
    }
  ],
  "onUnlockCommands": [
    "broadcast {player} has unlocked the Elite Master tag!",
    "give {player} diamond 1"
  ]
}
```

**Complete Tag Examples:**

```json
{
  "tags": [
    {
      "id": "admin",
      "display": "&4&l[ADMIN]",
      "permission": "mysticnametags.tag.admin",
      "price": 0,
      "purchasable": false,
      "category": "Staff"
    },
    {
      "id": "builder",
      "display": "&#2E8B57[Builder]",
      "description": "For creative builders",
      "permission": "mysticnametags.tag.builder",
      "price": 0,
      "purchasable": false,
      "category": "Staff"
    },
    {
      "id": "gold_member",
      "display": "&#FFD700[Gold]",
      "description": "Premium membership",
      "permission": "mysticnametags.tag.gold",
      "price": 100000,
      "purchasable": true,
      "category": "Memberships"
    },
    {
      "id": "emerald_member",
      "display": "&#50C878[Emerald]",
      "description": "Top tier membership",
      "permission": "mysticnametags.tag.emerald",
      "price": 250000,
      "purchasable": true,
      "category": "Memberships",
      "requiredOwnedTags": ["gold_member"]
    },
    {
      "id": "speedrunner",
      "display": "&#FF4500&o[Speedrunner]",
      "description": "Complete challenges quickly",
      "permission": "mysticnametags.tag.speedrunner",
      "price": 0,
      "purchasable": false,
      "category": "Achievements",
      "requiredStats": [
        {
          "key": "challenge_completion_time",
          "min": 50
        }
      ]
    }
  ]
}
```

---

### 3. Localization Files (Optional)

MysticNameTags includes comprehensive localization support.

**Language Files Location:**
```
config/MysticNameTags/lang/<locale>/
├── messages.json          (UI and command messages)
└── howitworkspanel.json   (Tutorial panel content)
```

**Supported Locales:**
- `en_US` (English - Default)
- Other locales can be added by copying `en_US` folder and translating

**messages.json Structure:**

Contains 200+ translation keys for:
- UI labels and buttons
- Command responses
- Error messages
- Dashboard text
- Tag requirement descriptions

**howitworkspanel.json Structure:**

```json
{
  "howitworks": {
    "title": "How It Works",
    "content": [
      "Select a tag to see details and requirements.",
      "Complete requirements to unlock tags.",
      "Equip your favorite tag!"
    ]
  }
}
```

**To Add a New Language:**

1. Copy `config/MysticNameTags/lang/en_US/` folder
2. Rename to your locale (e.g., `fr_FR`, `es_ES`)
3. Edit `messages.json` and translate values
4. Set `language: "fr_FR"` in `settings.json`
5. Restart server or use `/tags reload`

---

## Permission System Setup

### LuckPerms Setup (Recommended)

If using LuckPerms for permissions:

#### 1. Verify LuckPerms Installation

```bash
# Check if LuckPerms plugin is loaded
/lp info
```

#### 2. Grant Tag Permissions

```bash
# Grant a user a tag
/lp user <username> permission set mysticnametags.tag.vip true

# Grant a group a tag
/lp group <groupname> permission set mysticnametags.tag.vip true

# Grant admin permissions
/lp user <username> permission set mysticnametags.reload true
/lp user <username> permission set mysticnametags.ui.open true
/lp user <username> permission set mysticnametags.admin.update true
```

#### 3. Default Tag Permission (Optional)

```bash
# If using default tags, grant the permission for default tag
/lp group default permission set mysticnametags.tag.mystic true
```

#### 4. Bulk Grant Tags to Group

```bash
# Grant all tags in a category to a group
/lp group premium permission set mysticnametags.tag.gold true
/lp group premium permission set mysticnametags.tag.emerald true
/lp group premium permission set mysticnametags.tag.platinum true
```

### HyperPerms Setup

If using HyperPerms:

```bash
# Grant permissions to player
/perms user <username> add mysticnametags.tag.vip
/perms user <username> add mysticnametags.reload

# Grant permissions to group
/perms group <groupname> add mysticnametags.tag.vip
/perms group <groupname> add mysticnametags.reload
```

### PermissionsPlus Setup

If using PermissionsPlus:

```bash
# Grant a user a tag permission
/perms user <username> add mysticnametags.tag.vip

# Grant a group a tag permission
/perms group <groupname> add mysticnametags.tag.vip

# Grant admin permissions
/perms user <username> add mysticnametags.reload
/perms user <username> add mysticnametags.ui.open
```

### Native Hytale Permissions

MysticNameTags falls back to native Hytale permissions if no other system is detected:

```bash
# Grant permissions via Hytale commands
/permission grant <player> mysticnametags.tag.vip
/permission grant <player> mysticnametags.reload
/permission grant <player> mysticnametags.ui.open
```

### Core Permission Nodes

| Permission | Description | Default |
|------------|-------------|---------|
| `mysticnametags.ui.open` | Opens admin dashboard UI | Op only |
| `mysticnametags.reload` | Reloads config & tags | Op only |
| `mysticnametags.admin.update` | Receives update notifications | Op only |
| `mysticnametags.tag.<id>` | Access to specific tag | Custom per tag |

---

## Economy System Setup

### Supported Economy Plugins

MysticNameTags supports multiple economy systems with automatic detection:

1. **TheEconomy** (Recommended - best performance)
2. **EcoTale**
3. **VaultUnlocked**
4. **EliteEssentials**
5. **CoinsAndMarkets**

### Economy Plugin Priority

MysticNameTags checks for economy plugins in this order:
```
TheEconomy → EcoTale → VaultUnlocked → EliteEssentials → CoinsAndMarkets
```

The first detected plugin is used. Ensure only one economy plugin is active for consistent pricing.

### Coin System vs. Cash System

**Cash System (Default):**
```json
{
  "useCoinSystem": false,
  "usePhysicalCoinEconomy": false,
  "tags": [
    {
      "id": "vip",
      "price": 50000,
      "purchasable": true
    }
  ]
}
```

**Coin System:**
```json
{
  "useCoinSystem": true,
  "usePhysicalCoinEconomy": false,
  "tags": [
    {
      "id": "premium",
      "price": 100,
      "purchasable": true
    }
  ]
}
```

**Physical Coin Economy:**
```json
{
  "useCoinSystem": true,
  "usePhysicalCoinEconomy": true,
  "tags": [
    {
      "id": "exclusive",
      "price": 50,
      "purchasable": true
    }
  ]
}
```

### Install an Economy Plugin

**Example: Installing TheEconomy**

1. Download the latest TheEconomy JAR from CurseForge
2. Place in `server/mods/` directory
3. Restart server
4. Verify installation:
   ```bash
   /economy info
   ```
5. Enable purchasable tags in `settings.json`:
   ```json
   {
     "economySystemEnabled": true
   }
   ```
6. Set prices in `tags.json`:
   ```json
   {
     "id": "vip",
     "price": 50000,
     "purchasable": true
   }
   ```

---

## PlaceholderAPI Integration (Optional)

### WiFlow PlaceholderAPI

If using WiFlow PlaceholderAPI:

#### Installation

1. Download WiFlow PlaceholderAPI
2. Place in `server/mods/`
3. Restart server

#### Configuration

MysticNameTags auto-detects WiFlow. To override:
```json
{
  "wiFlowPlaceholdersAutoDetect": false,
  "wiFlowPlaceholdersEnabled": true
}
```

#### Available Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|-----------------|
| `{mystictags_tag}` | Player's equipped tag with color | `[Mystic]` |
| `{mystictags_tag_plain}` | Player's equipped tag (plain text) | `[Mystic]` |
| `{mystictags_full}` | Full formatted name (rank + name + tag) | `[Admin] PlayerName [VIP]` |
| `{mystictags_full_plain}` | Plain full name | `[Admin] PlayerName [VIP]` |

### HelpChat PlaceholderAPI

If using HelpChat PlaceholderAPI:

#### Installation

1. Download HelpChat PlaceholderAPI
2. Place in `server/mods/`
3. Restart server

#### Configuration

MysticNameTags auto-detects HelpChat. To override:
```json
{
  "helpchPlaceholderApiAutoDetect": false,
  "helpchPlaceholderApiEnabled": true
}
```

#### Available Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|-----------------|
| `%mystictags_tag%` | Player's equipped tag with color | `[Mystic]` |
| `%mystictags_tag_plain%` | Player's equipped tag (plain text) | `[Mystic]` |
| `%mystictags_full%` | Full formatted name (rank + name + tag) | `[Admin] PlayerName [VIP]` |

#### Usage in Chat Plugins

Example with a chat formatting plugin:

```json
{
  "chat_format": "%lp_prefix% %player_name% %mystictags_tag% &7: &f%message%"
}
```

---

## Integration Setup

### RPG Leveling Integration

To use MysticNameTags with RPG Leveling:

#### 1. Disable RPG Leveling Nameplates

In RPG Leveling's `config.json`:

```json
{
  "EnablePlayerLevelNameplate": false
}
```

#### 2. Enable in MysticNameTags

In `settings.json`:

```json
{
  "rpgLevelingNameplatesEnabled": true,
  "rpgLevelingRefreshSeconds": 30
}
```

The `rpgLevelingRefreshSeconds` controls how often RPG level data is refreshed (minimum: 5 seconds).

#### 3. Update Nameplate Format (Optional)

In `settings.json`:

```json
{
  "nameplateFormat": "{rank} {name} {rpg_level} {tag}"
}
```

#### 4. Reload

```bash
/tags reload
```

Restart server if reload doesn't work.

### Endless Leveling Integration (v1.1.2+)

For Endless Leveling systems:

#### 1. Verify Endless Leveling is Installed

```bash
/endless info
```

#### 2. Enable Integration

In `settings.json`:

```json
{
  "endlessLevelingNameplatesEnabled": true,
  "endlessRaceDisplay": true,
  "endlessPrestigeDisplay": true,
  "endlessPrimaryClassDisplay": true,
  "endlessSecondaryClassDisplay": true,
  "endlessPrestigePrefix": "P"
}
```

#### 3. Update Nameplate Format (Optional)

```json
{
  "nameplateFormat": "{rank} {endless_prestige} {name} {endless_race} {endless_primary_class} {tag}"
}
```

#### 4. Safe Operation

This integration:
- Prevents double-writing of nameplates
- Avoids flickering effects
- Preserves all level and race data display
- Auto-disables if Endless Leveling is not installed

#### 5. Reload Configuration

```bash
/tags reload
```

---

## Playtime Tracking Setup (Optional)

MysticNameTags can track playtime for tag unlock requirements.

### Playtime Providers

| Provider | Setting | Notes |
|----------|---------|-------|
| **Auto-Detect** | `"AUTO"` | Automatically selects best available provider |
| **Internal** | `"INTERNAL"` | Built-in tracking (stores in database) |
| **ZIB Playtime** | `"ZIB_PLAYTIME"` | ZIB plugin integration |
| **Disabled** | `"NONE"` | No playtime tracking |

### Configuration

In `settings.json`:

```json
{
  "playtimeProvider": "AUTO",
  "ownedTagsCommandEnabled": true
}
```

### Using Playtime Requirements

In `tags.json`:

```json
{
  "id": "veteran",
  "display": "&#DAA520[Veteran]",
  "permission": "mysticnametags.tag.veteran",
  "price": 0,
  "purchasable": false,
  "category": "Achievements",
  "requiredPlaytimeMinutes": 10080,
  "description": "Requires 7 days of playtime"
}
```

The `requiredPlaytimeMinutes` value is in minutes:
- 60 = 1 hour
- 1440 = 24 hours (1 day)
- 10080 = 7 days
- 43200 = 30 days

### Viewing Owned Tags

If `ownedTagsCommandEnabled: true`, players can use:

```bash
/tags owned
```

This shows all tags they have unlocked/purchased.

---

## Verification & Testing

### Step 1: Verify Plugin Loaded

```bash
# Check plugin status
/plugins

# Output should include: "MysticNameTags v1.2.3"
```

### Step 2: Check Initialization

```bash
# Look at console for startup messages
tail -f /path/to/server/logs/latest.log | grep "MysticNameTags"

# Expected output:
# [MysticNameTags] Initializing MysticNameTags v1.2.3...
# [MysticNameTags] Loaded configuration from settings.json
# [MysticNameTags] Loaded X custom tags from tags.json
# [MysticNameTags] Permission backend initialized: LuckPerms
```

### Step 3: Test Permissions

```bash
# Grant yourself admin permission
/lp user <your_username> permission set mysticnametags.ui.open true

# Or use native permissions
/permission grant <username> mysticnametags.ui.open
```

### Step 4: Test Tag UI

```bash
# Grant yourself a tag permission
/lp user <your_username> permission set mysticnametags.tag.mystic true

# Open tag UI
/tags

# Should show available tags
```

### Step 5: Equip a Tag

```bash
# In the /tags UI, click on "Mystic" tag
# Your nameplate should update in-game
```

### Step 6: Check Admin Dashboard

```bash
# Open admin dashboard (requires mysticnametags.ui.open)
/mntag

# Verify:
# - Plugin version displayed
# - All tags listed
# - Permission backend shown
# - Economy status visible
# - Placeholder detection results shown
```

### Step 7: Check Logs for Errors

```bash
# Review server logs for warnings
grep -i "warning\|error" /path/to/server/logs/latest.log | grep -i mystic

# Should be minimal - only config notes
```

---

## Troubleshooting

### Plugin Not Loading

**Problem:** Plugin doesn't appear in `/plugins` list

**Solutions:**

1. **Verify JAR location:**
   ```bash
   ls -la /path/to/server/mods/MysticNameTags*.jar
   ```

2. **Check file permissions:**
   ```bash
   chmod 644 /path/to/server/mods/MysticNameTags-*.jar
   ```

3. **Check server logs for errors:**
   ```bash
   grep -i "mystic\|error" /path/to/server/logs/latest.log | head -50
   ```

4. **Verify Java version:**
   ```bash
   java -version
   # Must be Java 25 or higher
   ```

5. **Verify JAR integrity:**
   ```bash
   jar tf /path/to/server/mods/MysticNameTags-*.jar | head
   # Should show: com/mystichorizons/mysticnametags/...
   ```

### No Tags Appearing in UI

**Problem:** `/tags` opens but shows no tags

**Solutions:**

1. **Check tags.json syntax:**
   - Validate JSON at [JSONLint.com](https://jsonlint.com)
   - Ensure all required fields present: `id`, `display`, `permission`, `price`, `purchasable`, `category`

2. **Verify permissions are granted:**
   ```bash
   /lp user <username> permission info
   # Should show tag permissions: mysticnametags.tag.*
   ```

3. **Check for typos in tag IDs:**
   - Ensure permission node matches tag ID
   - Example: `"id": "vip"` → `"permission": "mysticnametags.tag.vip"`

4. **Check console for parse errors:**
   ```bash
   grep -i "tag\|error\|json" /path/to/server/logs/latest.log | grep -i "mystic"
   ```

5. **Reload configuration:**
   ```bash
   /tags reload
   # Check logs for parse errors
   ```

### Nameplate Not Updating

**Problem:** Tag equipped but nameplate doesn't change

**Solutions:**

1. **Verify nameplates are enabled:**
   ```json
   {
     "nameplatesEnabled": true
   }
   ```

2. **Re-equip the tag or rejoin:**
   - Unequip tag: `/tags` → click tag → unequip
   - Equip tag: `/tags` → click tag → equip
   - Or rejoin the server

3. **Check for integration conflicts:**
   - If using RPG Leveling, verify:
     ```json
     {
       "rpgLevelingNameplatesEnabled": true,
       "rpgLevelingRefreshSeconds": 30
     }
     ```
   - If using Endless Leveling, verify:
     ```json
     {
       "endlessLevelingNameplatesEnabled": true
     }
     ```

4. **Verify nameplate format:**
   ```json
   {
     "nameplateFormat": "{rank} {name} {tag}"
   }
   ```

5. **Review logs for warnings:**
   ```bash
   grep -i "nameplate" /path/to/server/logs/latest.log
   ```

### Economy Tags Not Working

**Problem:** Purchasable tags show but can't buy

**Solutions:**

1. **Verify economy plugin is installed:**
   ```bash
   /economy info
   # or check installed plugins
   ```

2. **Check economy is enabled:**
   ```json
   {
     "economySystemEnabled": true
   }
   ```

3. **Verify player has sufficient funds:**
   ```bash
   /balance <player>
   ```

4. **Ensure tag is marked purchasable:**
   ```json
   {
     "id": "vip",
     "purchasable": true,
     "price": 50000
   }
   ```

5. **Check dashboard for economy status:**
   ```bash
   /mntag
   # Look at Integrations tab
   # Should show which economy is detected
   ```

6. **Review logs for economy errors:**
   ```bash
   grep -i "economy\|transaction\|error" /path/to/server/logs/latest.log | grep -i "mystic"
   ```

### Placeholder Not Working

**Problem:** Chat placeholders show as literal text

**Solutions:**

1. **Verify PlaceholderAPI is installed:**
   ```bash
   /plugins
   # Should show PlaceholderAPI plugin
   ```

2. **Check auto-detection in logs:**
   ```bash
   grep -i "placeholder" /path/to/server/logs/latest.log | grep -i "mystic"
   ```

3. **Verify enabled in settings:**
   ```json
   {
     "wiFlowPlaceholdersAutoDetect": true,
     "helpchPlaceholderApiAutoDetect": true
   }
   ```

4. **Restart server** (PlaceholderAPI requires full restart, not just reload)

5. **Use correct placeholder syntax:**
   - HelpChat: `%mystictags_tag%`
   - WiFlow: `{mystictags_tag}`

6. **Verify plugin using placeholders:**
   - Check that chat plugin supports your PlaceholderAPI version
   - Verify placeholder is registered in chat format

### Permission Not Working

**Problem:** Players can see tags but can't equip

**Solutions:**

1. **Verify permission node:**
   ```bash
   # Check player's permissions
   /lp user <player> permission info | grep mystic
   ```

2. **Verify permission is granted:**
   ```bash
   /lp user <player> permission set mysticnametags.tag.vip true
   # Or check group
   /lp user <player> group list
   /lp group <group> permission set mysticnametags.tag.vip true
   ```

3. **Check permission node case:**
   - Must be lowercase: `mysticnametags.tag.vip`
   - Not: `MysticNameTags.tag.VIP`

4. **Check permission gate settings:**
   ```json
   {
     "fullPermissionGate": false,
     "permissionGate": false
   }
   ```

5. **Review logs for permission errors:**
   ```bash
   grep -i "permission\|denied\|access" /path/to/server/logs/latest.log | grep -i "mystic"
   ```

### Storage Issues

**Problem:** Tag ownership not saved between restarts

**Solutions:**

1. **Check storage backend:**
   ```bash
   /tagsadmin storage
   ```

2. **Verify storage files exist:**
   ```bash
   # For FILE backend
   ls -la config/MysticNameTags/playerdata/
   
   # For SQLITE
   ls -la config/MysticNameTags/playerdata.db
   ```

3. **Test with admin command:**
   ```bash
   /tagsadmin givetag <player> <tag_id>
   # Player should own tag after restart
   ```

4. **Check storage permissions:**
   ```bash
   chmod 755 config/MysticNameTags/
   chmod 755 config/MysticNameTags/playerdata/
   ```

5. **Review logs for storage errors:**
   ```bash
   grep -i "storage\|database\|file\|error" /path/to/server/logs/latest.log | grep -i "mystic"
   ```

---

## Admin Commands

### Tag Management

```bash
# Reload configuration without restart
/tags reload

# Give a tag to a player (direct, bypasses payment)
/tagsadmin givetag <player> <tag_id>

# Remove a specific tag from player
/tagsadmin removetag <player> <tag_id>

# Reset all tags for a player
/tagsadmin reset <player>

# Open tag UI for another player
/tagsadmin open <player>
```

### Debug & Troubleshooting

```bash
# Check which storage backend is active
/tagsadmin storage

# Get detailed storage diagnostics
/tagsadmin debugstorage

# Generate debug snapshot (for bug reports)
/mntag
# Then click "Debug Snapshot" button
```

### Help & Info

```bash
# Show all tag commands
/tags help

# Show plugin info & integrations
/tags info

# Open dashboard
/mntag

# Open tag selector
/tags

# View owned tags
/tags owned
```

---

## Next Steps

After successful installation:

1. **Customize tags** for your server theme in `tags.json`
2. **Set up economy pricing** for premium tags
3. **Configure integrations** needed for your server setup
4. **Grant permissions** to players and groups
5. **Test all features** before public release
6. **Monitor logs** for any warnings or errors

---

## Support

For issues or questions:

1. Check the [troubleshooting section](#troubleshooting) above
2. Review [GitHub Issues](https://github.com/L8-Alphine/MysticNametags/issues)
3. Open a new issue with:
   - Server version & Java version
   - Plugin version
   - Debug snapshot output
   - Relevant logs

---

**MysticNameTags v1.2.3** | Built for Hytale | [GitHub](https://github.com/L8-Alphine/MysticNametags)
