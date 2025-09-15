package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build a best-effort JSON Schema for the expected payload based on an AML config.
 * - Collects jsonPath selectors from source/fallback/project and variables.
 * - Maps rule constraints to JSON Schema keywords when shapes align.
 * - Uses permissive typing when transforms may change type (e.g., toNumber).
 */
public class SchemaExporter {
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public ObjectNode exportPayloadSchema(JsonNode config) {
        ObjectNode root = F.objectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("type", "object");
        if (config.hasNonNull("name")) root.put("title", config.get("name").asText());
        if (config.hasNonNull("description")) root.put("description", config.get("description").asText());

        ObjectNode properties = F.objectNode();
        root.set("properties", properties);
        root.put("additionalProperties", true);

        Map<String, Set<String>> requiredAt = new HashMap<>();

        Map<String, JsonNode> variables = new HashMap<>();
        if (config.has("variables") && config.get("variables").isObject()) {
            config.get("variables").properties().iterator().forEachRemaining(e -> variables.put(e.getKey(), e.getValue()));
        }

        if (config.has("rules") && config.get("rules").isArray()) {
            for (JsonNode rule : config.get("rules")) {
                List<JsonNode> selectors = new ArrayList<>();
                if (rule.has("source")) selectors.add(rule.get("source"));
                if (rule.has("fallback") && rule.get("fallback").isArray()) rule.get("fallback").forEach(selectors::add);
                if (rule.has("project") && rule.get("project").isObject()) rule.get("project").elements().forEachRemaining(selectors::add);

                boolean hasFallbackNonJsonPath = hasNonJsonPathFallback(rule);

                for (JsonNode sel : selectors) {
                    List<String> jsonPaths = resolveJsonPaths(sel, variables, new HashSet<>());
                    for (String jp : jsonPaths) {
                        TypeHint hint = inferTypeHint(sel);
                        addPath(properties, requiredAt, jp, hint, rule.get("constraints"));
                        if (!hasFallbackNonJsonPath && isDirectSource(rule, sel, jp)) {
                            markRequired(requiredAt, jp);
                        }
                    }
                }
            }
        }

        applyRequired(properties, requiredAt, "$");
        return root;
    }

    private boolean hasNonJsonPathFallback(JsonNode rule) {
        if (!rule.has("fallback")) return false;
        for (JsonNode fb : rule.get("fallback")) {
            if (!(fb.has("jsonPath"))) return true;
        }
        return false;
    }

    private boolean isDirectSource(JsonNode rule, JsonNode sel, String jsonPath) {
        if (!rule.has("source")) return false;
        JsonNode src = rule.get("source");
        if (src == sel && src.has("jsonPath") && jsonPath.equals(src.get("jsonPath").asText())) return true;
        if (src == sel && src.has("var")) return true;
        return false;
    }

    private enum TypeHint { STRING, NUMBER_OR_STRING, DATE_TIME_STRING, UNKNOWN }

    private TypeHint inferTypeHint(JsonNode selector) {
        ArrayNode t = selector.has("transform") && selector.get("transform").isArray()
                ? (ArrayNode) selector.get("transform") : null;
        if (t == null) return TypeHint.UNKNOWN;
        boolean numbery = false;
        boolean datey = false;
        for (JsonNode op : t) {
            String name = op.path("op").asText("");
            switch (name) {
                case "toInteger", "toNumber", "round", "unitConvert", "clamp" -> numbery = true;
                case "parseDateTime", "toZoned" -> datey = true;
            }
        }
        if (datey) return TypeHint.DATE_TIME_STRING;
        if (numbery) return TypeHint.NUMBER_OR_STRING;
        return TypeHint.UNKNOWN;
    }

    private void addPath(ObjectNode propertiesRoot, Map<String, Set<String>> requiredAt, String jsonPath, TypeHint hint, JsonNode constraints) {
        List<PathToken> tokens = parseJsonPath(jsonPath);
        if (tokens.isEmpty()) return;
        ObjectNode currentObj = ensureTypeObject(propertiesRoot);
        StringBuilder currentPath = new StringBuilder("$");
        for (int i = 0; i < tokens.size(); i++) {
            PathToken tok = tokens.get(i);
            currentPath.append("/").append(tok.toString());
            boolean isLeaf = (i == tokens.size() - 1);

            if (tok.kind == PathToken.Kind.FIELD) {
                ObjectNode props = (ObjectNode) currentObj.get("properties");
                if (props == null) { props = F.objectNode(); currentObj.set("properties", props); }
                ObjectNode child = props.has(tok.name) && props.get(tok.name).isObject() ? (ObjectNode) props.get(tok.name) : F.objectNode();
                if (!child.has("type")) child.put("type", isLeaf ? leafType(hint) : "object");
                if (!isLeaf && !"object".equals(child.path("type").asText())) child.put("type", "object");
                props.set(tok.name, child);
                currentObj = child;
            } else if (tok.kind == PathToken.Kind.ARRAY) {
                if (!currentObj.has("type") || !"array".equals(currentObj.get("type").asText())) {
                    currentObj.put("type", "array");
                }
                ObjectNode items = currentObj.has("items") && currentObj.get("items").isObject()
                        ? (ObjectNode) currentObj.get("items") : F.objectNode();
                if (!items.has("type")) items.put("type", isLeaf ? leafType(hint) : "object");
                currentObj.set("items", items);
                currentObj = items;
            }

            if (isLeaf) {
                if (hint == TypeHint.DATE_TIME_STRING) {
                    currentObj.put("format", "date-time");
                }
                if (constraints != null && constraints.isArray()) {
                    applyConstraints(currentObj, constraints);
                }
            }
        }
    }

    private String leafType(TypeHint hint) {
        return switch (hint) {
            case DATE_TIME_STRING -> "string";
            case NUMBER_OR_STRING -> "string"; // allow either (numbers as strings permitted)
            case STRING, UNKNOWN -> "string";
        };
    }

    private void applyConstraints(ObjectNode node, JsonNode constraints) {
        for (JsonNode c : constraints) {
            String kind = c.path("kind").asText("");
            switch (kind) {
                case "range" -> {
                    if (!node.has("minimum")) node.set("minimum", c.get("min"));
                    if (!node.has("maximum")) node.set("maximum", c.get("max"));
                }
                case "regex" -> {
                    if (!node.has("pattern") && c.has("pattern")) node.put("pattern", c.get("pattern").asText());
                }
                case "enum" -> {
                    if (!node.has("enum") && c.has("values") && c.get("values").isArray()) {
                        node.set("enum", c.get("values"));
                    }
                }
                case "maxLength" -> {
                    if (!node.has("maxLength") && c.has("value")) node.set("maxLength", c.get("value"));
                }
            }
        }
        node.set("x-aml-constraints", constraints.deepCopy());
    }

    private void markRequired(Map<String, Set<String>> requiredAt, String jsonPath) {
        List<PathToken> tokens = parseJsonPath(jsonPath);
        if (tokens.isEmpty()) return;
        StringBuilder parentPath = new StringBuilder("$");
        for (int i = 0; i < tokens.size() - 1; i++) parentPath.append("/").append(tokens.get(i).toString());
        PathToken leaf = tokens.get(tokens.size() - 1);
        if (leaf.kind != PathToken.Kind.FIELD) return;
        requiredAt.computeIfAbsent(parentPath.toString(), k -> new LinkedHashSet<>()).add(leaf.name);
    }

    private void applyRequired(ObjectNode propertiesRoot, Map<String, Set<String>> requiredAt, String currentPath) {
        ObjectNode props = (ObjectNode) propertiesRoot.get("properties");
        if (props != null && props.isObject()) {
            Set<String> req = requiredAt.getOrDefault(currentPath, Collections.emptySet());
            if (!req.isEmpty()) {
                ArrayNode required = F.arrayNode();
                req.forEach(required::add);
                propertiesRoot.set("required", required);
            }
            Iterator<Map.Entry<String, JsonNode>> it = props.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode child = e.getValue();
                String nextPath = currentPath + "/" + e.getKey();
                if ("object".equals(child.path("type").asText()) && child.isObject()) {
                    applyRequired((ObjectNode) child, requiredAt, nextPath);
                } else if ("array".equals(child.path("type").asText()) && child.has("items") && child.get("items").isObject()) {
                    applyRequired((ObjectNode) child.get("items"), requiredAt, nextPath + "/*");
                }
            }
        }
    }

    private List<String> resolveJsonPaths(JsonNode selector, Map<String, JsonNode> variables, Set<String> seen) {
        List<String> res = new ArrayList<>();
        if (selector == null || !selector.isObject()) return res;
        if (selector.has("jsonPath")) {
            res.add(selector.get("jsonPath").asText());
        } else if (selector.has("var")) {
            String name = selector.get("var").asText("");
            if (!seen.add(name)) return res; // cycle detected
            JsonNode v = variables.get(name);
            if (v != null) res.addAll(resolveJsonPaths(v, variables, seen));
        }
        return res;
    }

    private record PathToken(Kind kind, String name) {
        enum Kind { FIELD, ARRAY }
        public String toString() { return kind == Kind.FIELD ? name : "*"; }
    }

    private List<PathToken> parseJsonPath(String path) {
        List<PathToken> tokens = new ArrayList<>();
        if (path == null || path.isBlank()) return tokens;
        String p = path.trim();
        if (p.startsWith("$")) { p = p.substring(1); if (p.startsWith(".")) p = p.substring(1); }
        int i = 0; StringBuilder current = new StringBuilder(); List<String> segments = new ArrayList<>();
        while (i < p.length()) { char c = p.charAt(i); if (c == '.') { segments.add(current.toString()); current.setLength(0); i++; continue; } current.append(c); i++; }
        if (current.length() > 0) segments.add(current.toString());
        Pattern segTok = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)|\\['([^']+)'\\]|\\[(\\d+|\\*)]|");
        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            Matcher m = segTok.matcher(seg);
            boolean fieldEmitted = false;
            while (m.find()) {
                String f1 = m.group(1), f2 = m.group(2), arr = m.group(3);
                if (f1 != null) { tokens.add(new PathToken(PathToken.Kind.FIELD, f1)); fieldEmitted = true; }
                else if (f2 != null) { tokens.add(new PathToken(PathToken.Kind.FIELD, f2)); fieldEmitted = true; }
                else if (arr != null) { tokens.add(new PathToken(PathToken.Kind.ARRAY, "*")); }
            }
            if (!fieldEmitted && seg.equals("*")) tokens.add(new PathToken(PathToken.Kind.ARRAY, "*"));
        }
        return tokens;
    }

    private ObjectNode ensureTypeObject(ObjectNode node) {
        if (!node.has("type")) node.put("type", "object");
        return node;
    }
}
