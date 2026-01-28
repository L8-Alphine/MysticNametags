# MysticNameTags

**MysticNameTags** is a Hytale server plugin that adds a fully permission-driven **player tag system** with an in-game UI, LuckPerms integration, optional PlaceholderAPI support, and future-proof extensibility.

Designed for performance, modularity, and large servers.

---

## ✨ Features

- 🏷️ Custom player tags with UI selection
- 🔐 Permission-based access using **LuckPerms**
- 🧩 Optional **PlaceholderAPI** integration
- 🎨 UI color support using `&` and hex colors
- ⚙️ Optimized nameplate rebuilding (no ticking tasks)
- 🧱 Modular, developer-friendly architecture

---

## 📦 Requirements

### **Required**
- **LuckPerms**  
  https://www.curseforge.com/hytale/mods/luckperms

- **VaultUnlocked**  
  https://www.curseforge.com/hytale/mods/vaultunlocked

> These are **mandatory**. MysticNameTags will not function correctly without them.

### **Optional**
- **PlaceholderAPI (CreeperFace)**  
  https://www.curseforge.com/hytale/mods/placeholderapi

---

## 🖥️ Commands

| Command | Description |
|-------|------------|
| `/tags` | Opens the tag selection UI |
| `/tags tags` | Opens the tag list directly |

> A reload command is **planned**, but currently **server restart is required** after config changes.

---

## 🎨 Coloring Support

### UI
- Supports:
  - `&` color codes
  - Hex colors (e.g. `&#8A2BE2`)
- Colors are shown correctly in the tag UI preview

### Nameplates
- ❌ **Coloring is NOT supported** by the current Hytale Nameplate API
- Nameplates are displayed as **plain text**
- LuckPerms prefixes are applied automatically when no tag is equipped

---

## 🔐 Permissions

- **All tags require permissions**
- This includes **free tags**
- Permissions are defined per-tag inside `tags.json`

Example:
```json
{
  "id": "mystic",
  "display": "&#8A2BE2&l[Mystic]",
  "permission": "mysticnametags.tag.mystic",
  "price": 0,
  "purchasable": false
}
````

Permissions are enforced using **LuckPerms only**.

---

## 🧩 PlaceholderAPI Support (Optional)

If PlaceholderAPI is installed, MysticNameTags registers the following placeholders:

| Placeholder              | Description                             |
| ------------------------ | --------------------------------------- |
| `%mystictags_tag%`       | Colored active tag                      |
| `%mystictags_tag_plain%` | Plain tag (no colors)                   |
| `%mystictags_full%`      | Full formatted name (rank + name + tag) |

> Placeholder availability depends on PlaceholderAPI scope support.

---

## ⚙️ Performance & Safety

* 🚫 No repeating tasks
* 🚫 No polling loops
* ✅ Nameplates rebuild **only when needed**:

    * Player joins
    * Tag changes
    * LuckPerms data recalculates
* ✅ World-thread safe updates
* ✅ Does **not** interfere with teleport plugins
  (Tested with **EliteEssentials**)

---

## 🧪 Compatibility

Tested with:

* LuckPerms
* VaultUnlocked
* PlaceholderAPI
* EliteEssentials

Safe to run alongside other chat, scoreboard, and utility plugins.

---

## 🚧 In Development / Planned

* `/tags reload` command
* BetterScoreboard integration
* Player list (tab) tag support
* Expanded PlaceholderAPI usage
* Public Developer API
* More UI customization options

---

## 📄 License

This project is licensed under the **GNU General Public License v3 (GPL-3.0)**.

* You may use, modify, and redistribute this plugin
* Any redistributed versions **must remain open-source**
* Proper credit is required

See the `LICENSE` file for full details.

---

## 💬 Support & Contributions

* Issues & suggestions: **GitHub Issues**
* Contributions are welcome via pull requests
* Please keep changes consistent with the existing architecture

---

## ❤️ Credits

Developed by **MysticHorizons**
Built for the Hytale modding community