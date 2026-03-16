package com.mystichorizons.mysticnametags.nameplate;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.WiFlowPlaceholderSupport;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central place for building and resolving nameplate text.
 *
 * Flow:
 *   1) Build raw format from Settings.nameplateFormat using known tokens
 *   2) Optionally resolve WiFlow placeholders
 *   3) Optionally resolve helpch PlaceholderAPI placeholders
 *   4) Colorize for glyph/chat usage
 *   5) Strip formatting for vanilla Nameplate usage
 *
 * This class is intentionally the single source of truth so both:
 *   - NameplateManager
 *   - GlyphNameplateManager
 * consume the same resolved format.
 */
public final class NameplateTextResolver {

    private static final boolean HELPCH_AVAILABLE;

    static {
        boolean helpch;
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            helpch = true;
        } catch (ClassNotFoundException ex) {
            helpch = false;
        }
        HELPCH_AVAILABLE = helpch;
    }

    private NameplateTextResolver() {
    }

    /**
     * Full resolved result for both vanilla + glyph rendering.
     */
    public static final class ResolvedNameplateText {
        private final String raw;
        private final String colored;
        private final String plain;

        public ResolvedNameplateText(@Nonnull String raw,
                                     @Nonnull String colored,
                                     @Nonnull String plain) {
            this.raw = raw;
            this.colored = colored;
            this.plain = plain;
        }

        @Nonnull
        public String getRaw() {
            return raw;
        }

        @Nonnull
        public String getColored() {
            return colored;
        }

        @Nonnull
        public String getPlain() {
            return plain;
        }
    }

    /**
     * Rich nameplate context so resolver can remain the one true formatter.
     */
    public static final class Context {
        private final PlayerRef playerRef;
        private final String rank;
        private final String name;
        private final String tag;
        private final String endlessLevel;
        private final String endlessPrestige;
        private final String endlessRace;
        private final String endlessPrimaryClass;
        private final String endlessSecondaryClass;
        private final String rpgLevel;
        private final String ecoquestsRank;

        private Context(Builder builder) {
            this.playerRef = builder.playerRef;
            this.rank = normalizeSegment(builder.rank);
            this.name = normalizeSegment(builder.name);
            this.tag = normalizeSegment(builder.tag);
            this.endlessLevel = normalizeSegment(builder.endlessLevel);
            this.endlessPrestige = normalizeSegment(builder.endlessPrestige);
            this.endlessRace = normalizeSegment(builder.endlessRace);
            this.endlessPrimaryClass = normalizeSegment(builder.endlessPrimaryClass);
            this.endlessSecondaryClass = normalizeSegment(builder.endlessSecondaryClass);
            this.rpgLevel = normalizeSegment(builder.rpgLevel);
            this.ecoquestsRank = normalizeSegment(builder.ecoquestsRank);
        }

        @Nullable
        public PlayerRef getPlayerRef() {
            return playerRef;
        }

        @Nonnull
        public String getRank() {
            return rank;
        }

        @Nonnull
        public String getName() {
            return name;
        }

        @Nonnull
        public String getTag() {
            return tag;
        }

        @Nonnull
        public String getEndlessLevel() {
            return endlessLevel;
        }

        @Nonnull
        public String getEndlessPrestige() {
            return endlessPrestige;
        }

        @Nonnull
        public String getEndlessRace() {
            return endlessRace;
        }

        @Nonnull
        public String getEndlessPrimaryClass() {
            return endlessPrimaryClass;
        }

        @Nonnull
        public String getEndlessSecondaryClass() {
            return endlessSecondaryClass;
        }

        @Nonnull
        public String getRpgLevel() {
            return rpgLevel;
        }

        @Nonnull
        public String getEcoquestsRank() {
            return ecoquestsRank;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private PlayerRef playerRef;
            private String rank;
            private String name;
            private String tag;
            private String endlessLevel;
            private String endlessPrestige;
            private String endlessRace;
            private String endlessPrimaryClass;
            private String endlessSecondaryClass;
            private String rpgLevel;
            private String ecoquestsRank;

            public Builder playerRef(@Nullable PlayerRef playerRef) {
                this.playerRef = playerRef;
                return this;
            }

            public Builder rank(@Nullable String rank) {
                this.rank = rank;
                return this;
            }

            public Builder name(@Nullable String name) {
                this.name = name;
                return this;
            }

            public Builder tag(@Nullable String tag) {
                this.tag = tag;
                return this;
            }

            public Builder endlessLevel(@Nullable String endlessLevel) {
                this.endlessLevel = endlessLevel;
                return this;
            }

            public Builder endlessPrestige(@Nullable String endlessPrestige) {
                this.endlessPrestige = endlessPrestige;
                return this;
            }

            public Builder endlessRace(@Nullable String endlessRace) {
                this.endlessRace = endlessRace;
                return this;
            }

            public Builder endlessPrimaryClass(@Nullable String endlessPrimaryClass) {
                this.endlessPrimaryClass = endlessPrimaryClass;
                return this;
            }

            public Builder endlessSecondaryClass(@Nullable String endlessSecondaryClass) {
                this.endlessSecondaryClass = endlessSecondaryClass;
                return this;
            }

            public Builder rpgLevel(@Nullable String rpgLevel) {
                this.rpgLevel = rpgLevel;
                return this;
            }

            public Builder ecoquestsRank(@Nullable String ecoquestsRank) {
                this.ecoquestsRank = ecoquestsRank;
                return this;
            }

            public Context build() {
                return new Context(this);
            }
        }
    }

    /**
     * Resolve the configured nameplate format into both colored + plain forms.
     *
     * Supported built-in tokens:
     *  - {rank}
     *  - {name}
     *  - {tag}
     *  - {endless_level}
     *  - {endless_prestige}
     *  - {endless_race}
     *  - {rpg_level}
     *
     * Any remaining placeholders can still be resolved by external placeholder APIs.
     */
    @Nonnull
    public static ResolvedNameplateText resolve(@Nonnull Context context) {
        Settings settings = Settings.get();

        String raw = settings.getNameplateFormatRaw();

        raw = raw
                .replace("{rank}", context.getRank())
                .replace("{name}", context.getName())
                .replace("{tag}", context.getTag())
                .replace("{endless_level}", context.getEndlessLevel())
                .replace("{endless_prestige}", context.getEndlessPrestige())
                .replace("{endless_race}", context.getEndlessRace())
                .replace("{endless_primary_class}", context.getEndlessPrimaryClass())
                .replace("{endless_secondary_class}", context.getEndlessSecondaryClass())
                .replace("{rpg_level}", context.getRpgLevel())
                .replace("{ecoquests_rank}", context.getEcoquestsRank());

        if (settings.isStripExtraSpacesEnabled()) {
            raw = collapseWhitespacePreserveLines(raw);
        }

        PlayerRef playerRef = context.getPlayerRef();

        if (playerRef != null && settings.isWiFlowPlaceholdersEnabled()) {
            try {
                raw = WiFlowPlaceholderSupport.apply(playerRef, raw);
            } catch (Throwable ignored) {
            }
        }

        if (playerRef != null
                && settings.isHelpchPlaceholderApiEnabled()
                && HELPCH_AVAILABLE) {
            try {
                raw = PlaceholderAPI.setPlaceholders(playerRef, raw);
            } catch (Throwable ignored) {
            }
        }

        String colored = ColorFormatter.colorize(raw);
        if (colored == null) {
            colored = "";
        }

        String plain = ColorFormatter.stripFormatting(colored);
        if (plain == null) {
            plain = "";
        }
        plain = collapseWhitespacePreserveLines(plain);

        return new ResolvedNameplateText(raw, colored, plain);
    }

    /**
     * Backwards-compatible overload for existing callsites.
     */
    @Nonnull
    public static ResolvedNameplateText resolve(@Nullable PlayerRef playerRef,
                                                @Nullable String rank,
                                                @Nullable String name,
                                                @Nullable String tag) {
        return resolve(Context.builder()
                .playerRef(playerRef)
                .rank(rank)
                .name(name)
                .tag(tag)
                .build());
    }

    /**
     * Backwards-compatible helper used by older callsites.
     */
    @Nonnull
    public static String build(@Nullable PlayerRef playerRef,
                               @Nullable String rank,
                               @Nullable String name,
                               @Nullable String tag) {
        return resolve(playerRef, rank, name, tag).getColored();
    }

    @Nonnull
    private static String normalizeSegment(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.trim();
    }

    @Nonnull
    private static String collapseWhitespacePreserveLines(@Nonnull String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        StringBuilder out = new StringBuilder(normalized.length());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]
                    .replaceAll("[ \\t]+", " ")
                    .trim();

            out.append(line);

            if (i + 1 < lines.length) {
                out.append('\n');
            }
        }

        return out.toString();
    }
}