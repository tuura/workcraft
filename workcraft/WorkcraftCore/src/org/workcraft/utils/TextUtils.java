package org.workcraft.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TextUtils {

    public static final int DEFAULT_WRAP_LENGTH = 100;
    private static final String ELLIPSIS_SYMBOL = Character.toString((char) 0x2026);

    public static String truncateText(String text, int length) {
        StringBuffer result = new StringBuffer();
        boolean firstLine = true;
        for (String line : splitLines(text)) {
            if (firstLine) {
                firstLine = false;
            } else {
                result.append("\n");
            }
            result.append(truncateLine(line, length));
        }
        return result.toString();
    }

    public static String truncateLine(String line, int length) {
        if (line.length() <= length) {
            return line;
        }
        StringBuffer result = new StringBuffer();
        int curLength = 0;
        for (String word : splitWords(line)) {
            int wordLength = word.length();
            if (curLength > 0) {
                if (curLength + wordLength < length) {
                    result.append(" ");
                } else {
                    result.append(ELLIPSIS_SYMBOL);
                    break;
                }
            }
            result.append(word);
            curLength += wordLength;
        }
        return result.toString();
    }

    public static String wrapText(String text) {
        return wrapText(text, DEFAULT_WRAP_LENGTH);
    }

    public static String wrapText(String text, int length) {
        StringBuffer result = new StringBuffer();
        boolean firstLine = true;
        for (String line : splitLines(text)) {
            if (firstLine) {
                firstLine = false;
            } else {
                result.append("\n");
            }
            result.append(wrapLine(line, length));
        }
        return result.toString();
    }

    public static String wrapLine(String line) {
        return wrapLine(line, DEFAULT_WRAP_LENGTH);
    }

    public static String wrapLine(String line, int length) {
        if (line.length() <= length) {
            return line;
        }
        StringBuffer result = new StringBuffer();
        int curLength = 0;
        for (String word : splitWords(line)) {
            int wordLength = word.length();
            if (curLength > 0) {
                if (curLength + wordLength < length) {
                    result.append(" ");
                    curLength++;
                } else {
                    result.append("\n");
                    curLength = 0;
                }
            }
            result.append(word);
            curLength += wordLength;
        }
        return result.toString();
    }

    public static List<String> splitLines(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\\r?\\n", -1));
    }

    public static List<String> splitWords(String text) {
        if ((text == null) || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.trim().split("\\s+", -1));
    }

    public static String getHeadAndTail(String text, int firstCount, int lastCount) {
        StringBuffer result = new StringBuffer();
        List<String> lines = splitLines(text);
        int index = 0;
        boolean dotsInserted = false;
        for (String line : lines) {
            if ((index < firstCount) || (index >= lines.size() - lastCount)) {
                if (index > 0) {
                    result.append("\n");
                }
                result.append(line);
            } else if (!dotsInserted) {
                result.append("\n");
                result.append(ELLIPSIS_SYMBOL);
                dotsInserted = true;
            }
            index++;
        }
        return result.toString();
    }

    public static String wrapItems(Collection<String> items) {
        return wrapItems(items, DEFAULT_WRAP_LENGTH);
    }

    public static String wrapItems(Collection<String> items, int length) {
        return wrapText(String.join(", ", items), length);
    }

    public static String wrapMessageWithItems(String message, Collection<String> items) {
        return wrapMessageWithItems(message, items, DEFAULT_WRAP_LENGTH);
    }

    public static String wrapMessageWithItems(String message, Collection<String> items, int length) {
        if ((items == null) || items.isEmpty()) {
            return message;
        }
        if (items.size() == 1) {
            return message + " '" + items.iterator().next() + "'";
        }
        String text = makePlural(message) + ":";
        String str = String.join(", ", items);
        if (text.length() + str.length() > length) {
            text += "\n";
        } else {
            text += " ";
        }
        return text + wrapItems(items, length);
    }

    public static String makePlural(String word) {
        if (word.endsWith("y") && !word.endsWith("ay") && !word.endsWith("ey")
                && !word.endsWith("iy") && !word.endsWith("oy") && !word.endsWith("uy")) {

            return word.substring(0, word.length() - 1) + "ies";
        }
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("z") || word.endsWith("ch") || word.endsWith("sh")) {
            return word + "es";
        }
        return word + "s";
    }

    public static String repeat(String str, int count) {
        return String.join("", Collections.nCopies(count, str));
    }

    public static String escapeHtml(String str) {
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < str.length(); i++) {
            c = str.charAt(i);
            switch (c) {
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '&':
                sb.append("&amp;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            default:
                sb.append(c);
                break;
            }
        }
        return sb.toString();
    }

}
