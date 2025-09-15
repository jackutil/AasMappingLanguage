package github.jackutil.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import static github.jackutil.mapping.JsonUtils.*;

import java.util.*;

public class MappingEngine {
    private final ObjectMapper mapper = new ObjectMapper();

    private final Configuration jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build();

    public MappingResult map(JsonNode config, JsonNode payload) {
        List<String> errors = new ArrayList<>();
        ObjectNode out = JsonNodeFactory.instance.objectNode();

        // Validate apiVersion if present
        JsonNode apiVersion = config.path("apiVersion");
        if (!apiVersion.isTextual() || !"aasx.map/v1".equals(apiVersion.asText())) {
            errors.add("Unsupported or missing apiVersion: " + apiVersion.asText());
        }

        // Variables are currently parsed but not used yet (reserved for future ref)
        ObjectNode variables = config.path("variables").isObject() ? (ObjectNode) config.path("variables") : JsonNodeFactory.instance.objectNode();

        ArrayNode rules = config.path("rules").isArray() ? (ArrayNode) config.path("rules") : null;
        if (rules == null || rules.isEmpty()) {
            errors.add("No rules provided in config");
            return new MappingResult(out, errors);
        }

        for (JsonNode rule : rules) {
            if (!rule.isObject()) {
                errors.add("Rule is not an object: " + rule);
                continue;
            }
            String target = rule.path("target").asText(null);
            if (target == null || target.isBlank()) {
                errors.add("Rule missing target");
                continue;
            }

            if (!evaluatePredicate(rule.path("when"), payload)) {
                // Predicate false → skip rule
                continue;
            }

            JsonNode finalValue;

            // If 'project' present, build composite object from selectors
            if (rule.has("project") && rule.path("project").isObject()) {
                ObjectNode composite = JsonNodeFactory.instance.objectNode();
                Iterator<Map.Entry<String, JsonNode>> fields = rule.path("project").properties().iterator();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> e = fields.next();
                    JsonNode sel = e.getValue();
                    JsonNode v = evaluateSelector(sel, payload, variables);
                    composite.set(e.getKey(), v == null ? NullNode.getInstance() : v);
                }
                finalValue = composite;
            } else {
                // Evaluate source + fallback + transforms → scalar/object value
                JsonNode value = evaluateSourceExpr(rule.path("source"), payload, variables);

                if (isEmpty(value) && rule.has("fallback") && rule.get("fallback").isArray()) {
                    for (JsonNode fb : rule.get("fallback")) {
                        value = evaluateSourceExpr(fb, payload, variables);
                        if (!isEmpty(value)) break;
                    }
                }

                // Rule-level transforms
                if (rule.has("transform") && rule.get("transform").isArray()) {
                    value = applyTransforms(value, (ArrayNode) rule.get("transform"), payload, variables);
                }
                finalValue = value == null ? NullNode.getInstance() : value;
            }

            // Constraints
            if (rule.has("constraints") && rule.get("constraints").isArray()) {
                String err = validateConstraints(finalValue, (ArrayNode) rule.get("constraints"));
                if (err != null) {
                    errors.add("Constraint failed at '" + target + "': " + err);
                    continue; // Skip setting this value
                }
            }

            putDeep(out, target, finalValue);
        }

        // If submodel header is provided, wrap the mapped values into an AAS4J Submodel JSON
        if (config.has("submodel") && config.get("submodel").isObject()) {
            ObjectNode submodelHeader = (ObjectNode) config.get("submodel");
            Map<String, ObjectNode> metaByPath = new HashMap<>();
            if (submodelHeader.has("initialElements") && submodelHeader.get("initialElements").isArray()) {
                for (JsonNode ie : submodelHeader.get("initialElements")) {
                    String path = ie.path("path").asText(null);
                    if (path != null) metaByPath.put(path, (ObjectNode) ie);
                }
            }

            github.jackutil.mapping.Aas4jSubmodelFactory factory = new github.jackutil.mapping.Aas4jSubmodelFactory();
            ObjectNode wrapped = factory.build(submodelHeader, metaByPath, out);
            return new MappingResult(wrapped, errors);
        }

        return new MappingResult(out, errors);
    }

    private JsonNode evaluateSourceExpr(JsonNode sourceExpr, JsonNode payload, ObjectNode variables) {
        if (sourceExpr == null || !sourceExpr.isObject()) return NullNode.getInstance();

        JsonNode value;
        if (sourceExpr.has("jsonPath")) {
            String path = sourceExpr.path("jsonPath").asText();
            try {
                // Use Jayway JsonPath with Jackson provider → returns JsonNode
                value = JsonPath.using(jsonPathConfig).parse(payload).read(path, JsonNode.class);
                if (value == null) value = NullNode.getInstance();
            } catch (Exception e) {
                value = NullNode.getInstance();
            }
        } else if (sourceExpr.has("constant")) {
            value = sourceExpr.get("constant");
        } else if (sourceExpr.has("var")) {
            value = evaluateSelector(sourceExpr, payload, variables);
        } else {
            value = NullNode.getInstance();
        }

        if (sourceExpr.has("transform") && sourceExpr.get("transform").isArray()) {
            value = applyTransforms(value, (ArrayNode) sourceExpr.get("transform"), payload, variables);
        }

        return value;
    }

    private JsonNode evaluateSelector(JsonNode selector, JsonNode payload, ObjectNode variables) {
        return evaluateSelector(selector, payload, variables, new HashSet<>());
    }

    private JsonNode evaluateSelector(JsonNode selector, JsonNode payload, ObjectNode variables, Set<String> resolving) {
        if (selector == null || !selector.isObject()) return NullNode.getInstance();
        if (selector.has("jsonPath")) {
            try {
                return JsonPath.using(jsonPathConfig).parse(payload).read(selector.get("jsonPath").asText(), JsonNode.class);
            } catch (Exception e) {
                return NullNode.getInstance();
            }
        }
        if (selector.has("constant")) {
            return selector.get("constant");
        }
        if (selector.has("var")) {
            String name = selector.path("var").asText("");
            if (name.isEmpty()) return NullNode.getInstance();
            if (resolving.contains(name)) {
                return NullNode.getInstance(); // cycle detected
            }
            JsonNode varSel = variables == null ? null : variables.get(name);
            if (varSel == null) return NullNode.getInstance();
            resolving.add(name);
            try {
                return evaluateSelector(varSel, payload, variables, resolving);
            } finally {
                resolving.remove(name);
            }
        }
        return NullNode.getInstance();
    }

    private boolean evaluatePredicate(JsonNode predicate, JsonNode payload) {
        if (predicate == null || predicate.isMissingNode() || predicate.isNull()) return true; // no predicate → pass
        if (!predicate.isObject()) return true;

        // any
        if (predicate.has("any") && predicate.get("any").isArray()) {
            for (JsonNode atom : predicate.get("any")) {
                if (evaluateAtom(atom, payload)) return true;
            }
            return false;
        }

        // all
        if (predicate.has("all") && predicate.get("all").isArray()) {
            for (JsonNode atom : predicate.get("all")) {
                if (!evaluateAtom(atom, payload)) return false;
            }
            return true;
        }

        // not
        if (predicate.has("not")) {
            return !evaluatePredicate(predicate.get("not"), payload);
        }

        return true;
    }

    private boolean evaluateAtom(JsonNode atom, JsonNode payload) {
        if (atom == null || !atom.isObject()) return false;
        String jsonPath = atom.path("jsonPath").asText(null);
        JsonNode equalsTo = atom.get("equals");
        if (jsonPath == null) return false;
        try {
            JsonNode val = JsonPath.using(jsonPathConfig).parse(payload).read(jsonPath, JsonNode.class);
            if (val == null) return false;
            return val.equals(equalsTo);
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode applyTransforms(JsonNode value, ArrayNode transforms, JsonNode payload, ObjectNode variables) {
        JsonNode current = value == null ? NullNode.getInstance() : value;
        for (JsonNode t : transforms) {
            if (!t.isObject()) continue;
            String op = t.path("op").asText("");
            switch (op) {
                case "toInteger" -> current = valueToNode(toInteger(current));
                case "toNumber" -> current = valueToNode(toNumber(current));
                case "round" -> current = JsonUtils.round(current, t.path("places").asInt(0));
                case "trim" -> current = JsonUtils.trim(current);
                case "defaultIfEmpty" -> current = JsonUtils.defaultIfEmpty(current, t.get("value"));
                case "unitConvert" -> current = JsonUtils.unitConvert(current, t.path("from").asText(""), t.path("to").asText(""));
                case "parseDateTime" -> current = JsonUtils.parseDateTime(current);
                case "toZoned" -> current = JsonUtils.toZoned(current, t.path("zone").asText("UTC"));
                case "clamp" -> current = JsonUtils.clamp(current, t.path("min").asDouble(Double.NEGATIVE_INFINITY), t.path("max").asDouble(Double.POSITIVE_INFINITY));
                case "lookup" -> {
                    Map<String, String> table = new LinkedHashMap<>();
                    Iterator<String> it = t.path("table").fieldNames();
                    while (it.hasNext()) {
                        String k = it.next();
                        table.put(k, t.path("table").path(k).asText());
                    }
                    current = JsonUtils.lookup(current, table);
                }
                case "regexExtract" -> current = JsonUtils.regexExtract(current, t.path("pattern").asText(""), t.path("group").asInt(0));
                case "concat" -> {
                    String sep = t.path("separator").asText("");
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (JsonNode part : t.path("parts")) {
                        JsonNode pv;
                        if (part.isTextual()) {
                            pv = part;
                        } else {
                            pv = evaluateSelector(part, payload, variables);
                        }
                        String s = pv.isNull() ? "" : pv.asText("");
                        if (!first) sb.append(sep);
                        sb.append(s);
                        first = false;
                    }
                    current = JsonNodeFactory.instance.textNode(sb.toString());
                }
            }
        }
        return current == null ? NullNode.getInstance() : current;
    }

    private String validateConstraints(JsonNode value, ArrayNode constraints) {
        for (JsonNode c : constraints) {
            if (!c.isObject()) continue;
            String kind = c.path("kind").asText("");
            boolean ok = true;
            switch (kind) {
                case "range" -> ok = JsonUtils.validateRange(value, c.path("min").asDouble(), c.path("max").asDouble());
                case "regex" -> ok = JsonUtils.validateRegex(value, c.path("pattern").asText(""));
                case "enum" -> ok = JsonUtils.validateEnum(value, c.path("values"));
                case "maxLength" -> ok = JsonUtils.validateMaxLength(value, c.path("value").asInt());
            }
            if (!ok) return kind;
        }
        return null;
    }

    // Utility to pretty print mapping result as JSON string
    public String toPrettyJson(ObjectNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }
}
