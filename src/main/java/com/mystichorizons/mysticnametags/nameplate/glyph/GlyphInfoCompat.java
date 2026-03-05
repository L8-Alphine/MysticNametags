package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GlyphInfoCompat {

    // Similar spacing to PerfectHolograms
    public static final double CHAR_WIDTH = 0.02;
    public static final float  BASE_MODEL_SCALE = 0.07f;

    /**
     * Raw safe ids stored in lowercase to match your asset naming convention.
     * Examples: lo_a, up_a, amp, dquote, lparen, etc.
     */
    private static final Map<Character, String> CHAR_TO_SAFE_ID_RAW = new HashMap<>();

    static {
        // letters
        for (char c = 'a'; c <= 'z'; c++) CHAR_TO_SAFE_ID_RAW.put(c, "lo_" + c); // lo_a
        for (char c = 'A'; c <= 'Z'; c++) CHAR_TO_SAFE_ID_RAW.put(c, "up_" + Character.toLowerCase(c)); // up_a

        // digits
        for (char c = '0'; c <= '9'; c++) CHAR_TO_SAFE_ID_RAW.put(c, String.valueOf(c)); // "0".."9"

        // punctuation / symbols
        CHAR_TO_SAFE_ID_RAW.put('!', "excl");
        CHAR_TO_SAFE_ID_RAW.put('"', "dquote");
        CHAR_TO_SAFE_ID_RAW.put('#', "hash");
        CHAR_TO_SAFE_ID_RAW.put('$', "dollar");
        CHAR_TO_SAFE_ID_RAW.put('%', "pct");
        CHAR_TO_SAFE_ID_RAW.put('&', "amp");
        CHAR_TO_SAFE_ID_RAW.put('\'', "squote");
        CHAR_TO_SAFE_ID_RAW.put('(', "lparen");
        CHAR_TO_SAFE_ID_RAW.put(')', "rparen");
        CHAR_TO_SAFE_ID_RAW.put('*', "asterisk");
        CHAR_TO_SAFE_ID_RAW.put('+', "plus");
        CHAR_TO_SAFE_ID_RAW.put(',', "comma");
        CHAR_TO_SAFE_ID_RAW.put('-', "minus");
        CHAR_TO_SAFE_ID_RAW.put('.', "period");
        CHAR_TO_SAFE_ID_RAW.put('/', "slash");
        CHAR_TO_SAFE_ID_RAW.put(':', "colon");
        CHAR_TO_SAFE_ID_RAW.put(';', "semicol");
        CHAR_TO_SAFE_ID_RAW.put('<', "lt");
        CHAR_TO_SAFE_ID_RAW.put('=', "eq");
        CHAR_TO_SAFE_ID_RAW.put('>', "gt");
        CHAR_TO_SAFE_ID_RAW.put('?', "question");
        CHAR_TO_SAFE_ID_RAW.put('@', "at");
        CHAR_TO_SAFE_ID_RAW.put('[', "lbracket");
        CHAR_TO_SAFE_ID_RAW.put('\\', "backslash");
        CHAR_TO_SAFE_ID_RAW.put(']', "rbracket");
        CHAR_TO_SAFE_ID_RAW.put('^', "caret");
        CHAR_TO_SAFE_ID_RAW.put('_', "uscore");
        CHAR_TO_SAFE_ID_RAW.put('`', "grave");
        CHAR_TO_SAFE_ID_RAW.put('{', "lbrace");
        CHAR_TO_SAFE_ID_RAW.put('|', "pipe");
        CHAR_TO_SAFE_ID_RAW.put('}', "rbrace");
        CHAR_TO_SAFE_ID_RAW.put('~', "tilde");
        CHAR_TO_SAFE_ID_RAW.put('█', "block");
    }

    private GlyphInfoCompat() {}

    public static boolean isSupported(char ch) {
        return CHAR_TO_SAFE_ID_RAW.containsKey(ch);
    }

    /**
     * Lowercase safe id (matches your asset naming convention).
     * ex: 'a' -> lo_a, '"' -> dquote
     */
    @Nullable
    public static String getSafeIdLower(char ch) {
        String raw = CHAR_TO_SAFE_ID_RAW.get(ch);
        if (raw == null) return null;
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * Default safe id: lowercase.
     */
    @Nullable
    public static String getSafeId(char ch) {
        return getSafeIdLower(ch);
    }

    /**
     * Default model asset id: lowercase first.
     */
    @Nullable
    public static String getModelAssetId(char ch) {
        String safe = getSafeIdLower(ch);
        if (safe == null) return null;
        return GlyphAssets.modelId(safe);
    }

    /**
     * Returns candidate asset IDs in priority order:
     *  1) lowercase (your current assets)
     *  2) engine-case (if server internally remaps keys)
     */
    @Nullable
    public static String[] getModelAssetIdCandidates(char ch) {
        String lower = getSafeIdLower(ch);
        if (lower == null) return null;

        String engine = normalizeEngineCase(lower);

        String idLower = GlyphAssets.modelId(lower);
        String idEngine = GlyphAssets.modelId(engine);

        if (idLower.equals(idEngine)) return new String[]{ idLower };
        return new String[]{ idLower, idEngine };
    }

    /**
     * Converts "lowercase safe id" into commonly-enforced engine casing:
     * - Split by '_' and TitleCase each segment
     * - If segment is a single letter -> uppercase it (a -> A)
     * - Digits remain unchanged
     *
     * Examples:
     *  lo_a         -> Lo_A
     *  up_z         -> Up_Z
     *  sans_1       -> Sans_1
     *  dquote       -> Dquote
     *  backslash    -> Backslash
     */
    public static String normalizeEngineCase(String lowerRaw) {
        if (lowerRaw == null || lowerRaw.isEmpty()) return lowerRaw;

        String[] parts = lowerRaw.split("_");
        StringBuilder out = new StringBuilder(lowerRaw.length() + 4);

        for (String p : parts) {
            if (p.isEmpty()) continue;

            String norm;
            if (p.length() == 1) {
                char c = p.charAt(0);
                norm = Character.isLetter(c) ? String.valueOf(Character.toUpperCase(c)) : p;
            } else {
                norm = Character.toUpperCase(p.charAt(0)) + p.substring(1).toLowerCase(Locale.ROOT);
            }

            if (out.length() > 0) out.append('_');
            out.append(norm);
        }

        return out.toString();
    }
}