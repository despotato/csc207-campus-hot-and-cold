package campus.infrastructure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small JSON reader and writer so the project keeps zero runtime dependencies.
 * Objects become {@code Map<String, Object>}, arrays {@code List<Object>}, numbers {@code Double}.
 */
public final class Json {
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json reader = new Json(text);
        Object value = reader.value();
        reader.whitespace();
        if (reader.at != text.length()) {
            throw reader.error("Unexpected trailing characters");
        }
        return value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected a JSON object.");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("Expected a JSON array.");
    }

    public static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Expected a JSON number.");
    }

    private Object value() {
        whitespace();
        if (at >= text.length()) {
            throw error("Unexpected end of input");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> objectValue();
            case '[' -> arrayValue();
            case '"' -> stringValue();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> numberValue();
        };
    }

    private Map<String, Object> objectValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        at++;
        whitespace();
        if (peek('}')) {
            at++;
            return result;
        }
        while (true) {
            whitespace();
            if (!peek('"')) {
                throw error("Expected a property name");
            }
            String key = stringValue();
            whitespace();
            expect(':');
            result.put(key, value());
            whitespace();
            if (peek(',')) {
                at++;
            } else {
                expect('}');
                return result;
            }
        }
    }

    private List<Object> arrayValue() {
        List<Object> result = new ArrayList<>();
        at++;
        whitespace();
        if (peek(']')) {
            at++;
            return result;
        }
        while (true) {
            result.add(value());
            whitespace();
            if (peek(',')) {
                at++;
            } else {
                expect(']');
                return result;
            }
        }
    }

    private String stringValue() {
        StringBuilder out = new StringBuilder();
        at++;
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char escape = text.charAt(at++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("Bad unicode escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw error("Bad escape");
            }
        }
        throw error("Unterminated string");
    }

    private Double numberValue() {
        int begin = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        if (begin == at) {
            throw error("Unexpected character");
        }
        try {
            return Double.valueOf(text.substring(begin, at));
        } catch (NumberFormatException exception) {
            throw error("Bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("Unexpected token");
        }
        at += word.length();
        return value;
    }

    private void whitespace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private boolean peek(char c) {
        return at < text.length() && text.charAt(at) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        at++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + at + ".");
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(s, out);
            case Boolean b -> out.append(b);
            case Integer i -> out.append(i);
            case Long l -> out.append(l);
            case Number n -> {
                double d = n.doubleValue();
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException("JSON numbers must be finite.");
                }
                out.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d)
                        : String.format(Locale.ROOT, "%.7f", d).replaceAll("0+$", ""));
            }
            case Map<?, ?> map -> {
                out.append('{');
                Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<?, ?> entry = entries.next();
                    quote(String.valueOf(entry.getKey()), out);
                    out.append(':');
                    write(entry.getValue(), out);
                    if (entries.hasNext()) {
                        out.append(',');
                    }
                }
                out.append('}');
            }
            case Iterable<?> items -> {
                out.append('[');
                boolean first = true;
                for (Object item : items) {
                    if (!first) {
                        out.append(',');
                    }
                    write(item, out);
                    first = false;
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("Cannot write " + value.getClass().getSimpleName());
        }
    }

    private static void quote(String s, StringBuilder out) {
        out.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    // Escape HTML-significant and control characters so JSON is safe to embed anywhere.
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == ' ' || c == ' ') {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
