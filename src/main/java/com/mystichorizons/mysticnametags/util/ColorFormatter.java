package com.mystichorizons.mysticnametags.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorFormatter {

    // Hytale's text pipeline (chat/notifications) understands '&' color codes.
    private static final char COLOR_CHAR = '&';
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    private ColorFormatter() {
    }

    /**
     * Convert config-style text into something Hytale understands:
     * - "&#RRGGBB" -> &x&R&R&G&G&B&B
     * - normalize both '§' and '&' codes so that everything uses '&'.
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
                || c == 'r' || c == 'R' || c == 'x' || c == 'X';
    }

    // -------- CustomUI helpers stay the same --------

    /**
     * Strip all & / § formatting, leaving just the plain text.
     * e.g. "&#8A2BE2&l[Mystic]" -> "[Mystic]"
     */
    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) return input;

        String out = input;
        // Remove hex codes like &#RRGGBB
        out = out.replaceAll("&#[0-9a-fA-F]{6}", "");
        // Remove & + single code char
        out = out.replaceAll("&[0-9a-fk-orxA-FK-ORX]", "");
        // Remove § + single code char
        out = out.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "");

        return out;
    }

    /**
     * Extract the first hex color (RRGGBB) from strings like "&#8A2BE2&l[Mystic]".
     * Returns null if none found.
     */
    public static String extractFirstHexColor(String input) {
        if (input == null || input.isEmpty()) return null;
        Matcher m = HEX_PATTERN.matcher(input);
        return m.find() ? m.group(1) : null;
    }
}
