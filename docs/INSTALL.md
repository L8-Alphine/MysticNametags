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
| **Endless Leveling** | Advanced leveling integration | Optional |

---

## Installation Methods

### Method 1: Pre-Built JAR (Recommended for Users)

This is the fastest way to get started without compiling code.

#### Step 1: Download the Plugin

1. Navigate to the [MysticNameTags GitHub Releases](https://github.com/L8-Alphine/MysticNametags/releases)
2. Download the latest `MysticNameTags-x.x.x.jar` file
3. Verify the version matches your server requirements (v1.0.1 or later)

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
[MysticNameTags] Permission backend initialized: [LuckPerms|PermissionsPlus|Native]
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
1. Download dependencies from Maven repositories
2. Compile Java source code
3. Process resources
4. Generate glyph slot models
5. Create a shadow JAR with all dependencies bundled

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

After installation, MysticNameTags creates two essential config files on first startup.

### Config File Location

```
<server-directory>/
├── config/
│   ├── MysticNameTags/
│   │   ├── settings.json       (Master settings)
│   │   ├── tags.json           (Custom tag definitions)
│   │   └── language.json       (Localization - future)
```

### Initial Configuration Files

#### 1. `settings.json` - Master Configuration

**First startup (auto-generated):**

```json
{
  "nameplatesEnabled": true,
  "defaultTagEnabled": false,
  "defaultTagId": "mystic",
  "rpgLevelingNameplatesEnabled": false,
  "endlessLevelingNameplatesEnabled": false,
  "economyEnabled": true,
  "placeholderApiEnabled": true,
  "debug": false
}
```

**Configuration Reference:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `nameplatesEnabled` | Boolean | `true` | Master toggle for nameplate system |
| `defaultTagEnabled` | Boolean | `false` | Auto-apply default tag to players without selection |
| `defaultTagId` | String | `"mystic"` | Tag ID to use as default (must exist in tags.json) |
| `rpgLevelingNameplatesEnabled` | Boolean | `false` | Enable RPG Leveling integration |
| `endlessLevelingNameplatesEnabled` | Boolean | `false` | Enable Endless Leveling integration |
| `economyEnabled` | Boolean | `true` | Enable economy/purchasable tags |
| `placeholderApiEnabled` | Boolean | `true` | Enable PlaceholderAPI support |
| `debug` | Boolean | `false` | Enable debug logging (verbose) |

**How to Edit:**
1. Stop the server
2. Edit `config/MysticNameTags/settings.json`
3. Restart the server, or use `/tags reload`

#### 2. `tags.json` - Tag Definitions

**First startup (auto-generated with examples):**

```json
{
  "tags": [
    {
      "id": "mystic",
      "display": "&#8A2BE2&l[Mystic]",
      "permission": "mysticnametags.tag.mystic",
      "price": 0,
      "purchasable": false,
      "category": "Special"
    },
    {
      "id": "legendary",
      "display": "&#FFD700&l[Legendary]",
      "permission": "mysticnametags.tag.legendary",
      "price": 50000,
      "purchasable": true,
      "category": "Premium"
    },
    {
      "id": "vip",
      "display": "&#00FF00&l[VIP]",
      "permission": "mysticnametags.tag.vip",
      "price": 0,
      "purchasable": false,
      "category": "Ranks"
    }
  ]
}
```

**Tag Definition Reference:**

| Field | Type | Required | Example | Description |
|-------|------|----------|---------|-------------|
| `id` | String | Yes | `"mystic"` | Unique identifier (alphanumeric, no spaces) |
| `display` | String | Yes | `"&#8A2BE2[Mystic]"` | Display text with color codes |
| `permission` | String | Yes | `"mysticnametags.tag.mystic"` | Permission node required to access tag |
| `price` | Number | Yes | `50000` | Cost in economy currency (0 = free) |
| `purchasable` | Boolean | Yes | `true` | Whether tag can be bought with economy |
| `category` | String | Yes | `"Premium"` | UI category grouping |

**Color Format Support:**

- **Legacy Codes:** `&0` - `&f` for standard Minecraft colors
  - `&l` = Bold, `&o` = Italic, `&n` = Underline
  - Example: `&4&l[ADMIN]` = Bold red text
  
- **Hex Colors:** `&#RRGGBB` for custom colors
  - Example: `&#FF5733[Custom]` = Orange text
  - Uses 6-digit hex color codes

**Example Tag Configurations:**

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
      "permission": "mysticnametags.tag.builder",
      "price": 0,
      "purchasable": false,
      "category": "Staff"
    },
    {
      "id": "gold_member",
      "display": "&#FFD700[Gold]",
      "permission": "mysticnametags.tag.gold",
      "price": 100000,
      "purchasable": true,
      "category": "Memberships"
    },
    {
      "id": "emerald_member",
      "display": "&#50C878[Emerald]",
      "permission": "mysticnametags.tag.emerald",
      "price": 250000,
      "purchasable": true,
      "category": "Memberships"
    }
  ]
}
```

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
```

#### 3. Default Tag Permission (Optional)

```bash
# If using default tags, grant the permission for default tag
/lp group default permission set mysticnametags.tag.mystic true
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
```

---

## Economy System Setup

### Supported Economy Plugins

MysticNameTags supports multiple economy systems with automatic detection:

1. **TheEconomy** (Recommended - best performance)
2. **EcoTale**
3. **VaultUnlocked**
4. **EliteEssentials**

### Economy Plugin Priority

MysticNameTags checks for economy plugins in this order:
```
TheEconomy → EcoTale → VaultUnlocked → EliteEssentials
```

The first detected plugin is used. Ensure only one economy plugin is active for consistent pricing.

### Install an Economy Plugin

**Example: Installing TheEconomy**

1. Download the latest TheEconomy JAR
2. Place in `server/mods/` directory
3. Restart server
4. Verify installation:
   ```bash
   /economy info
   ```
5. Enable purchasable tags in `settings.json`:
   ```json
   {
     "economyEnabled": true
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

### HelpChat PlaceholderAPI

If using HelpChat PlaceholderAPI:

#### Installation

1. Download HelpChat PlaceholderAPI
2. Place in `server/mods/`
3. Restart server

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

### WiFlow PlaceholderAPI

If using WiFlow PlaceholderAPI:

#### Available Placeholders

| Placeholder | Description | Example Output |
|-------------|-------------|-----------------|
| `{mystictags_tag}` | Colored active tag | `[Mystic]` |
| `{mystictags_tag_plain}` | Plain active tag | `[Mystic]` |
| `{mystictags_full}` | Full formatted name | `[Admin] PlayerName [VIP]` |
| `{mystictags_full_plain}` | Plain full name | `[Admin] PlayerName [VIP]` |

#### Usage

Example in WiFlow configuration:

```json
{
  "format": "{mystictags_full} {message}"
}
```

---

## Integration Setup

### RPG Leveling Integration (Optional)

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
  "rpgLevelingNameplatesEnabled": true
}
```

#### 3. Reload

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
  "endlessLevelingNameplatesEnabled": true
}
```

#### 3. Safe Operation

This integration:
- Prevents double-writing of nameplates
- Avoids flickering effects
- Preserves level data display
- Auto-disables if Endless Leveling is not present

#### 4. Reload Configuration

```bash
/tags reload
```

---

## Verification & Testing

### Step 1: Verify Plugin Loaded

```bash
# Check plugin status
/plugins

# Output should include: "MysticNameTags v1.2.3"
```

### Step 2: Test Tag UI

```bash
# As an admin or player with permission
/tags

# Should open an in-game UI showing available tags
```

### Step 3: Grant Test Permissions

```bash
# Grant your test account a tag
/lp user <your_username> permission set mysticnametags.tag.mystic true
```

### Step 4: Equip a Tag

```bash
# Open tag UI
/tags

# Click on "Mystic" tag
# Your nameplate should update in-game
```

### Step 5: Check Admin Dashboard

```bash
# Open admin dashboard (requires mysticnametags.ui.open)
/tags admin

# Verify:
# - All tags are listed
# - Permission visibility matches expectations
# - No error messages
```

### Step 6: Check Logs for Errors

```bash
# Review server logs
tail -f /path/to/server/logs/latest.log

# Look for:
# ✓ "[MysticNameTags] Loaded X tags"
# ✓ "[MysticNameTags] Permission backend initialized"
# ✗ Avoid errors like "Permission backend not found"
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
   grep -i "mystic\|error" /path/to/server/logs/latest.log
   ```

4. **Verify Java version:**
   ```bash
   java -version
   # Must be Java 25 or higher
   ```

### No Tags Appearing in UI

**Problem:** `/tags` opens but shows no tags

**Solutions:**

1. **Check tags.json syntax:**
   - Validate JSON at [JSONLint.com](https://jsonlint.com)
   - Ensure all required fields are present

2. **Verify permissions are granted:**
   ```bash
   /lp user <username> permission info
   # Should show tag permissions: mysticnametags.tag.*
   ```

3. **Check for typos in tag IDs:**
   - Ensure permission node matches tag ID
   - Example: `id: "vip"` → `permission: "mysticnametags.tag.vip"`

4. **Reload configuration:**
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
   - Unequip tag: `/tags`
   - Equip tag: `/tags` → click tag
   - Or rejoin the server

3. **Check for integration conflicts:**
   - If using RPG Leveling, verify integration is properly configured
   - If using Endless Leveling, ensure `endlessLevelingNameplatesEnabled` is correct

4. **Review logs for warnings:**
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
     "economyEnabled": true
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

### Placeholder Not Working

**Problem:** Chat placeholders show as literal text

**Solutions:**

1. **Verify PlaceholderAPI is installed:**
   ```bash
   /papi info
   # or check installed plugins
   ```

2. **Enable in settings.json:**
   ```json
   {
     "placeholderApiEnabled": true
   }
   ```

3. **Restart server** (PlaceholderAPI requires full restart)

4. **Use correct placeholder syntax:**
   - HelpChat: `%mystictags_tag%`
   - WiFlow: `{mystictags_tag}`

5. **Verify plugin using placeholders supports your PlaceholderAPI version:**
   - Check chat plugin compatibility

---

## Next Steps

After successful installation:

1. **Customize tags** for your server theme
2. **Set up economy pricing** for premium tags
3. **Configure integrations** needed for your server
4. **Grant permissions** to players/groups
5. **Test all features** before public release

For more information:
- [Configuration Guide](./CONFIG.md) - Detailed settings reference
- [Commands Reference](./COMMANDS.md) - Available commands
- [Permissions Reference](./PERMISSIONS.md) - Complete permission nodes
- [FAQ](./FAQ.md) - Frequently asked questions

---

## Support

For issues or questions:

1. Check the [troubleshooting section](#troubleshooting) above
2. Review [GitHub Issues](https://github.com/L8-Alphine/MysticNametags/issues)
3. Open a new issue if your problem isn't covered

---

**MysticNameTags v1.2.3** | Built for Hytale | [GitHub](https://github.com/L8-Alphine/MysticNametags)