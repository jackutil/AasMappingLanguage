package github.jackutil.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.*;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.*;

import java.util.*;

public class Aas4jSubmodelFactory {
    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode build(ObjectNode header, Map<String, ObjectNode> metaByPath, JsonNode mappedValues) {
        Submodel submodel = buildSubmodelHeader(header);
        List<SubmodelElement> elements = new ArrayList<>();
        addElementsRecursive(elements, "", mappedValues, metaByPath);
        submodel.setSubmodelElements(elements);

        try {
            String json = new JsonSerializer().write(submodel);
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize AAS4J Submodel", e);
        }
    }

    private Submodel buildSubmodelHeader(ObjectNode header) {
        DefaultSubmodel.Builder b = new DefaultSubmodel.Builder();
        // id (idStrategy)
        b.id(resolveId(header.path("idStrategy")));

        if (header.hasNonNull("idShort")) b.idShort(header.get("idShort").asText());
        if (header.hasNonNull("kind")) b.kind(parseKind(header.get("kind").asText()));

        if (header.hasNonNull("semanticId")) {
            b.semanticId(globalRef(header.get("semanticId").asText()));
        }

        if (header.has("administration") && header.get("administration").isObject()) {
            ObjectNode a = (ObjectNode) header.get("administration");
            DefaultAdministrativeInformation.Builder ab = new DefaultAdministrativeInformation.Builder();
            if (a.hasNonNull("version")) ab.version(a.get("version").asText());
            if (a.hasNonNull("revision")) ab.revision(a.get("revision").asText());
            b.administration(ab.build());
        }

        if (header.has("displayName") && header.get("displayName").isArray()) {
            List<LangStringNameType> list = new ArrayList<>();
            for (JsonNode n : header.get("displayName")) {
                list.add(new DefaultLangStringNameType.Builder()
                        .language(n.path("lang").asText("en"))
                        .text(n.path("text").asText(""))
                        .build());
            }
            b.displayName(list);
        }

        if (header.has("description") && header.get("description").isArray()) {
            List<LangStringTextType> list = new ArrayList<>();
            for (JsonNode n : header.get("description")) {
                list.add(new DefaultLangStringTextType.Builder()
                        .language(n.path("lang").asText("en"))
                        .text(n.path("text").asText(""))
                        .build());
            }
            b.description(list);
        }

        return b.build();
    }

    private ModellingKind parseKind(String k) {
        if (k == null) return ModellingKind.INSTANCE;
        return switch (k.toUpperCase(Locale.ROOT)) {
            case "TEMPLATE" -> ModellingKind.TEMPLATE;
            default -> ModellingKind.INSTANCE;
        };
    }

    private String resolveId(JsonNode idStrategy) {
        if (idStrategy != null && idStrategy.isObject()) {
            String kind = idStrategy.path("kind").asText("");
            if ("explicit".equals(kind)) {
                return idStrategy.path("id").asText(UUID.randomUUID().toString());
            }
            if ("urn".equals(kind)) {
                JsonNode urn = idStrategy.path("urn");
                String ns = urn.path("namespace").asText("");
                String suffix = urn.path("suffix").asText("");
                if (!ns.isEmpty() && !suffix.isEmpty()) return ns + ":" + suffix;
            }
        }
        return "urn:uuid:" + UUID.randomUUID();
    }

    private Reference globalRef(String value) {
        DefaultKey key = new DefaultKey.Builder()
                .type(KeyTypes.GLOBAL_REFERENCE)
                .value(value)
                .build();
        return new DefaultReference.Builder()
                .type(ReferenceTypes.MODEL_REFERENCE)
                .keys(List.of(key))
                .build();
    }

    private void addElementsRecursive(List<SubmodelElement> list, String prefix, JsonNode node, Map<String, ObjectNode> metaByPath) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String path = prefix.isEmpty() ? e.getKey() : prefix + "/" + e.getKey();
                JsonNode v = e.getValue();
                if (v.isObject()) {
                    // create collection and recurse
                    SubmodelElementCollection coll = new DefaultSubmodelElementCollection.Builder()
                            .idShort(e.getKey())
                            .value(new ArrayList<>())
                            .build();
                    // attach semantics if provided
                    ObjectNode meta = metaByPath.get(path);
                    if (meta != null && meta.has("semanticId")) {
                        coll.setSemanticId(globalRef(meta.get("semanticId").asText()));
                    }
                    List<SubmodelElement> childList = coll.getValue();
                    addElementsRecursive(childList, path, v, metaByPath);
                    list.add(coll);
                } else {
                    list.add(buildProperty(e.getKey(), path, v, metaByPath));
                }
            }
        } else {
            // scalar at the root
            list.add(buildProperty(prefix, prefix, node, metaByPath));
        }
    }

    private SubmodelElement buildProperty(String idShort, String path, JsonNode value, Map<String, ObjectNode> metaByPath) {
        DefaultProperty.Builder pb = new DefaultProperty.Builder();
        pb.idShort(idShort);

        ObjectNode meta = metaByPath.get(path);
        DataTypeDefXsd vt = mapValueType(meta == null ? null : meta.path("valueType").asText(null));
        if (vt != null) pb.valueType(vt);

        if (meta != null && meta.has("semanticId")) {
            pb.semanticId(globalRef(meta.get("semanticId").asText()));
        }

        if (value == null || value.isNull()) {
            pb.value(null);
        } else if (value.isValueNode()) {
            pb.value(value.asText());
        } else {
            pb.value(value.toString());
        }
        return pb.build();
    }

    private DataTypeDefXsd mapValueType(String t) {
        if (t == null) return null;
        String s = t.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "string" -> DataTypeDefXsd.STRING;
            case "boolean" -> DataTypeDefXsd.BOOLEAN;
            case "integer", "int" -> DataTypeDefXsd.INTEGER;
            case "double", "number" -> DataTypeDefXsd.DOUBLE;
            case "date" -> DataTypeDefXsd.DATE;
            case "dateTime", "datetime" -> DataTypeDefXsd.DATE_TIME;
            case "time" -> DataTypeDefXsd.TIME;
            default -> DataTypeDefXsd.STRING;
        };
    }
}
