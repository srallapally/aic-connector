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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractHandler implements CrudqHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHandler.class);
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final CrestHttpClient http;
    protected final String resourceUrl;
    protected final PingAICConfiguration cfg;
    protected final Map<String, String> relationshipCollections;
    protected final Set<String> readOnlyRelationships;

    protected AbstractHandler(CrestHttpClient http, String resourceUrl, PingAICConfiguration cfg,
                              Map<String, String> relationshipCollections,
                              Set<String> readOnlyRelationships) {
        this.http = http;
        this.resourceUrl = resourceUrl;
        this.cfg = cfg;
        this.relationshipCollections = relationshipCollections;
        this.readOnlyRelationships = readOnlyRelationships;
    }

    protected ConnectorObject connectorObjectFromJson(JsonNode node, ObjectClass objectClass, String nameAttribute) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass);

        String id = node.path("_id").asText();
        builder.setUid(id);
        LOG.debug("connectorObjectFromJson: _id={}, objectClass={}", id, objectClass.getObjectClassValue());

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
            if (fieldValue.isNull()) {
                LOG.debug("  {} -> null, emitting empty list", fieldName);
                builder.addAttribute(fieldName, Collections.emptyList());
                continue;
            }
            if (fieldValue.isObject()) {
                LOG.debug("  {} -> object, skipping", fieldName);
                continue;
            }
            if (fieldValue.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : fieldValue) {
                    if (item.isTextual()) {
                        values.add(item.asText());
                    } else if (item.isObject()) {
                        values.add(item.toString());
                    }
                }
                LOG.debug("  {} -> array, values={}", fieldName, values);
                builder.addAttribute(fieldName, values);
            } else {
                Object scalar = scalarValue(fieldValue);
                LOG.debug("  {} -> scalar, value={}", fieldName, scalar);
                builder.addAttribute(fieldName, scalar);
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

    // ── Relationship read helpers ─────────────────────────────────────────────

    protected ConnectorObject enrichWithRelationships(ConnectorObject base, OperationOptions options) {
        String[] attrsToGet = options.getAttributesToGet();
        LOG.debug("enrichWithRelationships for user {}: attrsToGet={}", base.getUid().getUidValue(), attrsToGet);

        Set<String> requestedRelationships = new HashSet<>();
        if (attrsToGet == null) {
            requestedRelationships.addAll(relationshipCollections.keySet());
        } else {
            for (String attr : attrsToGet) {
                if (relationshipCollections.containsKey(attr)) {
                    requestedRelationships.add(attr);
                }
            }
        }
        LOG.debug("Requested relationships to fetch: {}", requestedRelationships);
        if (requestedRelationships.isEmpty()) {
            return base;
        }

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(base.getObjectClass());
        builder.setUid(base.getUid());
        builder.setName(base.getName());
        for (Attribute attr : base.getAttributes()) {
            if (!attr.is(Uid.NAME) && !attr.is(Name.NAME)) {
                builder.addAttribute(attr);
            }
        }

        String userId = base.getUid().getUidValue();
        for (String relAttr : requestedRelationships) {
            List<String> refIds = fetchRelationshipIds(userId, relAttr);
            LOG.debug("User {} relationship '{}': resolved IDs {}", userId, relAttr, refIds);
            builder.addAttribute(relAttr, refIds);
        }

        return builder.build();
    }

    private List<String> fetchRelationshipIds(String userId, String fieldName) {
        String url = resourceUrl + "/" + userId + "/" + fieldName;
        LOG.debug("Fetching relationship sub-resource: GET {}", url);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("_queryFilter", "true");
        params.put("_fields", "_ref,_refResourceCollection,_refResourceId,_refProperties");
        JsonNode response = http.get(url, params);
        LOG.debug("Relationship '{}' raw response: {}", fieldName, response);

        List<String> ids = new ArrayList<>();
        JsonNode results = response.get("result");
        if (results != null) {
            for (JsonNode node : results) {
                JsonNode refId = node.get("_refResourceId");
                if (refId != null && !refId.isNull()) {
                    ids.add(refId.asText());
                }
            }
        }
        return ids;
    }

    // ── Relationship write helpers ────────────────────────────────────────────

    protected void syncRelationship(String userId, String fieldName, List<Object> desiredValues) {
        Set<String> desired = new LinkedHashSet<>();
        if (desiredValues != null) {
            for (Object v : desiredValues) {
                if (v != null) {
                    desired.add(v.toString());
                }
            }
        }

        Map<String, RelationshipEntry> current = fetchCurrentRelationships(userId, fieldName);

        Set<String> toAdd = new LinkedHashSet<>(desired);
        toAdd.removeAll(current.keySet());
        Set<String> toRemove = new LinkedHashSet<>(current.keySet());
        toRemove.removeAll(desired);

        for (String id : toAdd) {
            applyRelationshipAdd(userId, fieldName, id);
        }
        for (String id : toRemove) {
            applyRelationshipRemove(userId, fieldName, current.get(id));
        }
    }

    private Map<String, RelationshipEntry> fetchCurrentRelationships(String userId, String fieldName) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("_queryFilter", "true");
        params.put("_fields", "_ref,_refResourceCollection,_refResourceId,_refProperties");
        JsonNode response = http.get(resourceUrl + "/" + userId + "/" + fieldName, params);

        Map<String, RelationshipEntry> map = new LinkedHashMap<>();
        JsonNode results = response.get("result");
        if (results != null) {
            for (JsonNode node : results) {
                String refId = node.path("_refResourceId").asText(null);
                if (refId != null) {
                    RelationshipEntry entry = new RelationshipEntry(
                            node.path("_ref").asText(),
                            node.path("_refResourceCollection").asText(),
                            refId,
                            node.path("_refProperties").path("_id").asText(),
                            node.path("_refProperties").path("_rev").asText()
                    );
                    map.put(refId, entry);
                }
            }
        }
        return map;
    }

    private void applyRelationshipAdd(String userId, String fieldName, String targetId) {
        String collectionPath = relationshipCollections.get(fieldName);
        ArrayNode ops = MAPPER.createArrayNode();
        ObjectNode op = ops.addObject();
        op.put("operation", "add");
        op.put("field", "/" + fieldName + "/-");
        ObjectNode value = MAPPER.createObjectNode();
        value.put("_ref", collectionPath + "/" + targetId);
        op.set("value", value);
        http.patch(resourceUrl + "/" + userId, ops);
    }

    private void applyRelationshipRemove(String userId, String fieldName,
                                         RelationshipEntry entry) {
        ArrayNode ops = MAPPER.createArrayNode();
        ObjectNode op = ops.addObject();
        op.put("operation", "remove");
        op.put("field", "/" + fieldName);
        ObjectNode value = MAPPER.createObjectNode();
        value.put("_ref", entry.ref);
        value.put("_refResourceCollection", entry.refResourceCollection);
        value.put("_refResourceId", entry.refResourceId);
        ObjectNode refProps = MAPPER.createObjectNode();
        refProps.put("_id", entry.refPropertiesId);
        refProps.put("_rev", entry.refPropertiesRev);
        value.set("_refProperties", refProps);
        op.set("value", value);
        http.patch(resourceUrl + "/" + userId, ops);
    }

    // ── RelationshipEntry ─────────────────────────────────────────────────────

    private static final class RelationshipEntry {
        final String ref;
        final String refResourceCollection;
        final String refResourceId;
        final String refPropertiesId;
        final String refPropertiesRev;

        RelationshipEntry(String ref, String refResourceCollection, String refResourceId,
                          String refPropertiesId, String refPropertiesRev) {
            this.ref = ref;
            this.refResourceCollection = refResourceCollection;
            this.refResourceId = refResourceId;
            this.refPropertiesId = refPropertiesId;
            this.refPropertiesRev = refPropertiesRev;
        }
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
