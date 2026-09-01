package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GlyphInfoCompat {

    // Similar spacing to PerfectHolograms
    public static final double CHAR_WIDTH = 0.1;
    public static final float  BASE_MODEL_SCALE = 1.0f;

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

    /** Every character with a glyph texture, for asset registration. */
    public static java.util.Set<Character> supportedChars() {
        return java.util.Collections.unmodifiableSet(CHAR_TO_SAFE_ID_RAW.keySet());
    }

    @Nullable
    public static String getSafeIdLower(char ch) {
        String raw = CHAR_TO_SAFE_ID_RAW.get(ch);
        if (raw == null) return null;
        return raw.toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static String getSafeId(char ch) {
        return getSafeIdLower(ch);
    }

}
