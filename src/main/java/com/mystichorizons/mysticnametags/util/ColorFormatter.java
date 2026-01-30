package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.server.core.Message;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorFormatter {

    private static final char COLOR_CHAR = '&';
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");

    // &x&f&f&0&0&0&0 or §x§f§f§0§0§0§0
    private static final Pattern EXPANDED_HEX_AMP  =
            Pattern.compile("&x(?:&[0-9a-fA-F]){6}");
    private static final Pattern EXPANDED_HEX_SECT =
            Pattern.compile("§x(?:§[0-9a-fA-F]){6}");

    // Legacy single char codes (&a, &b, etc.) – we use the LAST one as fallback
    private static final Pattern LEGACY_COLOR_PATTERN =
            Pattern.compile("(?i)[&§]([0-9a-f])");

    // For Message-based coloring (notifications etc.)
    private static final Color DEFAULT_COLOR = Color.WHITE;
    private static final Map<Character, Color> LEGACY_COLORS = new HashMap<>();

    static {
        LEGACY_COLORS.put('0', Color.BLACK);
        LEGACY_COLORS.put('1', new Color(0x0000AA));
        LEGACY_COLORS.put('2', new Color(0x00AA00));
        LEGACY_COLORS.put('3', new Color(0x00AAAA));
        LEGACY_COLORS.put('4', new Color(0xAA0000));
        LEGACY_COLORS.put('5', new Color(0xAA00AA));
        LEGACY_COLORS.put('6', new Color(0xFFAA00));
        LEGACY_COLORS.put('7', new Color(0xAAAAAA));
        LEGACY_COLORS.put('8', new Color(0x555555));
        LEGACY_COLORS.put('9', new Color(0x5555FF));
        LEGACY_COLORS.put('a', new Color(0x55FF55));
        LEGACY_COLORS.put('b', new Color(0x55FFFF));
        LEGACY_COLORS.put('c', new Color(0xFF5555));
        LEGACY_COLORS.put('d', new Color(0xFF55FF));
        LEGACY_COLORS.put('e', new Color(0xFFFF55));
        LEGACY_COLORS.put('f', Color.WHITE);
    }

    private ColorFormatter() {}

    // ------------------------------------------------------------
    // STRING HELPERS (chat / nameplate compatibility)
    // ------------------------------------------------------------

    /**
     * Convert config-style text into something Hytale understands:
     * - "#RRGGBB"   -> "&#RRGGBB"
     * - "&#RRGGBB"  -> &x&R&R&G&G&B&B
     * - normalize both '§' and '&' codes so that everything uses '&'.
     *
     * Use this for places that EXPECT legacy & codes (e.g. chat),
     * NOT for UI labels or Message-based APIs.
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 1) Expand hex codes like "&#8A2BE2" to &x&8&A&2&B&E&2
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder();
            replacement.append(COLOR_CHAR).append('x');
            for (char c : hex.toCharArray()) {
                replacement.append(COLOR_CHAR).append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        String processed = buffer.toString();

        // 2) Normalize '§' color codes -> '&' color codes
        processed = translateAlternateColorCodes('§', processed);

        // 3) Normalize '&' color codes (idempotent, but keeps logic in one place)
        processed = translateAlternateColorCodes('&', processed);

        return processed;
    }

    /**
     * Heuristic for UI labels:
     * Return the color (as RRGGBB) that is active when the first
     * "real" content letter/digit is rendered.
     *
     * Handles:
     *  - &#RRGGBB
     *  - &x&F&F&0&0&0&0 / §x§f§f§0§0§0§0
     *  - legacy &a..&f / §a..§f
     */
    public static String extractUiTextColor(String input) {
        if (input == null || input.isEmpty()) return null;

        String currentHex = null;
        int len = input.length();
        int i = 0;

        while (i < len) {
            char c = input.charAt(i);

            // Color/format prefix
            if ((c == '&' || c == '§') && i + 1 < len) {
                char next = input.charAt(i + 1);

                // &#RRGGBB
                if (next == '#' && i + 7 < len) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9A-Fa-f]{6}")) {
                        currentHex = hex.toUpperCase(Locale.ROOT);
                        i += 8;
                        continue;
                    }
                }

                // &x&F&F&0&0&0&0 / §x§f§f§0§0§0§0
                if ((next == 'x' || next == 'X') && i + 13 < len) {
                    StringBuilder hex = new StringBuilder(6);
                    // pattern marker, x, then 6 pairs of (marker, digit)
                    for (int j = i + 2; j <= i + 12; j += 2) {
                        char digit = input.charAt(j + 1);
                        hex.append(Character.toLowerCase(digit));
                    }
                    if (hex.length() == 6) {
                        currentHex = hex.toString().toUpperCase(Locale.ROOT);
                        i += 14;
                        continue;
                    }
                }

                // Legacy single-char color (&a..&f / §a..§f)
                char code = Character.toLowerCase(next);
                if (LEGACY_COLORS.containsKey(code)) {
                    Color col = LEGACY_COLORS.get(code);
                    if (col != null) {
                        currentHex = String.format("%06X", col.getRGB() & 0xFFFFFF);
                    }
                    i += 2;
                    continue;
                }

                // Style codes (&l, &o, &r, etc.) – just skip
                i += 2;
                continue;
            }

            // First "real" content letter/digit = stop and return current color
            if (Character.isLetterOrDigit(c)) {
                break;
            }

            i++;
        }

        // Fallback if we never saw a color before content:
        if (currentHex == null) {
            currentHex = extractFirstHexColor(input);
        }

        return currentHex;
    }


    /**
     * Convert text into NAMEPLATE-safe formatting:
     * - &#RRGGBB -> §x§R§R§G§G§B§B
     * - & codes -> § codes
     */
    public static String colorizeForNameplate(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Expand hex &#RRGGBB -> §x§R§R§G§G§B§B
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        // Convert & → §
        return buffer.toString().replace('&', '§');
    }

    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
        if (textToTranslate == null) {
            return null;
        }
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == altColorChar && isColorCodeChar(b[i + 1])) {
                b[i] = COLOR_CHAR;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    private static boolean isColorCodeChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || (c >= 'k' && c <= 'o')
                || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R'
                || c == 'x' || c == 'X';
    }

    // -------- CustomUI helpers (used in Tags.ui) --------

    /**
     * Strip all & / § formatting, leaving just the plain text.
     * Also strips expanded hex (&x&F&F&0&0&0&0) and "#RRGGBB".
     *
     * IMPORTANT: we also remove any leftover bare '&' / '§' so
     * LuckPerms control codes like "&w" or "&[" never leak.
     */
    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) return input;

        String out = input;

        // Remove hex codes like &#RRGGBB
        out = out.replaceAll("&#[0-9a-fA-F]{6}", "");

        // Remove bare "#RRGGBB"
        out = out.replaceAll("#[0-9a-fA-F]{6}", "");

        // Expanded hex (&x&f&f&0&0&0&0 / §x§f§f§0§0§0§0)
        out = out.replaceAll("&x(&[0-9a-fA-F]){6}", "");
        out = out.replaceAll("§x(§[0-9a-fA-F]){6}", "");

        // Legacy single-char style codes: &a, &b, &l, &o, &r, etc.
        out = out.replaceAll("&[0-9a-fk-orxA-FK-ORX]", "");
        out = out.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "");

        return out;
    }

    /**
     * Extract the first color as hex (RRGGBB) from:
     * - "&#RRGGBB..."
     * - "&x&F&F&0&0&0&0" / "§x§f§f§0§0§0§0"
     * - or, as a last resort, the last legacy color (&a, &b, etc.)
     */
    public static String extractFirstHexColor(String input) {
        if (input == null || input.isEmpty()) return null;

        // 0) Plain "#RRGGBB" anywhere in the string
        Matcher hashMatcher = HASH_HEX_PATTERN.matcher(input);
        if (hashMatcher.find()) {
            return hashMatcher.group(1);
        }

        // 1) &#RRGGBB
        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        if (hexMatcher.find()) {
            return hexMatcher.group(1);
        }

        // 2) &x&F&F&0&0&0&0 style
        Matcher mAmp = EXPANDED_HEX_AMP.matcher(input);
        if (mAmp.find()) {
            String unpacked = unpackExpandedHex(mAmp.group(), '&');
            if (unpacked != null) return unpacked;
        }

        // 3) §x§F§F§0§0§0§0 style
        Matcher mSect = EXPANDED_HEX_SECT.matcher(input);
        if (mSect.find()) {
            String unpacked = unpackExpandedHex(mSect.group(), '§');
            if (unpacked != null) return unpacked;
        }

        // 4) Fallback: last legacy &a / &b / &c etc.
        Matcher legacy = LEGACY_COLOR_PATTERN.matcher(input);
        Character lastCode = null;
        while (legacy.find()) {
            lastCode = Character.toLowerCase(legacy.group(1).charAt(0));
        }
        if (lastCode != null) {
            Color c = LEGACY_COLORS.get(lastCode);
            if (c != null) {
                return String.format("%06X", c.getRGB() & 0xFFFFFF);
            }
        }

        return null;
    }

    private static String unpackExpandedHex(String seq, char marker) {
        // seq looks like "&x&f&f&0&0&0&0"
        StringBuilder hex = new StringBuilder(6);
        for (int i = 0; i < seq.length(); i++) {
            char ch = seq.charAt(i);
            if (ch == marker || ch == 'x' || ch == 'X') {
                continue;
            }
            hex.append(ch);
        }
        return hex.length() == 6 ? hex.toString() : null;
    }

    // ------------------------------------------------------------
    // MESSAGE HELPER (for Notifications etc.)
    // ------------------------------------------------------------

    public static Message toMessage(String text) {
        return toMessage(text, DEFAULT_COLOR);
    }

    public static Message toMessage(String text, Color baseColor) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        Color currentColor = baseColor != null ? baseColor : DEFAULT_COLOR;
        boolean bold = false;
        boolean italic = false;

        List<Message> parts = new ArrayList<>();
        int i = 0;
        int textStart = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);

                // Hex: &#RRGGBB
                if (next == '#' && i + 7 < text.length()) {
                    String hexPart = text.substring(i + 2, i + 8);
                    if (hexPart.matches("[0-9A-Fa-f]{6}")) {
                        if (i > textStart) {
                            String segment = text.substring(textStart, i);
                            if (!segment.isEmpty()) {
                                parts.add(buildSegment(segment, currentColor, bold, italic));
                            }
                        }
                        currentColor = new Color(Integer.parseInt(hexPart, 16));
                        i += 8;
                        textStart = i;
                        continue;
                    }
                }

                char code = Character.toLowerCase(next);
                if (LEGACY_COLORS.containsKey(code) || code == 'r' || code == 'l' || code == 'o') {
                    if (i > textStart) {
                        String segment = text.substring(textStart, i);
                        if (!segment.isEmpty()) {
                            parts.add(buildSegment(segment, currentColor, bold, italic));
                        }
                    }

                    if (LEGACY_COLORS.containsKey(code)) {
                        currentColor = LEGACY_COLORS.get(code);
                    } else if (code == 'r') {
                        currentColor = baseColor != null ? baseColor : DEFAULT_COLOR;
                        bold = false;
                        italic = false;
                    } else if (code == 'l') {
                        bold = true;
                    } else if (code == 'o') {
                        italic = true;
                    }

                    i += 2;
                    textStart = i;
                    continue;
                }
            }

            i++;
        }

        if (textStart < text.length()) {
            String segment = text.substring(textStart);
            if (!segment.isEmpty()) {
                parts.add(buildSegment(segment, currentColor, bold, italic));
            }
        }

        if (parts.isEmpty()) {
            return Message.raw("");
        }
        return Message.join(parts.toArray(new Message[0]));
    }

    private static Message buildSegment(String text, Color color, boolean bold, boolean italic) {
        Message msg = Message.raw(text);
        if (color != null) {
            msg = msg.color(color);
        }
        if (bold) {
            msg = msg.bold(true);
        }
        if (italic) {
            msg = msg.italic(true);
        }
        return msg;
    }
}
