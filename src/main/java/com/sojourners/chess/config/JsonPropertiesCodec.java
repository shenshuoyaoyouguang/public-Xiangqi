package com.sojourners.chess.config;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.openbook.MoveRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Properties 文件的 JSON 序列化/反序列化 (手写，无依赖)。
 */
public final class JsonPropertiesCodec {

    private JsonPropertiesCodec() {}

    public static String toJson(Properties p) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{");
        writeKv(sb, "boardSize", p.getBoardSize().name()); sb.append(',');
        writeKv(sb, "boardStyle", p.getBoardStyle().name()); sb.append(',');
        writeKv(sb, "stepTip", p.isStepTip()); sb.append(',');
        writeKv(sb, "stepSound", p.isStepSound()); sb.append(',');
        writeKv(sb, "showNumber", p.isShowNumber()); sb.append(',');
        writeKv(sb, "topWindow", p.isTopWindow()); sb.append(',');
        writeKv(sb, "manualTip", p.isManualTip()); sb.append(',');
        writeKv(sb, "showChessNotation", p.isShowChessNotation()); sb.append(',');
        writeKv(sb, "colorTheme", p.getColorTheme().name()); sb.append(',');
        writeKv(sb, "threadNum", p.getThreadNum()); sb.append(',');
        writeKv(sb, "hashSize", p.getHashSize()); sb.append(',');
        writeKv(sb, "analysisModel", p.getAnalysisModel().name()); sb.append(',');
        writeKv(sb, "analysisValue", p.getAnalysisValue()); sb.append(',');
        writeKv(sb, "engineName", p.getEngineName()); sb.append(',');
        writeKv(sb, "stageWidth", p.getStageWidth()); sb.append(',');
        writeKv(sb, "stageHeight", p.getStageHeight()); sb.append(',');
        writeKv(sb, "splitPos", p.getSplitPos()); sb.append(',');
        writeKv(sb, "splitPos2", p.getSplitPos2()); sb.append(',');
        writeKv(sb, "linkScanTime", p.getLinkScanTime()); sb.append(',');
        writeKv(sb, "linkThreadNum", p.getLinkThreadNum()); sb.append(',');
        writeKv(sb, "linkAnimation", p.isLinkAnimation()); sb.append(',');
        writeKv(sb, "linkShowInfo", p.isLinkShowInfo()); sb.append(',');
        writeKv(sb, "linkBackMode", p.isLinkBackMode()); sb.append(',');
        writeKv(sb, "engineDelayStart", p.getEngineDelayStart()); sb.append(',');
        writeKv(sb, "engineDelayEnd", p.getEngineDelayEnd()); sb.append(',');
        writeKv(sb, "bookDelayStart", p.getBookDelayStart()); sb.append(',');
        writeKv(sb, "bookDelayEnd", p.getBookDelayEnd()); sb.append(',');
        writeKv(sb, "mouseClickDelay", p.getMouseClickDelay()); sb.append(',');
        writeKv(sb, "mouseMoveDelay", p.getMouseMoveDelay()); sb.append(',');
        writeKv(sb, "firstStepColor", p.getFirstStepColor()); sb.append(',');
        writeKv(sb, "firstStepNumberColor", p.getFirstStepNumberColor()); sb.append(',');
        writeKv(sb, "secondStepColor", p.getSecondStepColor()); sb.append(',');
        writeKv(sb, "secondStepNumberColor", p.getSecondStepNumberColor()); sb.append(',');
        writeKv(sb, "branchStepColor", p.getBranchStepColor()); sb.append(',');
        writeKv(sb, "branchStepNumberColor", p.getBranchStepNumberColor()); sb.append(',');
        writeKv(sb, "firstStepOpacity", p.getFirstStepOpacity()); sb.append(',');
        writeKv(sb, "secondStepOpacity", p.getSecondStepOpacity()); sb.append(',');
        writeKv(sb, "branchStepOpacity", p.getBranchStepOpacity()); sb.append(',');
        writeKvNullableBool(sb, "localBookFirst", p.getLocalBookFirst()); sb.append(',');
        writeKvNullableBool(sb, "useCloudBook", p.getUseCloudBook()); sb.append(',');
        writeKvNullableBool(sb, "onlyCloudFinalPhase", p.getOnlyCloudFinalPhase()); sb.append(',');
        writeKvNullableInt(sb, "cloudBookTimeout", p.getCloudBookTimeout()); sb.append(',');
        writeKvNullableInt(sb, "offManualSteps", p.getOffManualSteps()); sb.append(',');
        writeKvNullableBool(sb, "bookSwitch", p.getBookSwitch()); sb.append(',');
        writeKvNullableString(sb, "chessManualPath", p.getChessManualPath()); sb.append(',');
        writeKv(sb, "moveRule", p.getMoveRule().name()); sb.append(',');
        writeStringList(sb, "openBookList", p.getOpenBookList()); sb.append(',');
        sb.append("\"engines\":");
        writeEngines(sb, p.getEngineConfigList());
        sb.append('}');
        return sb.toString();
    }

    private static void writeKv(StringBuilder sb, String k, String v) {
        writeString(sb, k); sb.append(':'); writeString(sb, v);
    }
    private static void writeKv(StringBuilder sb, String k, boolean v) {
        writeString(sb, k); sb.append(':'); sb.append(v ? "true" : "false");
    }
    private static void writeKv(StringBuilder sb, String k, long v) {
        writeString(sb, k); sb.append(':'); sb.append(v);
    }
    private static void writeKv(StringBuilder sb, String k, int v) {
        writeString(sb, k); sb.append(':'); sb.append(v);
    }
    private static void writeKv(StringBuilder sb, String k, double v) {
        writeString(sb, k); sb.append(':'); sb.append(d2s(v));
    }
    private static void writeKvNullableBool(StringBuilder sb, String k, Boolean v) {
        writeString(sb, k); sb.append(':');
        if (v == null) sb.append("null"); else sb.append(v ? "true" : "false");
    }
    private static void writeKvNullableInt(StringBuilder sb, String k, Integer v) {
        writeString(sb, k); sb.append(':');
        if (v == null) sb.append("null"); else sb.append(v);
    }
    private static void writeKvNullableString(StringBuilder sb, String k, String v) {
        writeString(sb, k); sb.append(':'); writeString(sb, v);
    }
    private static void writeStringList(StringBuilder sb, String k, List<String> list) {
        writeString(sb, k); sb.append(":[");
        if (list != null) for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            writeString(sb, list.get(i));
        }
        sb.append(']');
    }

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
            writeKv(sb, "name", e.getName()); sb.append(',');
            writeKv(sb, "path", e.getPath()); sb.append(',');
            writeKv(sb, "protocol", e.getProtocol()); sb.append(',');
            writeKv(sb, "options", e.getOptions());
            sb.append('}');
        }
        sb.append(']');
    }
    // Map write — write as JSON object
    private static void writeKv(StringBuilder sb, String k, Map<String, String> opts) {
        writeString(sb, k); sb.append(":{");
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

    // ---------------- Deserialization ----------------

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

    private static String strOrNull(JsonValue v) {
        if (v == null || v.type == JsonValue.Type.NULL) return null;
        if (v.type == JsonValue.Type.STRING) return v.str;
        return String.valueOf(v.number);
    }

    private static void applyField(Properties props, String key, JsonValue v) {
        switch (key) {
            case "boardSize":          props.setBoardSize(parseEnum(v, ChessBoard.BoardSize.class, props.getBoardSize())); break;
            case "boardStyle":         props.setBoardStyle(parseEnum(v, ChessBoard.BoardStyle.class, props.getBoardStyle())); break;
            case "stepTip":            if (v.isBool()) props.setStepTip(v.bool); break;
            case "stepSound":          if (v.isBool()) props.setStepSound(v.bool); break;
            case "showNumber":         if (v.isBool()) props.setShowNumber(v.bool); break;
            case "topWindow":          if (v.isBool()) props.setTopWindow(v.bool); break;
            case "manualTip":          if (v.isBool()) props.setManualTip(v.bool); break;
            case "showChessNotation":  if (v.isBool()) props.setShowChessNotation(v.bool); break;
            case "colorTheme":         props.setColorTheme(parseEnum(v, Properties.ColorTheme.class, props.getColorTheme())); break;
            case "threadNum":          if (v.isNum()) props.setThreadNum((int) v.number); break;
            case "hashSize":           if (v.isNum()) props.setHashSize((int) v.number); break;
            case "analysisModel":      props.setAnalysisModel(parseEnum(v, Engine.AnalysisModel.class, props.getAnalysisModel())); break;
            case "analysisValue":      if (v.isNum()) props.setAnalysisValue((long) v.number); break;
            case "engineName":         props.setEngineName(v.isStr() ? v.str : null); break;
            case "stageWidth":         if (v.isNum()) props.setStageWidth(v.number); break;
            case "stageHeight":        if (v.isNum()) props.setStageHeight(v.number); break;
            case "splitPos":           if (v.isNum()) props.setSplitPos(v.number); break;
            case "splitPos2":          if (v.isNum()) props.setSplitPos2(v.number); break;
            case "linkScanTime":       if (v.isNum()) props.setLinkScanTime((long) v.number); break;
            case "linkThreadNum":      if (v.isNum()) props.setLinkThreadNum((int) v.number); break;
            case "linkAnimation":      if (v.isBool()) props.setLinkAnimation(v.bool); break;
            case "linkShowInfo":       if (v.isBool()) props.setLinkShowInfo(v.bool); break;
            case "linkBackMode":       if (v.isBool()) props.setLinkBackMode(v.bool); break;
            case "engineDelayStart":   if (v.isNum()) props.setEngineDelayStart((int) v.number); break;
            case "engineDelayEnd":     if (v.isNum()) props.setEngineDelayEnd((int) v.number); break;
            case "bookDelayStart":     if (v.isNum()) props.setBookDelayStart((int) v.number); break;
            case "bookDelayEnd":       if (v.isNum()) props.setBookDelayEnd((int) v.number); break;
            case "mouseClickDelay":    if (v.isNum()) props.setMouseClickDelay((int) v.number); break;
            case "mouseMoveDelay":     if (v.isNum()) props.setMouseMoveDelay((int) v.number); break;
            case "firstStepColor":     props.setFirstStepColor(v.isStr() ? v.str : null); break;
            case "firstStepNumberColor": props.setFirstStepNumberColor(v.isStr() ? v.str : null); break;
            case "secondStepColor":    props.setSecondStepColor(v.isStr() ? v.str : null); break;
            case "secondStepNumberColor": props.setSecondStepNumberColor(v.isStr() ? v.str : null); break;
            case "branchStepColor":    props.setBranchStepColor(v.isStr() ? v.str : null); break;
            case "branchStepNumberColor": props.setBranchStepNumberColor(v.isStr() ? v.str : null); break;
            case "firstStepOpacity":   if (v.isNum()) props.setFirstStepOpacity(v.number); break;
            case "secondStepOpacity":  if (v.isNum()) props.setSecondStepOpacity(v.number); break;
            case "branchStepOpacity":  if (v.isNum()) props.setBranchStepOpacity(v.number); break;
            case "localBookFirst":     props.setLocalBookFirst(v.isBool() ? v.bool : null); break;
            case "useCloudBook":       props.setUseCloudBook(v.isBool() ? v.bool : null); break;
            case "onlyCloudFinalPhase": props.setOnlyCloudFinalPhase(v.isBool() ? v.bool : null); break;
            case "cloudBookTimeout":   props.setCloudBookTimeout(v.isNum() ? (int) v.number : null); break;
            case "offManualSteps":     props.setOffManualSteps(v.isNum() ? (int) v.number : null); break;
            case "bookSwitch":         props.setBookSwitch(v.isBool() ? v.bool : null); break;
            case "chessManualPath":    props.setChessManualPath(v.isStr() ? v.str : null); break;
            case "moveRule":           props.setMoveRule(parseEnum(v, MoveRule.class, props.getMoveRule())); break;
            case "openBookList":       props.setOpenBookList(toStringList(v)); break;
        }
    }

    private static List<String> toStringList(JsonValue v) {
        List<String> out = new ArrayList<>();
        if (v == null || v.type != JsonValue.Type.ARRAY) return out;
        for (JsonValue item : v.array) {
            if (item.type == JsonValue.Type.STRING) out.add(item.str);
        }
        return out;
    }

    private static <E extends Enum<E>> E parseEnum(JsonValue v, Class<E> type, E fallback) {
        if (v == null || v.type != JsonValue.Type.STRING) return fallback;
        try { return Enum.valueOf(type, v.str); }
        catch (Exception e) { return fallback; }
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
}
