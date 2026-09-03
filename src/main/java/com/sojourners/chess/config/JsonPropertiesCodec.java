package com.sojourners.chess.config;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.openbook.MoveRule;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Properties 文件的 JSON 序列化/反序列化（IT-9.1/9.2 反射驱动版）。
 * 字段映射自动化：key 与 v1.x 手写版完全一致，旧 properties.json 无损兼容（IT-9.3）；
 * 手写 Parser 保留为 JSON 底层，维持零第三方依赖。
 * 新增配置字段无需再同步修改本类（engineConfigList 的 engines 结构除外）。
 */
public final class JsonPropertiesCodec {

    private static final Map<String, Field> FIELDS = new HashMap<>();

    static {
        for (Field f : Properties.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || f.isSynthetic()) {
                continue;
            }
            if ("engineConfigList".equals(f.getName())) {
                continue; // engines 结构单独处理
            }
            f.setAccessible(true);
            FIELDS.put(f.getName(), f);
        }
    }

    private JsonPropertiesCodec() {}

    /**
     * Serializes a Properties instance to JSON.
     *
     * @param p the Properties to serialize
     * @return the JSON string
     */
    public static String toJson(Properties p) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Field> en : FIELDS.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            Field f = en.getValue();
            try {
                writeValue(sb, en.getKey(), f.get(p), f.getType());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("读取配置字段失败: " + en.getKey(), e);
            }
        }
        sb.append(",\"engines\":");
        writeEngines(sb, p.getEngineConfigList());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Writes a single key-value pair to the JSON builder, dispatching on type.
     *
     * @param sb    the string builder
     * @param key   the field name
     * @param value the field value
     * @param type  the field type
     */
    private static void writeValue(StringBuilder sb, String key, Object value, Class<?> type) {
        writeString(sb, key);
        sb.append(':');
        if (value == null) {
            sb.append("null");
        } else if (type == String.class) {
            writeString(sb, (String) value);
        } else if (type == boolean.class || type == Boolean.class) {
            sb.append(value);
        } else if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            sb.append(((Number) value).longValue());
        } else if (type == double.class || type == Double.class) {
            sb.append(d2s((Double) value));
        } else if (type == float.class || type == Float.class) {
            sb.append(d2s(((Number) value).doubleValue()));
        } else if (type.isEnum()) {
            writeString(sb, ((Enum<?>) value).name());
        } else if (List.class.isAssignableFrom(type)) {
            writeStringList(sb, key, (List<?>) value);
        } else {
            throw new RuntimeException("不支持的配置字段类型: " + key + " -> " + type);
        }
    }

    /**
     * Deserializes a JSON string to a Properties instance.
     *
     * @param json the JSON string
     * @return the Properties instance
     */
    public static Properties fromJson(String json) {
        Parser p = new Parser(json);
        JsonValue root = p.parseValue();
        Properties props = Properties.defaults();
        if (root.type != JsonValue.Type.OBJECT) return props;
        for (Map.Entry<String, JsonValue> en : root.object.entrySet()) {
            if (!"engines".equals(en.getKey())) {
                applyField(props, en.getKey(), en.getValue());
            }
        }
        JsonValue engines = root.object.get("engines");
        if (engines != null && engines.type == JsonValue.Type.ARRAY) {
            List<EngineConfig> list = new ArrayList<>();
            for (JsonValue ev : engines.array) {
                String name = strOrNull(ev.object.get("name"));
                String path = strOrNull(ev.object.get("path"));
                String proto = strOrNull(ev.object.get("protocol"));
                LinkedHashMap<String, String> opts = new LinkedHashMap<>();
                JsonValue options = ev.object.get("options");
                if (options != null && options.type == JsonValue.Type.OBJECT) {
                    for (Map.Entry<String, JsonValue> oe : options.object.entrySet()) {
                        opts.put(oe.getKey(), strOrNull(oe.getValue()));
                    }
                }
                list.add(new EngineConfig(name, path, proto, opts));
            }
            props.setEngineConfigList(list);
        }
        return props;
    }

    /**
     * Extracts a string from a JsonValue, returning null for null/NULL types.
     *
     * @param v the JsonValue
     * @return the string value, or null
     */
    private static String strOrNull(JsonValue v) {
        if (v == null || v.type == JsonValue.Type.NULL) return null;
        if (v.type == JsonValue.Type.STRING) return v.str;
        return String.valueOf(v.number);
    }

    /**
     * Applies a single field from JSON to the Properties instance, type-checking and converting as needed.
     *
     * @param props the Properties instance to update
     * @param key   the field name
     * @param v     the JSON value
     */
    private static void applyField(Properties props, String key, JsonValue v) {
        Field f = FIELDS.get(key);
        if (f == null) {
            return; // 未知字段忽略
        }
        try {
            Class<?> type = f.getType();
            if (type == boolean.class) {
                if (v.isBool()) f.setBoolean(props, v.bool);
            } else if (type == int.class) {
                if (v.isNum()) f.setInt(props, (int) v.number);
            } else if (type == long.class) {
                if (v.isNum()) f.setLong(props, (long) v.number);
            } else if (type == double.class) {
                if (v.isNum()) f.setDouble(props, v.number);
            } else if (type == float.class) {
                if (v.isNum()) f.setFloat(props, (float) v.number);
            } else if (type == String.class) {
                f.set(props, v.isStr() ? v.str : null);
            } else if (type == Boolean.class) {
                f.set(props, v.isBool() ? v.bool : null);
            } else if (type == Integer.class) {
                f.set(props, v.isNum() ? (int) v.number : null);
            } else if (type == Double.class) {
                f.set(props, v.isNum() ? v.number : null);
            } else if (type == Float.class) {
                f.set(props, v.isNum() ? (float) v.number : null);
            } else if (type.isEnum()) {
                if (v.isStr()) {
                    try {
                        f.set(props, Enum.valueOf(type.asSubclass(Enum.class), v.str));
                    } catch (IllegalArgumentException ignored) {
                        // 非法枚举值保持当前值
                    }
                }
            } else if (List.class.isAssignableFrom(type)) {
                f.set(props, toStringList(v));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("写入配置字段失败: " + key, e);
        }
    }

    /**
     * Converts a JsonValue array to a List of Strings.
     *
     * @param v the JsonValue (expected to be ARRAY type)
     * @return the list of strings, or empty list if not an array
     */
    private static List<String> toStringList(JsonValue v) {
        List<String> out = new ArrayList<>();
        if (v == null || v.type != JsonValue.Type.ARRAY) return out;
        for (JsonValue item : v.array) {
            if (item.type == JsonValue.Type.STRING) out.add(item.str);
        }
        return out;
    }

    // ---------------- Minimal JSON parser ----------------

    static final class JsonValue {
        enum Type { OBJECT, ARRAY, STRING, NUMBER, BOOL, NULL }
        Type type;
        String str;
        double number;
        boolean bool;
        Map<String, JsonValue> object;
        List<JsonValue> array;
        boolean isBool() { return type == Type.BOOL; }
        boolean isNum() { return type == Type.NUMBER; }
        boolean isStr() { return type == Type.STRING; }
    }

    static final class Parser {
        private final String s;
        private int p;
        Parser(String s) { this.s = s; this.p = 0; }

        JsonValue parseValue() {
            skipWs();
            if (p >= s.length()) throw err("unexpected end");
            char c = s.charAt(p);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        private JsonValue parseObject() {
            expect('{');
            JsonValue v = new JsonValue();
            v.type = JsonValue.Type.OBJECT;
            v.object = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { p++; return v; }
            while (true) {
                skipWs();
                JsonValue key = parseString();
                skipWs();
                expect(':');
                JsonValue val = parseValue();
                v.object.put(key.str, val);
                skipWs();
                char c = peek();
                if (c == ',') { p++; continue; }
                if (c == '}') { p++; return v; }
                throw err("expected , or } in object");
            }
        }

        private JsonValue parseArray() {
            expect('[');
            JsonValue v = new JsonValue();
            v.type = JsonValue.Type.ARRAY;
            v.array = new ArrayList<>();
            skipWs();
            if (peek() == ']') { p++; return v; }
            while (true) {
                v.array.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') { p++; continue; }
                if (c == ']') { p++; return v; }
                throw err("expected , or ] in array");
            }
        }

        private JsonValue parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (p < s.length()) {
                char c = s.charAt(p++);
                if (c == '"') {
                    JsonValue v = new JsonValue();
                    v.type = JsonValue.Type.STRING;
                    v.str = sb.toString();
                    return v;
                }
                if (c == '\\') {
                    char e = s.charAt(p++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                            p += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw err("unterminated string");
        }

        private JsonValue parseBool() {
            JsonValue v = new JsonValue();
            v.type = JsonValue.Type.BOOL;
            if (s.startsWith("true", p)) { v.bool = true; p += 4; }
            else if (s.startsWith("false", p)) { v.bool = false; p += 5; }
            else throw err("expected bool");
            return v;
        }
        private JsonValue parseNull() {
            if (s.startsWith("null", p)) { p += 4; JsonValue v = new JsonValue(); v.type = JsonValue.Type.NULL; return v; }
            throw err("expected null");
        }
        private JsonValue parseNumber() {
            int start = p;
            if (peek() == '-') p++;
            while (p < s.length() && "0123456789.eE+-".indexOf(s.charAt(p)) >= 0) p++;
            String num = s.substring(start, p);
            JsonValue v = new JsonValue();
            v.type = JsonValue.Type.NUMBER;
            try { v.number = Double.parseDouble(num); }
            catch (Exception e) { v.number = 0; }
            return v;
        }

        private void skipWs() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
        private char peek() { return p < s.length() ? s.charAt(p) : '\0'; }
        private void expect(char c) { skipWs(); if (peek() != c) throw err("expected " + c); p++; }
        private RuntimeException err(String msg) { return new RuntimeException("JSON at " + p + ": " + msg); }
    }

    // ---------------- write helpers ----------------

    private static void writeString(StringBuilder sb, String s) {
        if (s == null) { sb.append("null"); return; }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static String d2s(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        return Double.toString(d);
    }

    private static void writeEngines(StringBuilder sb, List<EngineConfig> engines) {
        sb.append('[');
        if (engines != null) for (int i = 0; i < engines.size(); i++) {
            if (i > 0) sb.append(',');
            EngineConfig e = engines.get(i);
            sb.append('{');
            writeString(sb, "name"); sb.append(':'); writeString(sb, e.getName()); sb.append(',');
            writeString(sb, "path"); sb.append(':'); writeString(sb, e.getPath()); sb.append(',');
            writeString(sb, "protocol"); sb.append(':'); writeString(sb, e.getProtocol()); sb.append(',');
            writeString(sb, "options"); sb.append(':');
            writeOptions(sb, e.getOptions());
            sb.append('}');
        }
        sb.append(']');
    }

    private static void writeOptions(StringBuilder sb, Map<String, String> opts) {
        sb.append('{');
        if (opts != null) {
            boolean first = true;
            for (Map.Entry<String, String> en : opts.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, en.getKey());
                sb.append(':');
                writeString(sb, en.getValue());
            }
        }
        sb.append('}');
    }

    private static void writeStringList(StringBuilder sb, String key, List<?> list) {
        sb.append('[');
        if (list != null) for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            writeString(sb, String.valueOf(list.get(i)));
        }
        sb.append(']');
    }
}
