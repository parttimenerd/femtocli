package me.bechberger.femtocli;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for "agent args" strings.
 * <p>
 * Agent args are a single comma-separated string that is translated into the usual {@code String[]} argv.
 * This is useful when passing arguments through systems that make spaces/quoting difficult.
 * <p>
 * Format:
 * <ul>
 *   <li>Tokens are separated by commas (",").</li>
 *   <li>Backslash escapes: {@code \\} (backslash), {@code \,} (comma), {@code \=} (equals).</li>
 *   <li>Single-quoted tokens are supported: commas/spaces inside quotes are treated as part of the token.</li>
 *   <li>Whitespace around tokens is trimmed (outside of quotes).</li>
 *   <li>Empty tokens (caused by ",," or a trailing comma) are rejected; use {@code --opt=} to pass an empty value.</li>
 * </ul>
 */
final class AgentArgs {

    private AgentArgs() {}

    static String[] toArgv(String agentArgs) {
        if (agentArgs == null) {
            throw new IllegalArgumentException("agentArgs must not be null");
        }
        if (agentArgs.isBlank()) {
            return new String[0];
        }

        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean escaping = false;
        boolean inSingleQuotes = false;
        // Track character positions that are "protected" (from quotes or escapes)
        // so that trimming only affects unprotected leading/trailing whitespace.
        List<Boolean> protectedChars = new ArrayList<>();

        for (int i = 0; i < agentArgs.length(); i++) {
            char c = agentArgs.charAt(i);

            if (escaping) {
                // Only a small escape set is supported.
                if (c == '\\' || c == ',' || c == '=') {
                    cur.append(c);
                    protectedChars.add(true);
                } else {
                    throw new IllegalArgumentException("Invalid escape sequence: \\" + c + " at index " + i);
                }
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }

            if (c == '\'') {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (!inSingleQuotes && c == ',') {
                addToken(out, cur, protectedChars);
                cur.setLength(0);
                protectedChars.clear();
                continue;
            }

            cur.append(c);
            protectedChars.add(inSingleQuotes);
        }

        if (escaping) {
            throw new IllegalArgumentException("Dangling escape at end of agent args");
        }
        if (inSingleQuotes) {
            throw new IllegalArgumentException("Unterminated single quote in agent args");
        }
        addToken(out, cur, protectedChars);

        return out.toArray(String[]::new);
    }

    private static void addToken(List<String> out, StringBuilder cur, List<Boolean> protectedChars) {
        String raw = cur.toString();
        // Trim only unprotected leading/trailing whitespace
        int start = 0;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))
                && !protectedChars.get(start)) {
            start++;
        }
        int end = raw.length();
        while (end > start && Character.isWhitespace(raw.charAt(end - 1))
                && !protectedChars.get(end - 1)) {
            end--;
        }
        String token = raw.substring(start, end);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Empty token in agent args (did you use ',,' or a trailing comma?)");
        }
        out.add(token);
    }
}