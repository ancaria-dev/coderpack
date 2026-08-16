package dev.ancaria.coderpack.zygote;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of the wire protocol. Flat key/value rather than JSON: the JDK ships
 * no JSON parser, and a line like {@code ASK 3 health.damage damage=553} stays
 * readable in a terminal, which is most of what this layer does.
 */
public record Frame(String verb, long seq, String name, Map<String, String> fields) {

    public static Frame parse(String line) {
        String[] parts = line.strip().split(" ");
        if (parts.length < 1 || parts[0].isEmpty()) {
            return null;
        }
        if ("BYE".equals(parts[0])) {
            return new Frame("BYE", 0, "", Map.of());
        }
        if (parts.length < 2) {
            return null;
        }
        long seq;
        try {
            seq = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        // A token carrying '=' is a field, never the name -- frames without a
        // name exist, and treating the third token as one loses their fields.
        String name = "";
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 2; i < parts.length; i++) {
            int split = parts[i].indexOf('=');
            if (split > 0) {
                fields.put(decode(parts[i].substring(0, split)),
                           decode(parts[i].substring(split + 1)));
            } else if (name.isEmpty()) {
                name = decode(parts[i]);
            }
        }
        return new Frame(parts[0], seq, name, fields);
    }

    public String encode() {
        StringBuilder out = new StringBuilder(verb).append(' ').append(seq);
        if (!name.isEmpty()) {
            out.append(' ').append(name);
        }
        fields.forEach((key, value) ->
                out.append(' ').append(encode(key)).append('=').append(encode(value)));
        return out.toString();
    }

    /** Only what would break the line format is escaped, so values stay readable. */
    static String encode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '%' -> out.append("%25");
                case ' ' -> out.append("%20");
                case '=' -> out.append("%3D");
                case '\n' -> out.append("%0A");
                case '\r' -> out.append("%0D");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String decode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                try {
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) {
                    // not an escape after all; keep the literal '%'
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
