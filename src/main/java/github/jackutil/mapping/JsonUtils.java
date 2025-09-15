package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    public static boolean isEmpty(JsonNode node) {
        if (node == null || node.isNull()) return true;
        if (node.isTextual()) return node.asText().isBlank();
        if (node.isArray()) return node.size() == 0;
        if (node.isObject()) return node.size() == 0;
        return false;
    }

    public static JsonNode valueToNode(Object value) {
        if (value == null) return NullNode.getInstance();
        if (value instanceof JsonNode n) return n;
        return MAPPER.valueToTree(value);
    }

    public static Number toNumber(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.numberValue();
        if (n.isBoolean()) return n.booleanValue() ? 1 : 0;
        if (n.isTextual()) {
            try { return new BigDecimal(n.asText()); } catch (Exception ignored) {}
        }
        return null;
    }

    public static Integer toInteger(JsonNode n) {
        Number num = toNumber(n);
        if (num == null) return null;
        if (num instanceof BigDecimal bd) return bd.intValue();
        return num.intValue();
    }

    public static JsonNode round(JsonNode n, int places) {
        Number num = toNumber(n);
        if (num == null) return NullNode.getInstance();
        BigDecimal bd = (num instanceof BigDecimal) ? (BigDecimal) num : new BigDecimal(num.toString());
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return valueToNode(bd);
    }

    public static JsonNode trim(JsonNode n) {
        if (n == null || n.isNull()) return NullNode.getInstance();
        if (n.isTextual()) return JsonNodeFactory.instance.textNode(n.asText().trim());
        return n;
    }

    public static JsonNode defaultIfEmpty(JsonNode n, JsonNode def) {
        return isEmpty(n) ? def : n;
    }

    public static JsonNode clamp(JsonNode n, double min, double max) {
        Number num = toNumber(n);
        if (num == null) return NullNode.getInstance();
        double v = Math.max(min, Math.min(max, num.doubleValue()));
        return JsonNodeFactory.instance.numberNode(v);
    }

    public static JsonNode lookup(JsonNode n, Map<String, String> table) {
        String key = n == null || n.isNull() ? null : n.asText(null);
        if (key == null) return NullNode.getInstance();
        String out = table.get(key);
        return out == null ? NullNode.getInstance() : JsonNodeFactory.instance.textNode(out);
    }

    public static JsonNode regexExtract(JsonNode n, String pattern, int group) {
        if (n == null || n.isNull()) return NullNode.getInstance();
        String text = n.asText(null);
        if (text == null) return NullNode.getInstance();
        Matcher m = Pattern.compile(pattern).matcher(text);
        if (!m.find()) return NullNode.getInstance();
        if (group < 0 || group > m.groupCount()) return NullNode.getInstance();
        return JsonNodeFactory.instance.textNode(m.group(group));
    }

    public static JsonNode parseDateTime(JsonNode n) {
        if (n == null || n.isNull()) return NullNode.getInstance();
        String text = n.asText(null);
        if (text == null) return NullNode.getInstance();
        try {
            OffsetDateTime odt = OffsetDateTime.parse(text);
            return JsonNodeFactory.instance.textNode(odt.toInstant().toString());
        } catch (Exception e) {
            try {
                Instant inst = Instant.parse(text);
                return JsonNodeFactory.instance.textNode(inst.toString());
            } catch (Exception ignored) {
                return NullNode.getInstance();
            }
        }
    }

    public static JsonNode toZoned(JsonNode n, String zoneId) {
        if (n == null || n.isNull()) return NullNode.getInstance();
        String text = n.asText(null);
        if (text == null) return NullNode.getInstance();
        try {
            Instant inst = Instant.parse(text);
            ZonedDateTime zdt = inst.atZone(ZoneId.of(zoneId));
            return JsonNodeFactory.instance.textNode(zdt.toOffsetDateTime().toString());
        } catch (Exception e) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(text);
                ZonedDateTime zdt = odt.atZoneSameInstant(ZoneId.of(zoneId));
                return JsonNodeFactory.instance.textNode(zdt.toOffsetDateTime().toString());
            } catch (Exception ignored) {
                return NullNode.getInstance();
            }
        }
    }

    public static boolean validateRange(JsonNode n, double min, double max) {
        Number num = toNumber(n);
        if (num == null) return false;
        double v = num.doubleValue();
        return v >= min && v <= max;
    }

    public static boolean validateRegex(JsonNode n, String pattern) {
        if (n == null || n.isNull()) return false;
        String text = n.asText(null);
        if (text == null) return false;
        return Pattern.compile(pattern).matcher(text).matches();
    }

    public static boolean validateEnum(JsonNode n, JsonNode values) {
        if (n == null || n.isNull() || values == null || !values.isArray()) return false;
        for (JsonNode v : values) {
            if (v.equals(n)) return true;
        }
        return false;
    }

    public static boolean validateMaxLength(JsonNode n, int maxLen) {
        if (n == null || n.isNull()) return false;
        String s = n.asText(null);
        return s != null && s.length() <= maxLen;
    }

    // Very lightweight unit conversion placeholder. Extend as needed.
    public static JsonNode unitConvert(JsonNode n, String from, String to) {
        if (n == null || n.isNull()) return NullNode.getInstance();
        if (from.equalsIgnoreCase(to)) return n;
        Number num = toNumber(n);
        if (num == null) return NullNode.getInstance();
        double v = num.doubleValue();
        // Common conversions demo: C <-> F, m <-> mm/cm/km
        double out = v;
        String f = from.toLowerCase();
        String t = to.toLowerCase();
        if ((f.equals("c") || f.equals("°c")) && (t.equals("f") || t.equals("°f"))) {
            out = v * 9.0 / 5.0 + 32.0;
        } else if ((f.equals("f") || f.equals("°f")) && (t.equals("c") || t.equals("°c"))) {
            out = (v - 32.0) * 5.0 / 9.0;
        } else if (f.equals("m") && t.equals("mm")) {
            out = v * 1000.0;
        } else if (f.equals("mm") && t.equals("m")) {
            out = v / 1000.0;
        } else if (f.equals("m") && t.equals("cm")) {
            out = v * 100.0;
        } else if (f.equals("cm") && t.equals("m")) {
            out = v / 100.0;
        } else if (f.equals("m") && t.equals("km")) {
            out = v / 1000.0;
        } else if (f.equals("km") && t.equals("m")) {
            out = v * 1000.0;
        } else {
            // Unknown conversion → return as-is
            out = v;
        }
        return JsonNodeFactory.instance.numberNode(out);
    }

    public static void putDeep(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("/");
        ObjectNode cursor = root;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            if (i == parts.length - 1) {
                cursor.set(key, value);
            } else {
                JsonNode next = cursor.get(key);
                if (!(next instanceof ObjectNode)) {
                    ObjectNode created = JsonNodeFactory.instance.objectNode();
                    cursor.set(key, created);
                    cursor = created;
                } else {
                    cursor = (ObjectNode) next;
                }
            }
        }
    }
}

