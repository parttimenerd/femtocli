package me.bechberger.femtocli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny utility for help rendering. The layout logic lives in generated code;
 * only word-wrap and aligned-entry printing are shared here.
 */
public final class HelpUtil {

    private HelpUtil() {}

    /** Print a label + description pair, aligned to {@code descCol}. */
    public static void printAligned(PrintStream out, String label, String description,
                                    int descCol, int maxLineWidth) {
        int maxDescW = maxLineWidth - descCol;
        List<String> descLines = wrapLines(description, maxDescW);

        if (label.length() < descCol) {
            if (descLines.isEmpty()) {
                out.println(label);
            } else {
                out.printf("%-" + descCol + "s%s%n", label, descLines.get(0));
                for (int i = 1; i < descLines.size(); i++)
                    out.printf("%-" + descCol + "s%s%n", "", descLines.get(i));
            }
        } else {
            out.println(label);
            for (String dl : descLines)
                out.printf("%-" + descCol + "s%s%n", "", dl);
        }
    }

    /** Word-wrap a text block, keeping first line at current indent and subsequent at {@code indent}. */
    public static String wrapBlock(String text, int maxWidth, int indent) {
        if (text == null || text.isEmpty() || maxWidth <= 0 || text.length() <= maxWidth) {
            return text == null ? "" : text;
        }
        String pad = " ".repeat(Math.max(0, indent));
        List<String> lines = wrapLines(text, maxWidth);
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(lines.get(0));
        for (int i = 1; i < lines.size(); i++) sb.append("\n").append(pad).append(lines.get(i));
        return sb.toString();
    }

    /** Split text into lines no wider than {@code maxWidth}. */
    public static List<String> wrapLines(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            if (maxWidth <= 0 || line.length() <= maxWidth) {
                result.add(line);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String word : line.split(" ")) {
                    if (sb.isEmpty()) sb.append(word);
                    else if (sb.length() + 1 + word.length() <= maxWidth) sb.append(" ").append(word);
                    else { result.add(sb.toString()); sb.setLength(0); sb.append(word); }
                }
                if (!sb.isEmpty()) result.add(sb.toString());
            }
        }
        return result.isEmpty() ? List.of(text) : result;
    }
}
