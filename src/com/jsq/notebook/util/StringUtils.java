package com.jsq.notebook.util;

import java.util.Vector;

/**
 * String utilities tailored for CLDC 1.1 / BlackBerry OS 5.0.
 * Eliminates dependencies on post-Java 1.4 methods (e.g., String.split, String.contains).
 */
public final class StringUtils {

    private StringUtils() {
    }


    /**
     * Splits a string by a delimiter character, returning a Vector of String elements.
     */
    public static Vector split(String source, char delimiter) {
        Vector result = new Vector();
        if (source == null) {
            return result;
        }

        int len = source.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            if (source.charAt(i) == delimiter) {
                result.addElement(source.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= len) {
            result.addElement(source.substring(start));
        }
        return result;
    }

    /**
     * Splits a string by lines (\r\n, \n, or \r).
     */
    public static Vector splitLines(String source) {
        Vector result = new Vector();
        if (source == null) {
            return result;
        }

        int len = source.length();
        int start = 0;
        int i = 0;
        while (i < len) {
            char c = source.charAt(i);
            if (c == '\r') {
                result.addElement(source.substring(start, i));
                if (i + 1 < len && source.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            } else if (c == '\n') {
                result.addElement(source.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        if (start <= len) {
            result.addElement(source.substring(start));
        }
        return result;
    }

    /**
     * Replaces occurrences of target with replacement in source.
     * Uses StringBuffer to prevent object thrashing.
     */
    public static String replace(String source, String target, String replacement) {
        if (source == null || target == null || replacement == null || target.length() == 0) {
            return source;
        }

        StringBuffer sb = new StringBuffer();
        int start = 0;
        int idx = 0;
        int targetLen = target.length();

        while ((idx = source.indexOf(target, start)) >= 0) {
            sb.append(source.substring(start, idx));
            sb.append(replacement);
            start = idx + targetLen;
        }
        sb.append(source.substring(start));
        return sb.toString();
    }

    /**
     * Sanitizes a string for use as a file or directory name on SD Card.
     */
    public static String sanitizeFileName(String name) {
        if (name == null || name.trim().length() == 0) {
            return "untitled";
        }

        StringBuffer sb = new StringBuffer();
        int len = name.length();
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' ||
                c == '"' || c == '<' || c == '>' || c == '|' || c < 32) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String res = sb.toString().trim();
        return res.length() == 0 ? "untitled" : res;
    }

    /**
     * Escapes standard HTML special characters.
     */
    public static String htmlEscape(String text) {
        if (text == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(text.length() + 16);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Checks if a string starts with a given prefix, ignoring case.
     */
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        int pLen = prefix.length();
        if (str.length() < pLen) {
            return false;
        }
        for (int i = 0; i < pLen; i++) {
            char c1 = Character.toLowerCase(str.charAt(i));
            char c2 = Character.toLowerCase(prefix.charAt(i));
            if (c1 != c2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a string contains only numeric digits.
     */
    public static boolean isDigits(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
