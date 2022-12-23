package io.github.kidofcubes;


/*
    Euphoria color calculation code taken from https://github.com/CylonicRaider/euphoria-app/blob/master/app/src/main/java/io/euphoria/xkcd/app/impl/ui/UIUtils.java
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class ColorUtils {
    // Color saturation and lightness for input bar and messages
    public static final float COLOR_SENDER_SATURATION = 0.85f; //ORIG=0.65f tweaked higher for mc
    public static final float COLOR_SENDER_LIGHTNESS = 0.85f;

    // Color saturation and lightness for emotes
    private static final float COLOR_EMOTE_SATURATION = 0.65f;
    private static final float COLOR_EMOTE_LIGHTNESS = 0.9f; // Euphoria has 0.95f

    // Color saturation and lightness for @mentions
    public static final float COLOR_AT_SATURATION = 0.42f;
    public static final float COLOR_AT_LIGHTNESS = 0.50f;

    private static final Pattern WHITESPACE_TRIMMING_RE = Pattern.compile("^\\p{Z}+|\\p{Z}+$");

    // TODO check against actual list of valid emoji
    private static final Pattern EMOJI_RE = Pattern.compile(":[a-zA-Z!?\\-]+?:");

    // Emote message testing
    public static final Pattern EMOTE_RE = Pattern.compile("^/me");
    public static final int MAX_EMOTE_LENGTH = 240;

    // Hue hash cache
    private static final Map<String, Double> HUE_CACHE = new HashMap<>();

    /*                                                                       *
     * Hue hashing code ported to Java from                                  *
     * https://github.com/euphoria-io/heim/blob/master/client/lib/hueHash.js *
     *                                                                       */

    /**
     * Raw hue hash implementation
     * Inline comments are original; block comments are porters' notes.
     */
    private static double hueHash(String text, double offset) {
        // DJBX33A-ish
        double val = 0.0;
        for (int i = 0; i < text.length(); i++) {
            // scramble char codes across [0-255]
            // prime multiple chosen so @greenie can green, and @redtaboo red.
            double charVal = (text.charAt(i) * 439.0) % 256.0;

            // multiply val by 33 while constraining within signed 32 bit int range.
            // this keeps the value within Number.MAX_SAFE_INTEGER without throwing out
            // information.
            double origVal = val;
            /* Double cast to avoid integer saturation; recall that the shift is applied after the casts */
            val = (int) (long) val << 5;
            val += origVal;

            // add the character information to the hash.
            val += charVal;
        }

        // cast the result of the final character addition to a 32 bit int.
        val = (int) (long) val;

        // add the minimum possible value, to ensure that val is positive (without
        // throwing out information).
        /* Original has Math.pow(2.0, 31.0) */
        val += 1L << 31;

        // add the calibration offset and scale within 0-254 (an arbitrary range kept
        // for consistency with prior behavior).
        return (val + offset) % 255.0;
    }

    private static final double greenieOffset = 148.0 - hueHash("greenie", 0.0);

    /** Normalize a string for hue hash processing */
    private static String normalize(String text) {
        return EMOJI_RE.matcher(text).replaceAll("").replaceAll("[^\\w_\\-]", "").toLowerCase();
    }

    /**
     * Trim all unicode whitespace characters from the start and end of the passed String.
     *
     * String::trim() uses a non-unicode-aware (or only partially so) concept of whitespace characters.
     *
     * @param text The string to be trimmed
     * @return The passed string with all whitespace characters at the start and end of it removed
     */
    public static String trimUnicodeWhitespace(String text) {
        return WHITESPACE_TRIMMING_RE.matcher(text).replaceAll("");
    }

    /**
     * Obtain the hue associated with the given nickname
     * Use the *Color methods to compose ready-to-use colors.
     * @param text The nickname whose hue to obtain
     * @return The hue corresponding to <code>text</code>
     */
    public static double hue(String text) {
        String normalized = normalize(text);

        if (normalized.isEmpty()) {
            normalized = text;
        }

        Double ret = HUE_CACHE.get(normalized);
        if (ret == null) {
            ret = hueHash(normalized, greenieOffset);
            HUE_CACHE.put(normalized, ret);
        }
        return ret;
    }

    /**
     * Converts an HSLA color value to an integer representing the color in 0xAARRGGBB format
     * if h > 360 or h < 0: h = h%360
     * if s > 1 or s < 0: s = max(min(s,1),0)
     * if l > 1 or l < 0: l = max(min(l,1),0)
     * if a > 1 or a < 0: a = max(min(a,1),0)
     *
     * @param h Hue (0.0 -- 360.0)
     * @param s Saturation (0.0 -- 1.0)
     * @param l Lightness (0.0 -- 1.0)
     * @param a Opacity (0.0 -- 1.0)
     * @return RGB-ColorInt
     */
    public static int hslaToRgbaInt(double h, double s, double l, double a) {
        // ensure proper values
        h = (h % 360 + 360) % 360; // real modulus not just remainder
        s = Math.max(Math.min(s, 1f), 0f);
        l = Math.max(Math.min(l, 1f), 0f);
        a = Math.max(Math.min(a, 1f), 0f);
        // convert
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs(h / 60f % 2 - 1));
        double m = l - c / 2;
        double r = 0, g = 0, b = 0;
        if (h >= 0 && h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h >= 60 && h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h >= 120 && h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h >= 180 && h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h >= 240 && h < 300) {
            r = x;
            g = 0;
            b = c;
        } else if (h >= 300 && h < 360) {
            r = c;
            g = 0;
            b = x;
        }
        r = Math.max(Math.min((r + m) * 255, 255f), 0f);
        g = Math.max(Math.min((g + m) * 255, 255f), 0f);
        b = Math.max(Math.min((b + m) * 255, 255f), 0f);
        a = Math.max(Math.min(a * 255, 255f), 0f);
        return ((int) a << 8 * 3) + ((int) r << 8 * 2) + ((int) g << 8) + (int) b;
    }

    /**
     * Convert an HSL color value to an integer representing the color in 0xFFRRGGBB format
     * @see #hslaToRgbaInt(double, double, double,double)
     */
    public static int hslToRgbInt(double h, double s, double l) {
        return hslaToRgbaInt(h, s, l, 1f);
    }

    /**
     * Obtain the color corresponding to the given nickname for use as a background
     * @param name The nickname to colorize
     * @return The color corresponding to <code>name</code>
     */
    public static int nickColor(String name) {
        return hslToRgbInt(hue(name), COLOR_SENDER_SATURATION, COLOR_SENDER_LIGHTNESS);
    }

    /**
     * Obtain the color corresponding to the given nickname for use as a light background
     *
     * @param name The nickname to colorize
     * @return the color corresponding to <code>name</code>
     */
    public static int emoteColor(String name) {
        return hslToRgbInt(hue(name), COLOR_EMOTE_SATURATION, COLOR_EMOTE_LIGHTNESS);
    }

    /**
     * Obtain the color corresponding to the given nickname for use as a text color
     * @param name The nickname to colorize
     * @return The color corresponding to <code>name</code>
     */
    public static int mentionColor(String name) {
        return hslToRgbInt(hue(name), COLOR_AT_SATURATION, COLOR_AT_LIGHTNESS);
    }

    /**
     * Test whether the given message content string corresponds to an emote message
     *
     * @param text The message content to test
     * @return Whether <code>text</code> is an emote message text
     */
    public static boolean isEmote(String text) {
        return text.length() < MAX_EMOTE_LENGTH && EMOTE_RE.matcher(text).find();
    }



    /**
     * Null-safe method for comparing objects.
     *
     * @param a An arbitrary object, or null.
     * @param b An arbitrary object, or null.
     * @return {@code (a == null) ? (b == null) : a.equals(b)}
     */
    public static boolean equalsOrNull(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Null-safe hash code calculation method.
     *
     * @param a An arbitrary object, or null.
     * @return {@code (a == null) ? 0 : a.hashCode()}
     */
    public static int hashCodeOrNull(Object a) {
        return (a == null) ? 0 : a.hashCode();
    }
}
