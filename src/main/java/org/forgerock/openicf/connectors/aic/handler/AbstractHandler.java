// src/main/java/org/forgerock/openicf/connectors/aic/handler/AbstractHandler.java
package org.forgerock.openicf.connectors.aic.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.forgerock.openicf.connectors.aic.PingAICConfiguration;
import org.forgerock.openicf.connectors.aic.util.CrestHttpClient;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractHandler implements CrudqHandler {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final CrestHttpClient http;
    protected final String resourceUrl;
    protected final PingAICConfiguration cfg;

    protected AbstractHandler(CrestHttpClient http, String resourceUrl, PingAICConfiguration cfg) {
        this.http = http;
        this.resourceUrl = resourceUrl;
        this.cfg = cfg;
    }

    protected ConnectorObject connectorObjectFromJson(JsonNode node, ObjectClass objectClass, String nameAttribute) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass);

        String id = node.path("_id").asText();
        builder.setUid(id);

        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            if ("_id".equals(fieldName)) {
                continue;
            }
            if (fieldName.equals(nameAttribute)) {
                builder.setName(fieldValue.asText());
                continue;
            }
            if (fieldValue.isNull() || fieldValue.isObject()) {
                continue;
            }
            if (fieldValue.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : fieldValue) {
                    if (item.isTextual()) {
                        values.add(item.asText());
                    }
                }
                builder.addAttribute(fieldName, values);
            } else {
                builder.addAttribute(fieldName, scalarValue(fieldValue));
            }
        }

        return builder.build();
    }

    private static Object scalarValue(JsonNode node) {
        if (node.isBoolean()) return node.asBoolean();
        if (node.isIntegralNumber()) return node.asInt();
        if (node.isNumber()) return node.asDouble();
        return node.asText();
    }

    private static JsonNode valuesToJsonNode(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return MAPPER.nullNode();
        }
        if (values.size() == 1) {
            return MAPPER.valueToTree(values.get(0));
        }
        ArrayNode arr = MAPPER.createArrayNode();
        for (Object v : values) {
            arr.add(MAPPER.valueToTree(v));
        }
        return arr;
    }

    protected ObjectNode attributesToJson(Set<Attribute> attributes, String nameAttribute) {
        ObjectNode node = MAPPER.createObjectNode();
        for (Attribute attr : attributes) {
            if (attr.is(Uid.NAME)) {
                continue;
            }
            String fieldName = attr.is(Name.NAME) ? nameAttribute : attr.getName();
            node.set(fieldName, valuesToJsonNode(attr.getValue()));
        }
        return node;
    }

    protected ArrayNode attributesToPatchOps(Set<Attribute> attributes) {
        ArrayNode ops = MAPPER.createArrayNode();
        for (Attribute attr : attributes) {
            if (attr.is(Uid.NAME) || attr.is(Name.NAME)) {
                continue;
            }
            ObjectNode op = ops.addObject();
            op.put("operation", "replace");
            op.put("field", "/" + attr.getName());
            op.set("value", valuesToJsonNode(attr.getValue()));
        }
        return ops;
    }

    protected String buildQueryFilter(String translatedFilter, String baseFilter) {
        List<String> parts = new ArrayList<>();

        if (baseFilter != null && !baseFilter.isBlank()) {
            parts.add("(" + baseFilter + ")");
        }

        String tsAttr = cfg.getTimestampAttribute();
        String tsVal = cfg.getTimestampValue();
        if (tsAttr != null && !tsAttr.isBlank() && tsVal != null && !tsVal.isBlank()) {
            parts.add(tsAttr + " ge \"" + tsVal + "\"");
        }

        if (translatedFilter != null && !translatedFilter.isBlank()) {
            parts.add("(" + translatedFilter + ")");
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" and ", parts);
    }

    @Override
    public abstract Uid create(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options);

    @Override
    public abstract ConnectorObject getObject(ObjectClass objectClass, Uid uid, OperationOptions options);

    @Override
    public abstract Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> attributes, OperationOptions options);

    @Override
    public abstract void delete(ObjectClass objectClass, Uid uid, OperationOptions options);

    @Override
    public abstract void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler handler, OperationOptions options);
}
