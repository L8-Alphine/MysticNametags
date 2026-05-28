# MysticNameTags Upgrade Notes

These notes cover the recent tag coloring, glyph nameplate self-view toggle,
UI preview cleanup, and Endless Leveling integration upgrade.

## Summary

This upgrade focuses on three areas:

- Color formatting now flows through one shared pipeline for chat, UI previews,
  placeholders, and glyph nameplates.
- Players can hide only their own packet glyph nameplate for third-person camera
  comfort.
- Endless Leveling integration now uses the public API to coordinate nameplates
  and refresh MysticNameTags output when Endless Leveling state changes.

## Tag Coloring Upgrade

Tags now support the existing legacy color formats plus a small MiniMessage
subset.

Supported display formats:

```json
{
  "id": "legacy_hex",
  "display": "&#FF5555&l[Slayer I]",
  "description": "&7A red Slayer tag.",
  "price": 0,
  "purchasable": false,
  "permission": "mysticnametags.tag.slayer_1",
  "category": "Combat"
}
```

```json
{
  "id": "gradient_mystic",
  "display": "<bold><gradient:#8A2BE2:#00E5FF:#FF55FF>[Mystic]</gradient></bold>",
  "description": "&7A shimmering gradient title.",
  "price": 0,
  "purchasable": false,
  "permission": "mysticnametags.tag.gradient_mystic",
  "category": "Special"
}
```

Supported MiniMessage subset:

- `<#RRGGBB>`
- named colors such as `<red>`, `<gold>`, `<aqua>`, and `<white>`
- `<bold>`
- `<italic>`
- `<reset>`
- `<gradient:#RRGGBB:#RRGGBB[:#RRGGBB...]>text</gradient>`

Unsupported MiniMessage features:

- `<rainbow>`
- click events
- hover events
- full Adventure MiniMessage syntax

## Where Colors Apply

Color formatting is now handled separately for each target:

- Chat and placeholders keep compact color markup where supported.
- Custom UI labels strip raw formatting from text values and apply a single
  representative `TextColor` when that UI field supports it.
- Glyph nameplates preserve per-segment color data for tint lookup.
- Native fallback nameplates remain plain text because that path is limited by
  the Hytale nameplate API.

Important UI note: a single Hytale Custom UI label cannot render multiple text
colors inside the same label. Full nameplate previews are shown as clean visible
text instead of leaking raw `&#RRGGBB` codes. Individual tag labels still use a
representative color.

## Glyph Nameplate Self-View Toggle

Players can now toggle only their own packet glyph nameplate visibility:

```text
/mnametags nameplate off
/mnametags nameplate on
/mnametags nameplate toggle
/mnametags nameplate status
```

This is intended for third-person camera comfort. When disabled, the player's
own view no longer receives their packet glyph nameplate. Other players still
see that player's nameplate normally.

Stored player data now includes:

```json
{
  "ownNameplateVisible": true
}
```

Existing player data defaults to visible, so this upgrade does not hide any
nameplates until a player toggles the setting.

## Glyph System Scope

The glyph system changes for this upgrade are intentionally limited to:

- color parsing/normalization for glyph text
- tint effect lookup for glyph colors
- self-view visibility toggle and cleanup

The packet glyph attachment/movement model is not changed by this upgrade.

## Endless Leveling Integration Upgrade

MysticNameTags now integrates with Endless Leveling through its public API.

When enabled:

- MysticNameTags asks Endless Leveling to suppress its native player nameplates.
- MysticNameTags becomes the nameplate writer for the player display.
- Endless Leveling data is read through `EndlessLevelingAPI` and `PlayerSnapshot`
  when available.
- MysticNameTags refreshes a player's nameplate when Endless Leveling state
  changes.
- On shutdown or when the integration is disabled, MysticNameTags attempts to
  restore Endless Leveling's original player nameplate setting.

Enable it in `settings.json`:

```json
{
  "endlessLevelingNameplatesEnabled": true
}
```

Optional Endless Leveling display toggles:

```json
{
  "endlessRaceDisplay": true,
  "endlessPrestigeDisplay": true,
  "endlessPrimaryClassDisplay": true,
  "endlessSecondaryClassDisplay": true,
  "endlessPrestigePrefix": "P"
}
```

## Endless Leveling Format Tokens

The normal `nameplateFormat` can include Endless Leveling tokens:

```json
{
  "nameplateFormat": "{rank} {name} {tag}\\nLv.{endless_level} {endless_prestige} {endless_race} {endless_primary_class} {endless_secondary_class}"
}
```

Available tokens:

| Token | Description |
| --- | --- |
| `{endless_level}` | Endless Leveling player level |
| `{endless_prestige}` | Prestige text, controlled by `endlessPrestigeDisplay` |
| `{endless_race}` | Race id/text, controlled by `endlessRaceDisplay` |
| `{endless_primary_class}` | Primary class id/text, controlled by `endlessPrimaryClassDisplay` |
| `{endless_secondary_class}` | Secondary class id/text, controlled by `endlessSecondaryClassDisplay` |

Disabled optional tokens resolve to empty text.

## Upgrade Checklist

1. Back up `settings.json`, `tags.json`, and `playerdata`.
2. Update the plugin jar.
3. Reload or restart the server.
4. Confirm `settings.json` includes any new keys generated by MysticNameTags.
5. If using Endless Leveling nameplates, set:

```json
{
  "endlessLevelingNameplatesEnabled": true
}
```

6. Update `nameplateFormat` if you want Endless Leveling tokens visible.
7. Test one legacy hex tag and one MiniMessage gradient tag.
8. Use `/mnametags nameplate status` to confirm the self-view toggle state.

## Example Combined Configuration

```json
{
  "nameplatesEnabled": true,
  "nameplateFormat": "{rank} {name}\\n{tag} Lv.{endless_level}",
  "endlessLevelingNameplatesEnabled": true,
  "endlessRaceDisplay": false,
  "endlessPrestigeDisplay": true,
  "endlessPrimaryClassDisplay": false,
  "endlessSecondaryClassDisplay": false,
  "endlessPrestigePrefix": "P",
  "experimentalGlyphNameplatesEnabled": true
}
```

## Troubleshooting

If MiniMessage appears as raw text:

- Confirm the tag uses the supported subset listed above.
- Confirm gradient tags are closed with `</gradient>`.
- Run `/mnametags reload`.

If a player's own nameplate blocks third-person view:

- Run `/mnametags nameplate off`.
- Run `/mnametags nameplate on` to restore it.

If Endless Leveling data does not appear:

- Confirm Endless Leveling is installed and loaded.
- Confirm `endlessLevelingNameplatesEnabled` is `true`.
- Confirm the `nameplateFormat` contains Endless Leveling tokens.
- Check the server log for `EndlessLeveling API not detected`.
