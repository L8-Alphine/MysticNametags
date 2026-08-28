package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.license.MysticNameTagsLicense;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerAssetManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class TagConfigValidator {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final Type TAG_LIST_TYPE = new TypeToken<List<TagDefinition>>() {}.getType();
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final Set<String> PLACEHOLDER_OPERATORS = Set.of(
            "true", "false", "==", "!=", ">", ">=", "<", "<=", "contains"
    );
    private static final Set<String> HIGH_RISK_COMMANDS = Set.of(
            "stop", "restart", "reload", "op", "deop", "ban", "kick", "pardon"
    );

    private TagConfigValidator() {
    }

    @Nonnull
    public static Report validateDefault(@Nullable Settings settings,
                                         @Nullable IntegrationManager integrations) {
        File tagsFile = resolveTagsFile();
        return validate(tagsFile, settings, integrations);
    }

    @Nonnull
    public static Report validate(@Nonnull File tagsFile,
                                  @Nullable Settings settings,
                                  @Nullable IntegrationManager integrations) {
        Report report = new Report(tagsFile);

        if (!tagsFile.exists()) {
            report.add(Severity.ERROR, "tags.json", "File does not exist.");
            return report;
        }
        if (!tagsFile.isFile()) {
            report.add(Severity.ERROR, "tags.json", "Path is not a file.");
            return report;
        }
        if (!tagsFile.canRead()) {
            report.add(Severity.ERROR, "tags.json", "File is not readable.");
            return report;
        }
        if (tagsFile.length() == 0L) {
            report.add(Severity.ERROR, "tags.json", "File is empty.");
            return report;
        }

        List<TagDefinition> definitions;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(tagsFile),
                StandardCharsets.UTF_8
        )) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                report.add(Severity.ERROR, "tags.json", "Root value must be a JSON array of tag definitions.");
                return report;
            }
            definitions = GSON.fromJson(root, TAG_LIST_TYPE);
        } catch (Exception e) {
            report.add(Severity.ERROR, "tags.json", "Could not parse JSON: " + e.getMessage());
            return report;
        }

        if (definitions == null) {
            report.add(Severity.ERROR, "tags.json", "Parsed file was null.");
            return report;
        }

        report.rawTagCount = definitions.size();
        validateDefinitions(definitions, settings, integrations, report);
        return report;
    }

    @Nonnull
    private static File resolveTagsFile() {
        com.mystichorizons.mysticnametags.MysticNameTagsPlugin plugin =
                com.mystichorizons.mysticnametags.MysticNameTagsPlugin.getInstance();
        File dataFolder = plugin.getDataDirectory().toFile();
        return new File(dataFolder, "tags.json");
    }

    private static void validateDefinitions(@Nonnull List<TagDefinition> definitions,
                                            @Nullable Settings settings,
                                            @Nullable IntegrationManager integrations,
                                            @Nonnull Report report) {
        Map<String, TagDefinition> byId = new LinkedHashMap<>();
        Map<String, Integer> firstIndexById = new LinkedHashMap<>();
        Set<String> categories = new LinkedHashSet<>();

        for (int i = 0; i < definitions.size(); i++) {
            TagDefinition def = definitions.get(i);
            String path = "tags[" + i + "]";

            if (def == null) {
                report.add(Severity.ERROR, path, "Entry is null.");
                continue;
            }

            String id = trimToNull(def.getId());
            if (id == null) {
                report.add(Severity.ERROR, path, "Tag id is missing or blank.");
                continue;
            }

            String normalizedId = id.toLowerCase(Locale.ROOT);
            if (byId.containsKey(normalizedId)) {
                Integer firstIndex = firstIndexById.get(normalizedId);
                report.add(Severity.ERROR, path, "Duplicate tag id '" + id + "' also appears at tags[" + firstIndex + "].");
            } else {
                byId.put(normalizedId, def);
                firstIndexById.put(normalizedId, i);
            }

            validateTagShape(def, path + " (" + id + ")", report);
            categories.add(def.getCategory());
        }

        report.uniqueTagCount = byId.size();
        report.categoryCount = categories.size();

        validateReferences(definitions, byId, report);
        validateSettings(settings, integrations, byId, report);
    }

    private static void validateTagShape(@Nonnull TagDefinition def,
                                         @Nonnull String path,
                                         @Nonnull Report report) {
        String id = trimToNull(def.getId());
        if (id != null && !SAFE_ID.matcher(id).matches()) {
            report.add(Severity.WARNING, path, "Tag id contains unusual characters. Prefer letters, numbers, dashes, underscores, dots, or colons.");
        }

        String display = def.getDisplay();
        if (trimToNull(display) == null) {
            report.add(Severity.ERROR, path, "Display text is missing or blank.");
        } else {
            validateFormattedText(display, path, "display", report);
            String plain = ColorFormatter.stripFormatting(ColorFormatter.colorizeCompact(display));
            if (plain == null || plain.trim().isEmpty()) {
                report.add(Severity.WARNING, path, "Display text has no visible characters after formatting is stripped.");
            }
        }

        String description = def.getDescription();
        if (description != null && !description.isBlank()) {
            validateFormattedText(description, path, "description", report);
        }

        if (def.getPrice() < 0.0d) {
            report.add(Severity.ERROR, path, "Price cannot be negative.");
        }
        if (!def.isPurchasable() && def.getPrice() > 0.0d) {
            report.add(Severity.WARNING, path, "Price is set but purchasable=false, so the price will be ignored.");
        }

        String category = trimToNull(def.category);
        if (category == null) {
            report.add(Severity.WARNING, path, "Category is missing; it will default to General.");
        }

        validateStatRequirements(def, path, report);
        validateItemRequirements(def, path, report);
        validatePlaceholderRequirements(def, path, report);
        validateUnlockCommands(def, path, report);
        validateAvailability(def, path, report);
        validateBanner(def, path, report);
    }

    private static void validateBanner(@Nonnull TagDefinition def,
                                       @Nonnull String path,
                                       @Nonnull Report report) {
        if (!def.hasBanner()) {
            return;
        }

        if (!MysticNameTagsLicense.bannersLicensed()) {
            report.add(Severity.INFO, path, "Banner is configured, but tag banners are not licensed on this server ("
                    + MysticNameTagsLicense.service().status().operatorSummary()
                    + "). The tag renders its text display.");
            return;
        }

        Settings settings = Settings.get();
        if (settings != null && !settings.isBannersEnabled()) {
            report.add(Severity.INFO, path, "Banner is configured, but bannersEnabled=false; the tag renders its text display.");
        } else if (settings != null && !settings.isExperimentalGlyphNameplatesEnabled()) {
            report.add(Severity.WARNING, path,
                    "Banner is configured, but experimentalGlyphNameplatesEnabled=false. Banners need glyph nameplates; the tag renders its text display.");
        }

        BannerAssetManager banners = BannerAssetManager.get();
        if (banners == null) {
            return;
        }

        if (!banners.has(def.getBanner())) {
            report.add(Severity.ERROR, path, "Banner '" + def.getBanner()
                    + "' was not found. Put the PNG in the plugin's images/ folder and run /tags reload.");
        }

        if (def.bannerScale != null && def.bannerScale <= 0.0d) {
            report.add(Severity.ERROR, path, "bannerScale must be greater than 0.");
        }
    }

    private static void validateFormattedText(@Nonnull String text,
                                              @Nonnull String path,
                                              @Nonnull String field,
                                              @Nonnull Report report) {
        if (hasMalformedCompactHex(text)) {
            report.add(Severity.ERROR, path, field + " contains a malformed compact hex color. Use &#RRGGBB.");
        }
        if (hasMalformedMiniMessageHex(text)) {
            report.add(Severity.ERROR, path, field + " contains a malformed MiniMessage hex color. Use <#RRGGBB>.");
        }

        int gradientOpen = countOccurrences(text.toLowerCase(Locale.ROOT), "<gradient:");
        int gradientClose = countOccurrences(text.toLowerCase(Locale.ROOT), "</gradient>");
        if (gradientOpen != gradientClose) {
            report.add(Severity.ERROR, path, field + " has mismatched <gradient:...> and </gradient> tags.");
        }
    }

    private static boolean hasMalformedCompactHex(@Nonnull String text) {
        int index = 0;
        while ((index = text.indexOf("&#", index)) >= 0) {
            if (index + 8 > text.length()) {
                return true;
            }

            for (int i = index + 2; i < index + 8; i++) {
                if (!isHex(text.charAt(i))) {
                    return true;
                }
            }

            index += 8;
        }
        return false;
    }

    private static boolean hasMalformedMiniMessageHex(@Nonnull String text) {
        int index = 0;
        while ((index = text.indexOf("<#", index)) >= 0) {
            if (index + 9 > text.length() || text.charAt(index + 8) != '>') {
                return true;
            }

            for (int i = index + 2; i < index + 8; i++) {
                if (!isHex(text.charAt(i))) {
                    return true;
                }
            }

            index += 9;
        }
        return false;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private static void validateStatRequirements(@Nonnull TagDefinition def,
                                                 @Nonnull String path,
                                                 @Nonnull Report report) {
        if (def.usesLegacyStatRequirement()) {
            report.add(Severity.INFO, path, "Uses legacy requiredStatKey/requiredStatValue; reload will migrate it to requiredStats.");
        }

        List<TagDefinition.StatRequirement> statReqs = def.getRequiredStats();
        for (int i = 0; i < statReqs.size(); i++) {
            TagDefinition.StatRequirement req = statReqs.get(i);
            String reqPath = path + ".requiredStats[" + i + "]";
            if (req == null) {
                report.add(Severity.ERROR, reqPath, "Requirement is null.");
                continue;
            }
            if (trimToNull(req.getKey()) == null) {
                report.add(Severity.ERROR, reqPath, "Stat key is missing or blank.");
            }
            if (req.getMin() == null || req.getMin() <= 0) {
                report.add(Severity.ERROR, reqPath, "Minimum value must be greater than 0.");
            }
        }
    }

    private static void validateItemRequirements(@Nonnull TagDefinition def,
                                                 @Nonnull String path,
                                                 @Nonnull Report report) {
        List<TagDefinition.ItemRequirement> itemReqs = def.getRequiredItems();
        for (int i = 0; i < itemReqs.size(); i++) {
            TagDefinition.ItemRequirement req = itemReqs.get(i);
            String reqPath = path + ".requiredItems[" + i + "]";
            if (req == null) {
                report.add(Severity.ERROR, reqPath, "Requirement is null.");
                continue;
            }
            if (trimToNull(req.getItemId()) == null) {
                report.add(Severity.ERROR, reqPath, "Item id is missing or blank.");
            }
            if (req.getAmount() <= 0) {
                report.add(Severity.ERROR, reqPath, "Amount must be greater than 0.");
            }
        }
    }

    private static void validatePlaceholderRequirements(@Nonnull TagDefinition def,
                                                        @Nonnull String path,
                                                        @Nonnull Report report) {
        List<TagDefinition.PlaceholderRequirement> phReqs = def.getPlaceholderRequirements();
        for (int i = 0; i < phReqs.size(); i++) {
            TagDefinition.PlaceholderRequirement req = phReqs.get(i);
            String reqPath = path + ".placeholderRequirements[" + i + "]";
            if (req == null) {
                report.add(Severity.ERROR, reqPath, "Requirement is null.");
                continue;
            }
            if (trimToNull(req.getPlaceholder()) == null) {
                report.add(Severity.ERROR, reqPath, "Placeholder is missing or blank.");
            }
            String operator = trimToNull(req.getOperator());
            if (operator == null) {
                report.add(Severity.ERROR, reqPath, "Operator is missing or blank.");
            } else if (!PLACEHOLDER_OPERATORS.contains(operator.toLowerCase(Locale.ROOT))) {
                report.add(Severity.ERROR, reqPath, "Unsupported operator '" + operator + "'.");
            }
            if (req.getValue() == null) {
                report.add(Severity.ERROR, reqPath, "Value is missing.");
            }
        }
    }

    private static void validateUnlockCommands(@Nonnull TagDefinition def,
                                               @Nonnull String path,
                                               @Nonnull Report report) {
        List<String> commands = def.getOnUnlockCommands();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            String cmdPath = path + ".onUnlockCommands[" + i + "]";
            String normalized = trimToNull(command);
            if (normalized == null) {
                report.add(Severity.ERROR, cmdPath, "Command is missing or blank.");
                continue;
            }

            String firstToken = normalized.startsWith("/")
                    ? normalized.substring(1).trim()
                    : normalized;
            int space = firstToken.indexOf(' ');
            if (space >= 0) {
                firstToken = firstToken.substring(0, space);
            }

            if (HIGH_RISK_COMMANDS.contains(firstToken.toLowerCase(Locale.ROOT))) {
                report.add(Severity.WARNING, cmdPath, "Command starts with high-risk command '" + firstToken + "'. Verify this is intentional.");
            }
        }
    }

    private static void validateAvailability(@Nonnull TagDefinition def,
                                             @Nonnull String path,
                                             @Nonnull Report report) {
        String activeFrom = trimToNull(def.getActiveFrom());
        String activeUntil = trimToNull(def.getActiveUntil());

        Instant from = null;
        Instant until = null;

        if (activeFrom != null) {
            from = TagDefinition.parseAvailabilityInstant(activeFrom, true);
            if (from == null) {
                report.add(Severity.ERROR, path, "activeFrom must be ISO-8601, such as 2026-10-01 or 2026-10-01T00:00:00Z.");
            }
        }

        if (activeUntil != null) {
            until = TagDefinition.parseAvailabilityInstant(activeUntil, false);
            if (until == null) {
                report.add(Severity.ERROR, path, "activeUntil must be ISO-8601, such as 2026-11-01 or 2026-11-01T00:00:00Z.");
            }
        }

        if (from != null && until != null && !until.isAfter(from)) {
            report.add(Severity.ERROR, path, "activeUntil must be after activeFrom.");
        }

        if ((activeFrom != null || activeUntil != null) && !def.isCurrentlyAvailable()) {
            report.add(Severity.INFO, path, "Seasonal tag is currently outside its availability window.");
        }

        if (trimToNull(def.getAvailabilityMessage()) != null && activeFrom == null && activeUntil == null) {
            report.add(Severity.INFO, path, "availabilityMessage is set, but activeFrom/activeUntil are not configured.");
        }
    }

    private static void validateReferences(@Nonnull List<TagDefinition> definitions,
                                           @Nonnull Map<String, TagDefinition> byId,
                                           @Nonnull Report report) {
        for (int i = 0; i < definitions.size(); i++) {
            TagDefinition def = definitions.get(i);
            if (def == null || trimToNull(def.getId()) == null) {
                continue;
            }

            String id = def.getId().trim();
            String normalizedId = id.toLowerCase(Locale.ROOT);
            List<String> requiredOwnedTags = def.getRequiredOwnedTags();

            for (int r = 0; r < requiredOwnedTags.size(); r++) {
                String required = requiredOwnedTags.get(r);
                String reqPath = "tags[" + i + "] (" + id + ").requiredOwnedTags[" + r + "]";
                String normalizedRequired = trimToNull(required);

                if (normalizedRequired == null) {
                    report.add(Severity.ERROR, reqPath, "Required owned tag id is missing or blank.");
                    continue;
                }

                String requiredKey = normalizedRequired.toLowerCase(Locale.ROOT);
                if (requiredKey.equals(normalizedId)) {
                    report.add(Severity.ERROR, reqPath, "Tag cannot require itself.");
                }
                if (!byId.containsKey(requiredKey)) {
                    report.add(Severity.ERROR, reqPath, "Required owned tag '" + normalizedRequired + "' does not exist.");
                }
            }
        }
    }

    private static void validateSettings(@Nullable Settings settings,
                                         @Nullable IntegrationManager integrations,
                                         @Nonnull Map<String, TagDefinition> byId,
                                         @Nonnull Report report) {
        if (settings == null) {
            return;
        }

        if (settings.isDefaultTagEnabled()) {
            String defaultTagId = trimToNull(settings.getDefaultTagId());
            if (defaultTagId == null) {
                report.add(Severity.ERROR, "settings.defaultTagId", "Default tag is enabled but defaultTagId is blank.");
            } else if (!byId.containsKey(defaultTagId.toLowerCase(Locale.ROOT))) {
                report.add(Severity.ERROR, "settings.defaultTagId", "Default tag '" + defaultTagId + "' does not exist in tags.json.");
            }
        }

        if (!"CUSTOM".equals(settings.getNameplatePreset())) {
            report.add(Severity.INFO, "settings.nameplatePreset",
                    "Nameplate preset '" + settings.getNameplatePreset() + "' is overriding nameplateFormat.");
        }

        String glyphFont = settings.getExperimentalGlyphFont();
        if (settings.isExperimentalGlyphNameplatesEnabled()) {
            report.add(Severity.INFO, "settings.experimentalGlyphFont",
                    "Glyph nameplates will use the '" + glyphFont + "' font.");
        } else if (!"default".equals(glyphFont)) {
            report.add(Severity.INFO, "settings.experimentalGlyphFont",
                    "Glyph font '" + glyphFont + "' is configured, but glyph nameplates are disabled.");
        }

        if (!settings.isExperimentalGlyphNameplatesEnabled() && usesMultilineNameplate(settings)) {
            report.add(Severity.WARNING, "settings.nameplateFormat",
                    "Multiline nameplate formatting is configured, but native Hytale nameplates may render as one line. Enable experimentalGlyphNameplatesEnabled for reliable multiline rendering.");
        }

        boolean economyAvailable = integrations != null && integrations.hasAnyEconomy();
        for (TagDefinition def : byId.values()) {
            String id = def.getId() != null ? def.getId() : "?";
            if (def.isPurchasable() && def.getPrice() > 0.0d && !settings.isEconomySystemEnabled()) {
                report.add(Severity.WARNING, "tag " + id, "Tag is purchasable with a price, but economySystemEnabled=false.");
            } else if (def.isPurchasable() && def.getPrice() > 0.0d && integrations != null && !economyAvailable) {
                report.add(Severity.WARNING, "tag " + id, "Tag is purchasable with a price, but no economy backend is available.");
            }

            String permission = trimToNull(def.getPermission());
            if ((settings.isPermissionGateEnabled() || settings.isFullPermissionGateEnabled()) && permission == null) {
                report.add(Severity.WARNING, "tag " + id, "Permission gates are enabled but this tag has no permission node.");
            }
        }
    }

    private static int countOccurrences(@Nonnull String haystack, @Nonnull String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static boolean usesMultilineNameplate(@Nonnull Settings settings) {
        String preset = settings.getNameplatePreset();
        if ("TWO_LINE".equals(preset) || "RPG".equals(preset) || "ENDLESS".equals(preset)) {
            return true;
        }

        String format = settings.getNameplateFormatRaw().toLowerCase(Locale.ROOT);
        return format.contains("\n")
                || format.contains("\\n")
                || format.contains("/n")
                || format.contains("{nl}")
                || format.contains("{newline}")
                || format.contains("<br");
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public static final class Finding {
        private final Severity severity;
        private final String location;
        private final String message;

        private Finding(@Nonnull Severity severity,
                        @Nonnull String location,
                        @Nonnull String message) {
            this.severity = severity;
            this.location = location;
            this.message = message;
        }

        @Nonnull
        public Severity getSeverity() {
            return severity;
        }

        @Nonnull
        public String getLocation() {
            return location;
        }

        @Nonnull
        public String getMessage() {
            return message;
        }
    }

    public static final class Report {
        private final File file;
        private final List<Finding> findings = new ArrayList<>();
        private int rawTagCount;
        private int uniqueTagCount;
        private int categoryCount;

        private Report(@Nonnull File file) {
            this.file = file;
        }

        private void add(@Nonnull Severity severity,
                         @Nonnull String location,
                         @Nonnull String message) {
            findings.add(new Finding(severity, location, message));
        }

        @Nonnull
        public File getFile() {
            return file;
        }

        public int getRawTagCount() {
            return rawTagCount;
        }

        public int getUniqueTagCount() {
            return uniqueTagCount;
        }

        public int getCategoryCount() {
            return categoryCount;
        }

        @Nonnull
        public List<Finding> getFindings() {
            return Collections.unmodifiableList(findings);
        }

        public int count(@Nonnull Severity severity) {
            int count = 0;
            for (Finding finding : findings) {
                if (finding.getSeverity() == severity) {
                    count++;
                }
            }
            return count;
        }

        public boolean hasErrors() {
            return count(Severity.ERROR) > 0;
        }

        @Nonnull
        public Collection<Finding> getFindingsUpTo(int max) {
            if (max <= 0 || findings.isEmpty()) {
                return List.of();
            }
            return findings.subList(0, Math.min(max, findings.size()));
        }
    }
}
