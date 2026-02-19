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

        // 0) MiniMessage -> legacy (&#RRGGBB, &l, &o, &r)
        // This also expands <gradient:...>text</gradient> into per-char &#RRGGBB.
        input = MiniMessageSupport.miniToLegacy(input);

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

        // 3) Normalize '&' color codes
        processed = translateAlternateColorCodes('&', processed);

        return processed;
    }

    // ------------------------------------------------------------
    // MINIMESSAGE (very small subset) + GRADIENT SUPPORT
    // ------------------------------------------------------------

    /**
     * MiniMessage-aware Message builder.
     * Supports:
     *  - <#RRGGBB> ... </#RRGGBB>
     *  - <red>, <green>, <yellow>, <white>, <black>, <gray>, etc.
     *  - <bold>, <italic>, <reset>
     *  - <gradient:#RRGGBB:#RRGGBB[:#RRGGBB...]> ... </gradient>
     *
     * Unknown tags are treated as literal text.
     */
    public static Message toMessageMini(String text) {
        return toMessageMini(text, DEFAULT_COLOR);
    }

    public static Message toMessageMini(String text, Color baseColor) {
        if (text == null || text.isEmpty()) return Message.raw("");

        // If it doesn't look like minimessage, fall back to legacy parser.
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return toMessage(text, baseColor);
        }

        return MiniMessageParser.parse(text, baseColor != null ? baseColor : DEFAULT_COLOR);
    }

    /**
     * Convert MiniMessage tags into legacy-friendly markup:
     * - <#RRGGBB> -> &#RRGGBB
     * - <bold> -> &l
     * - <italic> -> &o
     * - <reset> -> &r
     * - <gradient:...>text</gradient> -> per-character &#RRGGBB + char
     *
     * Useful for nameplates/chat strings that don't accept MiniMessage directly.
     */
    public static String miniToLegacy(String input) {
        if (input == null || input.isEmpty()) return input;
        if (input.indexOf('<') < 0 || input.indexOf('>') < 0) return input;

        return MiniMessageParser.toLegacy(input);
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

        // MiniMessage -> legacy so existing scanner sees &#RRGGBB etc.
        input = MiniMessageSupport.miniToLegacy(input);


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

        // 0) MiniMessage -> legacy first
        input = MiniMessageSupport.miniToLegacy(input);

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

        // 0) Remove MiniMessage tags but keep content.
        // First: explicitly unwrap gradients (keep inner text)
        out = MiniMessageSupport.stripMiniTags(out);


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

        input = MiniMessageSupport.miniToLegacy(input);

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

        // MiniMessage -> legacy so existing parsing handles everything
        text = MiniMessageSupport.miniToLegacy(text);

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
                        try {
                            currentColor = new Color(Integer.parseInt(hexPart, 16));
                        } catch (NumberFormatException ignored) {}

                        i += 8;
                        textStart = i;
                        continue;
                    }
                }

                // Expanded hex: &x&F&F&0&0&0&0 / §x§f§f§0§0§0§0
                if ((next == 'x' || next == 'X') && i + 13 < text.length()) {
                    // flush pending text
                    if (i > textStart) {
                        String segment = text.substring(textStart, i);
                        if (!segment.isEmpty()) {
                            parts.add(buildSegment(segment, currentColor, bold, italic));
                        }
                    }

                    StringBuilder hex = new StringBuilder(6);
                    // marker, x, then 6 pairs of (marker, digit)
                    for (int j = i + 2; j <= i + 12; j += 2) {
                        char digit = text.charAt(j + 1);
                        hex.append(Character.toLowerCase(digit));
                    }

                    try {
                        currentColor = new Color(Integer.parseInt(hex.toString(), 16));
                    } catch (NumberFormatException ignored) {
                        // if invalid, keep old color
                    }

                    i += 14;       // skip &x + 6 pairs
                    textStart = i;
                    continue;
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

    // ------------------------------------------------------------
    // Internal MiniMessage subset parser
    // ------------------------------------------------------------
    private static final class MiniMessageParser {

        private static final Map<String, Color> NAMED = new HashMap<>();
        static {
            NAMED.put("black", Color.BLACK);
            NAMED.put("white", Color.WHITE);
            NAMED.put("gray", new Color(0xAAAAAA));
            NAMED.put("dark_gray", new Color(0x555555));
            NAMED.put("red", new Color(0xFF5555));
            NAMED.put("dark_red", new Color(0xAA0000));
            NAMED.put("green", new Color(0x55FF55));
            NAMED.put("dark_green", new Color(0x00AA00));
            NAMED.put("blue", new Color(0x5555FF));
            NAMED.put("dark_blue", new Color(0x0000AA));
            NAMED.put("aqua", new Color(0x55FFFF));
            NAMED.put("dark_aqua", new Color(0x00AAAA));
            NAMED.put("yellow", new Color(0xFFFF55));
            NAMED.put("gold", new Color(0xFFAA00));
            NAMED.put("light_purple", new Color(0xFF55FF));
            NAMED.put("dark_purple", new Color(0xAA00AA));
        }

        private static final Pattern HEX_TAG = Pattern.compile("^#([0-9A-Fa-f]{6})$");
        private static final Pattern GRADIENT_OPEN = Pattern.compile("^gradient:(.+)$");

        private static final class State {
            Color color;
            boolean bold;
            boolean italic;

            State(Color color, boolean bold, boolean italic) {
                this.color = color;
                this.bold = bold;
                this.italic = italic;
            }

            State copy() { return new State(color, bold, italic); }
        }

        static Message parse(String input, Color baseColor) {
            List<Message> parts = new ArrayList<>();
            Deque<State> stack = new ArrayDeque<>();
            State state = new State(baseColor, false, false);

            int i = 0;
            int textStart = 0;

            while (i < input.length()) {
                char ch = input.charAt(i);

                if (ch == '<') {
                    int close = input.indexOf('>', i + 1);
                    if (close > i) {
                        // flush text before tag
                        if (i > textStart) {
                            String seg = input.substring(textStart, i);
                            if (!seg.isEmpty()) parts.add(buildSegment(seg, state));
                        }

                        String tagRaw = input.substring(i + 1, close).trim();
                        boolean isCloseTag = tagRaw.startsWith("/");

                        String tag = isCloseTag ? tagRaw.substring(1).trim().toLowerCase(Locale.ROOT)
                                : tagRaw.toLowerCase(Locale.ROOT);

                        // Handle </...>
                        if (isCloseTag) {
                            // Pop one state for matching formatting tags we track
                            if (!stack.isEmpty()) {
                                state = stack.pop();
                            }
                            i = close + 1;
                            textStart = i;
                            continue;
                        }

                        // <reset>
                        if ("reset".equals(tag)) {
                            state = new State(baseColor, false, false);
                            stack.clear();
                            i = close + 1;
                            textStart = i;
                            continue;
                        }

                        // <bold>, <italic>
                        if ("bold".equals(tag)) {
                            stack.push(state.copy());
                            state.bold = true;
                            i = close + 1;
                            textStart = i;
                            continue;
                        }
                        if ("italic".equals(tag)) {
                            stack.push(state.copy());
                            state.italic = true;
                            i = close + 1;
                            textStart = i;
                            continue;
                        }

                        // <#RRGGBB>
                        Matcher hexM = HEX_TAG.matcher(tag);
                        if (hexM.matches()) {
                            Color c = parseHexColor(hexM.group(1));
                            if (c != null) {
                                stack.push(state.copy());
                                state.color = c;
                                i = close + 1;
                                textStart = i;
                                continue;
                            }
                        }

                        // <red> <green> etc.
                        Color named = NAMED.get(tag);
                        if (named != null) {
                            stack.push(state.copy());
                            state.color = named;
                            i = close + 1;
                            textStart = i;
                            continue;
                        }

                        // <gradient:...>...</gradient>
                        Matcher gradM = GRADIENT_OPEN.matcher(tag);
                        if (gradM.matches()) {
                            List<Color> stops = parseGradientStops(gradM.group(1));
                            int endTag = findClosingTag(input, close + 1, "gradient");
                            if (endTag >= 0 && stops.size() >= 2) {
                                String inner = input.substring(close + 1, endTag);
                                parts.addAll(applyGradientToText(inner, stops, state));
                                i = endTag + "</gradient>".length();
                                textStart = i;
                                continue;
                            }
                        }

                        // Unknown tag -> treat literally
                        parts.add(buildSegment("<" + tagRaw + ">", state));
                        i = close + 1;
                        textStart = i;
                        continue;
                    }
                }

                i++;
            }

            // flush tail
            if (textStart < input.length()) {
                String seg = input.substring(textStart);
                if (!seg.isEmpty()) parts.add(buildSegment(seg, state));
            }

            if (parts.isEmpty()) return Message.raw("");
            return Message.join(parts.toArray(new Message[0]));
        }

        static String toLegacy(String input) {
            StringBuilder out = new StringBuilder();
            Deque<String> tagStack = new ArrayDeque<>();

            int i = 0;
            while (i < input.length()) {
                char ch = input.charAt(i);

                if (ch == '<') {
                    int close = input.indexOf('>', i + 1);
                    if (close > i) {
                        String tagRaw = input.substring(i + 1, close).trim();
                        boolean isClose = tagRaw.startsWith("/");
                        String tag = isClose ? tagRaw.substring(1).trim().toLowerCase(Locale.ROOT)
                                : tagRaw.toLowerCase(Locale.ROOT);

                        if (isClose) {
                            // For simplicity, any close pops and emits &r then reapplies remaining stack
                            if (!tagStack.isEmpty()) tagStack.pop();
                            out.append("&r");
                            // reapply remaining tags
                            List<String> remaining = new ArrayList<>(tagStack);
                            Collections.reverse(remaining);
                            for (String t : remaining) out.append(t);
                            i = close + 1;
                            continue;
                        }

                        if ("reset".equals(tag)) {
                            tagStack.clear();
                            out.append("&r");
                            i = close + 1;
                            continue;
                        }

                        if ("bold".equals(tag)) {
                            tagStack.push("&l");
                            out.append("&l");
                            i = close + 1;
                            continue;
                        }
                        if ("italic".equals(tag)) {
                            tagStack.push("&o");
                            out.append("&o");
                            i = close + 1;
                            continue;
                        }

                        Matcher hexM = HEX_TAG.matcher(tag);
                        if (hexM.matches()) {
                            String hex = hexM.group(1).toUpperCase(Locale.ROOT);
                            String legacy = "&#" + hex;
                            tagStack.push(legacy);
                            out.append(legacy);
                            i = close + 1;
                            continue;
                        }

                        Color named = NAMED.get(tag);
                        if (named != null) {
                            String legacy = "&#" + String.format("%06X", named.getRGB() & 0xFFFFFF);
                            tagStack.push(legacy);
                            out.append(legacy);
                            i = close + 1;
                            continue;
                        }

                        Matcher gradM = GRADIENT_OPEN.matcher(tag);
                        if (gradM.matches()) {
                            List<Color> stops = parseGradientStops(gradM.group(1));
                            int endTag = findClosingTag(input, close + 1, "gradient");
                            if (endTag >= 0 && stops.size() >= 2) {
                                String inner = input.substring(close + 1, endTag);
                                out.append(applyGradientLegacy(inner, stops));
                                i = endTag + "</gradient>".length();
                                continue;
                            }
                        }

                        // Unknown tag -> literal
                        out.append('<').append(tagRaw).append('>');
                        i = close + 1;
                        continue;
                    }
                }

                out.append(ch);
                i++;
            }

            return out.toString();
        }

        private static Message buildSegment(String text, State state) {
            Message msg = Message.raw(text);
            if (state.color != null) msg = msg.color(state.color);
            if (state.bold) msg = msg.bold(true);
            if (state.italic) msg = msg.italic(true);
            return msg;
        }

        private static Color parseHexColor(String hex6) {
            try {
                return new Color(Integer.parseInt(hex6, 16));
            } catch (Exception ignored) {
                return null;
            }
        }

        private static List<Color> parseGradientStops(String stopSpec) {
            // stopSpec example: "#FF0000:#00FF00:#0000FF"
            String[] raw = stopSpec.split(":");
            List<Color> colors = new ArrayList<>();
            for (String part : raw) {
                String p = part.trim();
                if (p.startsWith("#")) p = p.substring(1);
                if (p.matches("[0-9A-Fa-f]{6}")) {
                    Color c = parseHexColor(p);
                    if (c != null) colors.add(c);
                } else {
                    // allow named stops too
                    Color named = NAMED.get(p.toLowerCase(Locale.ROOT));
                    if (named != null) colors.add(named);
                }
            }
            return colors;
        }

        private static int findClosingTag(String input, int fromIndex, String tagName) {
            // minimal: find first </tagName>
            String needle = "</" + tagName.toLowerCase(Locale.ROOT) + ">";
            return input.toLowerCase(Locale.ROOT).indexOf(needle, fromIndex);
        }

        private static List<Message> applyGradientToText(String text, List<Color> stops, State base) {
            List<Message> out = new ArrayList<>();
            if (text == null || text.isEmpty()) return out;

            // Only color letters/digits/spaces as characters; keep formatting from base state
            int n = text.length();
            if (n == 0) return out;

            // Build one message per char (safe + simple)
            for (int idx = 0; idx < n; idx++) {
                char ch = text.charAt(idx);
                Color c = gradientColorAt(stops, n, idx);
                State s = base.copy();
                s.color = c;
                out.add(buildSegment(String.valueOf(ch), s));
            }
            return out;
        }

        private static String applyGradientLegacy(String text, List<Color> stops) {
            if (text == null || text.isEmpty()) return "";

            StringBuilder out = new StringBuilder(text.length() * 10);
            int n = text.length();
            for (int idx = 0; idx < n; idx++) {
                Color c = gradientColorAt(stops, n, idx);
                String hex = String.format("%06X", c.getRGB() & 0xFFFFFF);
                out.append("&#").append(hex).append(text.charAt(idx));
            }
            return out.toString();
        }

        private static Color gradientColorAt(List<Color> stops, int length, int index) {
            if (stops == null || stops.size() < 2) return DEFAULT_COLOR;
            if (length <= 1) return stops.get(0);

            double t = index / (double) (length - 1);

            int segments = stops.size() - 1;
            double segT = t * segments;
            int segIndex = Math.min(segments - 1, (int) Math.floor(segT));
            double localT = segT - segIndex;

            Color a = stops.get(segIndex);
            Color b = stops.get(segIndex + 1);

            int r = (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * localT);
            int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * localT);
            int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue())  * localT);

            r = clamp255(r); g = clamp255(g); bl = clamp255(bl);
            return new Color(r, g, bl);
        }

        private static int clamp255(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

    // ------------------------------------------------------------
    // MiniMessage subset support (converted to legacy formatting)
    // ------------------------------------------------------------
    private static final class MiniMessageSupport {

        private static final Map<String, String> NAMED_HEX = new HashMap<>();
        static {
            NAMED_HEX.put("black", "000000");
            NAMED_HEX.put("white", "FFFFFF");
            NAMED_HEX.put("gray", "AAAAAA");
            NAMED_HEX.put("dark_gray", "555555");
            NAMED_HEX.put("red", "FF5555");
            NAMED_HEX.put("dark_red", "AA0000");
            NAMED_HEX.put("green", "55FF55");
            NAMED_HEX.put("dark_green", "00AA00");
            NAMED_HEX.put("blue", "5555FF");
            NAMED_HEX.put("dark_blue", "0000AA");
            NAMED_HEX.put("aqua", "55FFFF");
            NAMED_HEX.put("dark_aqua", "00AAAA");
            NAMED_HEX.put("yellow", "FFFF55");
            NAMED_HEX.put("gold", "FFAA00");
            NAMED_HEX.put("light_purple", "FF55FF");
            NAMED_HEX.put("dark_purple", "AA00AA");
        }

        // <gradient:#FF0000:#00FF00[:...]>text</gradient>
        private static final Pattern GRADIENT_OPEN = Pattern.compile("(?is)<gradient:([^>]+)>");
        private static final Pattern GRADIENT_CLOSE = Pattern.compile("(?is)</gradient>");
        private static final Pattern TAG_ANY = Pattern.compile("(?is)</?[^>]+>");

        private static final Pattern HEX_TAG = Pattern.compile("^#([0-9A-Fa-f]{6})$");

        static String miniToLegacy(String input) {
            if (input == null || input.isEmpty()) return input;
            if (input.indexOf('<') < 0 || input.indexOf('>') < 0) return input;

            // 1) Handle gradients first (supports multiple stops)
            String out = applyGradients(input);

            // 2) Replace simple tags (<#RRGGBB>, <red>, <bold>, <italic>, <reset>) with legacy
            out = replaceSimpleTags(out);

            return out;
        }

        static String stripMiniTags(String input) {
            if (input == null || input.isEmpty()) return input;
            if (input.indexOf('<') < 0 || input.indexOf('>') < 0) return input;

            // unwrap gradients: keep content
            String out = input;
            out = out.replaceAll("(?is)<gradient:[^>]+>", "");
            out = out.replaceAll("(?is)</gradient>", "");

            // remove all other minimessage tags, keep content
            out = TAG_ANY.matcher(out).replaceAll("");

            return out;
        }

        private static String applyGradients(String input) {
            String out = input;

            // Iterate while there is a gradient tag
            while (true) {
                Matcher open = GRADIENT_OPEN.matcher(out);
                if (!open.find()) break;

                int openStart = open.start();
                int openEnd = open.end();
                String stopSpec = open.group(1);

                Matcher close = GRADIENT_CLOSE.matcher(out);
                close.region(openEnd, out.length());
                if (!close.find()) break; // no close tag -> leave as-is

                int closeStart = close.start();
                int closeEnd = close.end();

                String inner = out.substring(openEnd, closeStart);

                List<Color> stops = parseStops(stopSpec);
                String replaced = (stops.size() >= 2) ? gradientLegacy(inner, stops) : inner;

                out = out.substring(0, openStart) + replaced + out.substring(closeEnd);
            }

            return out;
        }

        private static String replaceSimpleTags(String input) {
            StringBuilder sb = new StringBuilder(input.length() + 16);

            int i = 0;
            while (i < input.length()) {
                char ch = input.charAt(i);
                if (ch == '<') {
                    int close = input.indexOf('>', i + 1);
                    if (close > i) {
                        String raw = input.substring(i + 1, close).trim();
                        boolean isClose = raw.startsWith("/");
                        String tag = isClose ? raw.substring(1).trim().toLowerCase(Locale.ROOT)
                                : raw.toLowerCase(Locale.ROOT);

                        // closing tags: output &r to “end” styles safely
                        if (isClose) {
                            sb.append("&r");
                            i = close + 1;
                            continue;
                        }

                        // formatting
                        if ("bold".equals(tag)) { sb.append("&l"); i = close + 1; continue; }
                        if ("italic".equals(tag)) { sb.append("&o"); i = close + 1; continue; }
                        if ("reset".equals(tag)) { sb.append("&r"); i = close + 1; continue; }

                        // <#RRGGBB>
                        Matcher m = HEX_TAG.matcher(tag);
                        if (m.matches()) {
                            sb.append("&#").append(m.group(1).toUpperCase(Locale.ROOT));
                            i = close + 1;
                            continue;
                        }

                        // <red> etc.
                        String named = NAMED_HEX.get(tag);
                        if (named != null) {
                            sb.append("&#").append(named);
                            i = close + 1;
                            continue;
                        }

                        // unknown tag -> drop it (or keep literal if you prefer)
                        i = close + 1;
                        continue;
                    }
                }

                sb.append(ch);
                i++;
            }

            return sb.toString();
        }

        private static List<Color> parseStops(String stopSpec) {
            // "#FF0000:#00FF00" or "red:blue" etc.
            String[] parts = stopSpec.split(":");
            List<Color> colors = new ArrayList<>();
            for (String p0 : parts) {
                String p = p0.trim();
                if (p.startsWith("#")) p = p.substring(1);

                if (p.matches("[0-9A-Fa-f]{6}")) {
                    colors.add(new Color(Integer.parseInt(p, 16)));
                    continue;
                }

                String named = NAMED_HEX.get(p.toLowerCase(Locale.ROOT));
                if (named != null) {
                    colors.add(new Color(Integer.parseInt(named, 16)));
                }
            }
            return colors;
        }

        private static String gradientLegacy(String text, List<Color> stops) {
            if (text == null || text.isEmpty()) return "";
            int n = text.length();
            if (n <= 1) {
                Color c = stops.get(0);
                return "&#" + String.format("%06X", c.getRGB() & 0xFFFFFF) + text;
            }

            StringBuilder out = new StringBuilder(n * 10);
            for (int idx = 0; idx < n; idx++) {
                Color c = gradientColorAt(stops, n, idx);
                out.append("&#").append(String.format("%06X", c.getRGB() & 0xFFFFFF))
                        .append(text.charAt(idx));
            }
            return out.toString();
        }

        private static Color gradientColorAt(List<Color> stops, int length, int index) {
            double t = index / (double) (length - 1);

            int segments = stops.size() - 1;
            double segT = t * segments;
            int segIndex = Math.min(segments - 1, (int) Math.floor(segT));
            double localT = segT - segIndex;

            Color a = stops.get(segIndex);
            Color b = stops.get(segIndex + 1);

            int r = (int) Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * localT);
            int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * localT);
            int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue())  * localT);

            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            bl = Math.max(0, Math.min(255, bl));
            return new Color(r, g, bl);
        }
    }
}
